package com.paynest.transfer.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paynest.common.error.InsufficientBalanceException;
import com.paynest.common.error.InvalidTransferException;
import com.paynest.common.error.SelfTransferNotAllowedException;
import com.paynest.common.error.UserNotFoundException;
import com.paynest.common.error.WalletNotFoundException;
import com.paynest.transaction.domain.Transaction;
import com.paynest.transaction.persistence.TransactionRepository;
import com.paynest.transfer.api.TransferRequest;
import com.paynest.transfer.api.TransferResponse;
import com.paynest.user.domain.EmailNormalizer;
import com.paynest.user.domain.User;
import com.paynest.user.persistence.UserRepository;
import com.paynest.wallet.domain.Wallet;
import com.paynest.wallet.persistence.WalletRepository;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");
    private static final String SENT = "SENT";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final Validator validator;

    public TransferService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            Clock clock,
            Validator validator
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.validator = validator;
    }

    @Transactional
    public TransferResponse transfer(long senderId, TransferRequest request) {
        CanonicalTransfer canonical = validate(request);

        User sender = userRepository.findById(senderId).orElseThrow(UserNotFoundException::new);
        User recipient = userRepository.findByEmail(canonical.recipientEmail())
                .orElseThrow(UserNotFoundException::new);
        if (sender.getId().equals(recipient.getId())) {
            throw new SelfTransferNotAllowedException();
        }

        Wallet senderWallet = walletRepository.findByUserId(sender.getId())
                .orElseThrow(WalletNotFoundException::new);
        Wallet recipientWallet = walletRepository.findByUserId(recipient.getId())
                .orElseThrow(WalletNotFoundException::new);
        requireUsdWallets(senderWallet, recipientWallet);
        if (canonical.amount().compareTo(senderWallet.getBalance()) > 0) {
            throw new InsufficientBalanceException();
        }

        senderWallet.debit(canonical.amount());
        recipientWallet.credit(canonical.amount());
        Transaction transaction = transactionRepository.save(Transaction.completed(
                senderWallet, recipientWallet, canonical.amount(), clock));

        return new TransferResponse(
                transaction.getId(),
                SENT,
                recipient.getName(),
                recipient.getEmail(),
                transaction.getAmount(),
                senderWallet.getCurrency(),
                transaction.getStatus().name(),
                transaction.getCreatedAt(),
                senderWallet.getBalance());
    }

    private CanonicalTransfer validate(TransferRequest request) {
        if (request == null) {
            throw new InvalidTransferException(Map.of("request", "Request body is required"));
        }

        Map<String, String> errors = new LinkedHashMap<>();
        String recipientEmail = normalizeEmail(request.recipientEmail(), errors);
        BigDecimal amount = normalizeAmount(request.amount(), errors);
        if (!errors.isEmpty()) {
            throw new InvalidTransferException(errors);
        }
        return new CanonicalTransfer(recipientEmail, amount);
    }

    private String normalizeEmail(String email, Map<String, String> errors) {
        if (email == null) {
            errors.put("recipientEmail", "Recipient email is required");
            return "";
        }

        String normalized;
        try {
            normalized = EmailNormalizer.normalize(email);
        } catch (IllegalArgumentException exception) {
            errors.put("recipientEmail", "Recipient email must be a valid address of at most 254 characters");
            return "";
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_EMAIL_LENGTH
                || !isWellFormedUnicode(normalized)
                || !validator.validate(new CanonicalEmail(normalized)).isEmpty()) {
            errors.put("recipientEmail", "Recipient email must be a valid address of at most 254 characters");
        }
        return normalized;
    }

    private BigDecimal normalizeAmount(BigDecimal amount, Map<String, String> errors) {
        if (amount == null) {
            errors.put("amount", "Amount is required");
            return BigDecimal.ZERO;
        }
        if (amount.signum() <= 0
                || amount.scale() > 2
                || amount.compareTo(MAX_AMOUNT) > 0) {
            errors.put("amount", "Amount must be positive, exact to two decimal places, and supported");
            return BigDecimal.ZERO;
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void requireUsdWallets(Wallet senderWallet, Wallet recipientWallet) {
        if (!Wallet.USD.equals(senderWallet.getCurrency())
                || !Wallet.USD.equals(recipientWallet.getCurrency())) {
            throw new InvalidTransferException(Map.of("currency", "Both wallets must use USD"));
        }
    }

    private static boolean isWellFormedUnicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    private record CanonicalTransfer(String recipientEmail, BigDecimal amount) {
    }

    private record CanonicalEmail(@Email String value) {
    }
}
