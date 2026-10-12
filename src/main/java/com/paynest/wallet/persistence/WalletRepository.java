package com.paynest.wallet.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paynest.wallet.domain.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);
}
