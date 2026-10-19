package com.ewallet.transaction.service;

import com.ewallet.common.dto.PageResponse;
import com.ewallet.common.exception.WalletNotFoundException;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transaction.repository.TransactionRepository;
import com.ewallet.wallet.repository.WalletRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

	static final int MAX_PAGE_SIZE = 100;

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt")
			.and(Sort.by(Sort.Direction.DESC, "id"));

	private final TransactionRepository transactionRepository;
	private final WalletRepository walletRepository;

	public TransactionService(TransactionRepository transactionRepository, WalletRepository walletRepository) {
		this.transactionRepository = transactionRepository;
		this.walletRepository = walletRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<TransactionResponse> getHistory(Long userId, int page, int size) {
		Long walletId = walletRepository.findIdByUserId(userId)
				.orElseThrow(() -> new WalletNotFoundException(userId));

		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

		return PageResponse.from(transactionRepository
				.findByWalletId(walletId, PageRequest.of(safePage, safeSize, NEWEST_FIRST))
				.map(TransactionResponse::from));
	}
}
