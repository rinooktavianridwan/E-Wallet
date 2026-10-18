package com.ewallet.transaction.repository;

import com.ewallet.entity.Transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	Page<Transaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

	Page<Transaction> findByWalletId(Long walletId, Pageable pageable);
}
