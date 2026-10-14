package com.paynest.transaction.persistence;

import com.paynest.transaction.domain.Transaction;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
