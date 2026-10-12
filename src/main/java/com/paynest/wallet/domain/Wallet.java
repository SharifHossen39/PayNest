package com.paynest.wallet.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paynest.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "wallets",
        schema = "paynest",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallets_user", columnNames = "user_id")
)
public class Wallet {

    public static final String USD = "USD";

    private static final BigDecimal ZERO_BALANCE = new BigDecimal("0.00");
    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_wallets_user")
    )
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Wallet() {
    }

    private Wallet(User user, Instant createdAt) {
        this.user = user;
        this.balance = ZERO_BALANCE;
        this.currency = USD;
        this.createdAt = createdAt;
    }

    public static Wallet create(User user, Clock clock) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        if (clock == null) {
            throw new IllegalArgumentException("Clock is required");
        }
        return new Wallet(user, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    public void credit(BigDecimal amount) {
        BigDecimal exactAmount = requirePositiveScaleTwoAmount(amount);
        BigDecimal result = balance.add(exactAmount);
        if (result.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Balance exceeds the supported maximum");
        }
        balance = result;
    }

    public void debit(BigDecimal amount) {
        BigDecimal exactAmount = requirePositiveScaleTwoAmount(amount);
        if (exactAmount.compareTo(balance) > 0) {
            throw new IllegalStateException("Insufficient wallet balance");
        }
        balance = balance.subtract(exactAmount);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static BigDecimal requirePositiveScaleTwoAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Amount exceeds the supported maximum");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must have at most two decimal places");
        }
        BigDecimal exactAmount = amount.setScale(2, RoundingMode.UNNECESSARY);
        return exactAmount;
    }
}
