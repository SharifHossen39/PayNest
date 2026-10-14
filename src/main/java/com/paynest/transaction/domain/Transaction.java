package com.paynest.transaction.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.paynest.wallet.domain.Wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "transactions", schema = "paynest")
public class Transaction {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "sender_wallet_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_transactions_sender_wallet")
    )
    private Wallet senderWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "receiver_wallet_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_transactions_receiver_wallet")
    )
    private Wallet receiverWallet;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    private Transaction(
            Wallet senderWallet,
            Wallet receiverWallet,
            BigDecimal amount,
            Instant createdAt
    ) {
        this.senderWallet = senderWallet;
        this.receiverWallet = receiverWallet;
        this.amount = amount;
        this.status = TransactionStatus.COMPLETED;
        this.createdAt = createdAt;
    }

    public static Transaction completed(
            Wallet senderWallet,
            Wallet receiverWallet,
            BigDecimal amount,
            Clock clock
    ) {
        if (senderWallet == null) {
            throw new IllegalArgumentException("Sender wallet is required");
        }
        if (receiverWallet == null) {
            throw new IllegalArgumentException("Receiver wallet is required");
        }
        if (senderWallet == receiverWallet
                || (senderWallet.getId() != null
                && senderWallet.getId().equals(receiverWallet.getId()))) {
            throw new IllegalArgumentException("Sender and receiver wallets must be different");
        }
        if (clock == null) {
            throw new IllegalArgumentException("Clock is required");
        }
        return new Transaction(
                senderWallet,
                receiverWallet,
                requireExactPositiveAmount(amount),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    public Long getId() {
        return id;
    }

    public Wallet getSenderWallet() {
        return senderWallet;
    }

    public Wallet getReceiverWallet() {
        return receiverWallet;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static BigDecimal requireExactPositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must have at most two decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Amount exceeds the supported maximum");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
