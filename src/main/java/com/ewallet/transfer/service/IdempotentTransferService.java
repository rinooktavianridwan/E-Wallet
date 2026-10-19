package com.ewallet.transfer.service;

import com.ewallet.common.exception.IdempotencyKeyReuseException;
import com.ewallet.common.exception.InvalidIdempotencyKeyException;
import com.ewallet.entity.IdempotencyRecord;
import com.ewallet.entity.Transaction;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transaction.repository.TransactionRepository;
import com.ewallet.transfer.dto.TransferRequest;
import com.ewallet.transfer.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

// Sengaja tidak memakai @Transactional: pelanggaran unique key harus ditangkap di luar transaksi yang sudah gagal
@Service
public class IdempotentTransferService {

	static final int MAX_KEY_LENGTH = 100;

	public record Result(TransactionResponse transaction, boolean replayed) {
	}

	private final TransferService transferService;
	private final IdempotencyRecordRepository recordRepository;
	private final TransactionRepository transactionRepository;
	private final TransactionTemplate transactionTemplate;

	public IdempotentTransferService(TransferService transferService,
									 IdempotencyRecordRepository recordRepository,
									 TransactionRepository transactionRepository,
									 TransactionTemplate transactionTemplate) {
		this.transferService = transferService;
		this.recordRepository = recordRepository;
		this.transactionRepository = transactionRepository;
		this.transactionTemplate = transactionTemplate;
	}

	public Result transfer(Long userId, String rawKey, TransferRequest request) {
		if (rawKey == null) {
			return new Result(transferService.transfer(userId, request), false);
		}

		String key = rawKey.trim();
		if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
			throw new InvalidIdempotencyKeyException();
		}

		String hash = hash(request);

		Optional<IdempotencyRecord> existing = recordRepository.findByUserIdAndIdempotencyKey(userId, key);
		if (existing.isPresent()) {
			return replay(existing.get(), hash);
		}

		try {
			TransactionResponse response = transactionTemplate.execute(status -> {
				// key dicatat paling awal; request duplikat yang bersamaan menunggu di sini sampai yang pertama selesai
				IdempotencyRecord record = recordRepository.saveAndFlush(new IdempotencyRecord(userId, key, hash));
				TransactionResponse created = transferService.transfer(userId, request);
				record.assignTransaction(created.id());
				return created;
			});
			return new Result(response, false);
		} catch (DataIntegrityViolationException e) {
			IdempotencyRecord winner = recordRepository.findByUserIdAndIdempotencyKey(userId, key)
					.orElseThrow(() -> e);
			return replay(winner, hash);
		}
	}

	private Result replay(IdempotencyRecord record, String hash) {
		if (!record.getRequestHash().equals(hash)) {
			throw new IdempotencyKeyReuseException();
		}
		Transaction original = transactionRepository.findById(record.getTransactionId())
				.orElseThrow(() -> new IllegalStateException("Transaction missing for idempotency key"));
		return new Result(TransactionResponse.from(original), true);
	}

	private static String hash(TransferRequest request) {
		String payload = request.recipientEmail().trim().toLowerCase(Locale.ROOT)
				+ "|" + request.amount().stripTrailingZeros().toPlainString()
				+ "|" + (request.description() == null ? "" : request.description().trim());
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
