package com.ewallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One ledger entry. A transfer produces two rows (DEBIT for sender, CREDIT for receiver)
 * that share the same reference number. Rows are never updated after creation.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "wallet_id", nullable = false, updatable = false)
	private Wallet wallet;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, updatable = false)
	private TransactionType transactionType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private TransactionCategory category;

	@Column(nullable = false, updatable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 2)
	private BigDecimal balanceAfter;

	@Column(name = "reference_number", nullable = false, updatable = false)
	private String referenceNumber;

	@Column(updatable = false)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Transaction() {
	}

	public Transaction(Wallet wallet, TransactionType transactionType, TransactionCategory category,
					   BigDecimal amount, BigDecimal balanceAfter, String referenceNumber, String description) {
		this.wallet = wallet;
		this.transactionType = transactionType;
		this.category = category;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
		this.referenceNumber = referenceNumber;
		this.description = description;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Wallet getWallet() {
		return wallet;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public TransactionCategory getCategory() {
		return category;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public String getReferenceNumber() {
		return referenceNumber;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
