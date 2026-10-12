package com.paynest.user.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "users",
        schema = "paynest",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email")
)
public class User {

    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_PASSWORD_HASH_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = MAX_PASSWORD_HASH_LENGTH)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    private User(String name, String email, String passwordHash, Instant createdAt) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public static User create(String name, String email, String passwordHash, Clock clock) {
        return new User(
                normalizeName(name),
                EmailNormalizer.normalize(email),
                requirePasswordHash(passwordHash),
                requireClock(clock).instant().truncatedTo(ChronoUnit.MICROS));
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name is required");
        }
        String normalized = EmailNormalizer.stripUnicodeEdges(name);
        int length = normalized.codePointCount(0, normalized.length());
        if (normalized.isBlank() || length < MIN_NAME_LENGTH || length > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name must contain between 2 and 100 characters");
        }
        return normalized;
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash is required");
        }
        if (passwordHash.length() > MAX_PASSWORD_HASH_LENGTH) {
            throw new IllegalArgumentException("Password hash must not exceed 255 characters");
        }
        return passwordHash;
    }

    private static Clock requireClock(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock is required");
        }
        return clock;
    }
}
