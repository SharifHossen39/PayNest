package com.paynest.auth.security;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paynest.jwt")
public final class JwtProperties {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final byte[] secret;
    private final Duration ttl;

    public JwtProperties(String secret, Duration ttl) {
        this.secret = decodeSecret(secret);
        if (ttl == null || ttl.compareTo(Duration.ofSeconds(1)) < 0 || ttl.getNano() != 0) {
            throw new IllegalArgumentException("JWT TTL must be a positive whole-second duration");
        }
        this.ttl = ttl;
    }

    SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.clone());
    }

    public Duration ttl() {
        return ttl;
    }

    private static byte[] decodeSecret(String encodedSecret) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalArgumentException("JWT secret is required");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT secret must be valid Base64", exception);
        }
        if (decoded.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must decode to at least 32 bytes");
        }
        return decoded;
    }
}
