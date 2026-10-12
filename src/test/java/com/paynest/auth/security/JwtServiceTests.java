package com.paynest.auth.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {

    private static final String VALID_SECRET =
            "dGVzdC1vbmx5LXBheW5lc3Qtand0LXNlY3JldC0zMmI=";
    private static final Instant NOW = Instant.parse("2026-10-07T08:30:00Z");

    @Test
    void issuesAValidHs256TokenWithOnlySubjectIssuedAtAndExpiration() {
        Instant clockValue = Instant.parse("2026-10-07T08:30:00.987654321Z");
        JwtProperties properties = new JwtProperties(VALID_SECRET, Duration.ofMinutes(15));
        JwtService service = new JwtService(
                properties,
                Clock.fixed(clockValue, ZoneOffset.UTC));

        JwtService.IssuedToken issued = service.issue(42L);
        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(properties.signingKey())
                .clock(() -> Date.from(clockValue))
                .build()
                .parseSignedClaims(issued.value());

        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(parsed.getPayload()).hasSize(3);
        assertThat(parsed.getPayload().getSubject()).isEqualTo("42");
        assertThat(parsed.getPayload().getIssuedAt().toInstant())
                .isEqualTo(Instant.parse("2026-10-07T08:30:00Z"));
        assertThat(parsed.getPayload().getExpiration().toInstant())
                .isEqualTo(Instant.parse("2026-10-07T08:45:00Z"));
        assertThat(issued.expiresAt()).isEqualTo(parsed.getPayload().getExpiration().toInstant());
        assertThat(service.verifyUserId(issued.value())).hasValue(42L);
    }

    @Test
    void rejectsMalformedTamperedWrongSignatureAndExpiredTokens() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, Duration.ofMinutes(15));
        JwtService service = service(properties);
        String valid = token(properties.signingKey(), "42", NOW.plusSeconds(60));
        int signatureStart = valid.lastIndexOf('.') + 1;
        int tamperAt = signatureStart + 2;
        char replacement = valid.charAt(tamperAt) == 'A' ? 'B' : 'A';
        String tampered = valid.substring(0, tamperAt) + replacement + valid.substring(tamperAt + 1);
        SecretKey wrongKey = Keys.hmacShaKeyFor("another-test-only-secret-with-32-bytes".getBytes());

        assertThat(service.verifyUserId("not-a-compact-jwt")).isEmpty();
        assertThat(service.verifyUserId(tampered)).isEmpty();
        assertThat(service.verifyUserId(token(wrongKey, "42", NOW.plusSeconds(60)))).isEmpty();
        assertThat(service.verifyUserId(token(properties.signingKey(), "42", NOW.minusSeconds(1))))
                .isEmpty();
    }

    @Test
    void rejectsMissingNonCanonicalNonNumericAndOverflowSubjects() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, Duration.ofMinutes(15));
        JwtService service = service(properties);
        Date expiry = Date.from(NOW.plusSeconds(60));
        String missing = Jwts.builder()
                .expiration(expiry)
                .signWith(properties.signingKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(service.verifyUserId(missing)).isEmpty();
        assertThat(service.verifyUserId(token(properties.signingKey(), "user-42", expiry.toInstant())))
                .isEmpty();
        assertThat(service.verifyUserId(token(properties.signingKey(), "9223372036854775808", expiry.toInstant())))
                .isEmpty();
        assertThat(service.verifyUserId(token(properties.signingKey(), "0", expiry.toInstant())))
                .isEmpty();
        assertThat(service.verifyUserId(token(properties.signingKey(), "042", expiry.toInstant())))
                .isEmpty();
    }

    @Test
    void rejectsACorrectlySignedTokenWithoutExpiration() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, Duration.ofMinutes(15));
        String timeless = Jwts.builder()
                .subject("42")
                .signWith(properties.signingKey(), Jwts.SIG.HS256)
                .compact();

        assertThat(service(properties).verifyUserId(timeless)).isEmpty();
    }

    @Test
    void rejectsAValidSignatureWhenTheDeclaredAlgorithmIsNotHs256() {
        byte[] longSecret = new byte[64];
        java.util.Arrays.fill(longSecret, (byte) 7);
        JwtProperties properties = new JwtProperties(
                Base64.getEncoder().encodeToString(longSecret),
                Duration.ofMinutes(15));
        String hs384 = Jwts.builder()
                .subject("42")
                .expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(properties.signingKey(), Jwts.SIG.HS384)
                .compact();

        assertThat(service(properties).verifyUserId(hs384)).isEmpty();
    }

    private JwtService service(JwtProperties properties) {
        return new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private String token(SecretKey key, String subject, Instant expiration) {
        return Jwts.builder()
                .subject(subject)
                .expiration(Date.from(expiration))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
