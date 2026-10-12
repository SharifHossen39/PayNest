package com.paynest.wallet.application;

import com.paynest.wallet.api.WalletResponse;
import com.paynest.wallet.persistence.WalletRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletQueryService {

    private final WalletRepository walletRepository;

    public WalletQueryService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public WalletResponse currentWallet(long userId) {
        return walletRepository.findByUserId(userId)
                .map(WalletResponse::from)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user is missing the required wallet"));
    }
}
