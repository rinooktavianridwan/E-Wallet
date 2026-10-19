package com.ewallet.transaction.dto;

import com.ewallet.entity.Transaction;
import com.ewallet.entity.TransactionCategory;
import com.ewallet.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
		UUID id,
		String referenceNumber,
		TransactionType type,
		TransactionCategory category,
		BigDecimal amount,
		BigDecimal balanceAfter,
		String description,
		Instant createdAt
) {

	public static TransactionResponse from(Transaction transaction) {
		return new TransactionResponse(
				transaction.getId(),
				transaction.getReferenceNumber(),
				transaction.getTransactionType(),
				transaction.getCategory(),
				transaction.getAmount(),
				transaction.getBalanceAfter(),
				transaction.getDescription(),
				transaction.getCreatedAt());
	}
}
