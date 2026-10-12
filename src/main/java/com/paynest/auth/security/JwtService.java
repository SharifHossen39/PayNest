package com.paynest.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.OptionalLong;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.signingKey = properties.signingKey();
        this.properties = properties;
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public IssuedToken issue(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID is required");
        }

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(properties.ttl());
        String value = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(value, expiresAt);
    }

    public OptionalLong verifyUserId(String token) {
        if (token == null || token.isBlank()) {
            return OptionalLong.empty();
        }

        try {
            Jws<Claims> parsed = parser.parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())) {
                return OptionalLong.empty();
            }
            if (parsed.getPayload().getExpiration() == null) {
                return OptionalLong.empty();
            }

            String subject = parsed.getPayload().getSubject();
            long userId = Long.parseLong(subject);
            if (userId <= 0 || !Long.toString(userId).equals(subject)) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(userId);
        } catch (JwtException | IllegalArgumentException exception) {
            return OptionalLong.empty();
        }
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
