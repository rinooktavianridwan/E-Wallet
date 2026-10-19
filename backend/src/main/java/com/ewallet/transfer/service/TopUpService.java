package com.ewallet.transfer.service;

import com.ewallet.common.exception.WalletInactiveException;
import com.ewallet.common.exception.WalletNotFoundException;
import com.ewallet.entity.Transaction;
import com.ewallet.entity.TransactionCategory;
import com.ewallet.entity.TransactionType;
import com.ewallet.entity.Wallet;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transaction.repository.TransactionRepository;
import com.ewallet.transaction.service.ReferenceNumberGenerator;
import com.ewallet.transfer.dto.TopUpRequest;
import com.ewallet.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TopUpService {

	private static final String DEFAULT_DESCRIPTION = "Top up";

	private final WalletRepository walletRepository;
	private final TransactionRepository transactionRepository;
	private final ReferenceNumberGenerator referenceNumberGenerator;

	public TopUpService(WalletRepository walletRepository,
						TransactionRepository transactionRepository,
						ReferenceNumberGenerator referenceNumberGenerator) {
		this.walletRepository = walletRepository;
		this.transactionRepository = transactionRepository;
		this.referenceNumberGenerator = referenceNumberGenerator;
	}

	@Transactional
	public TransactionResponse topUp(Long userId, TopUpRequest request) {
		// wallet dikunci sampai commit supaya top up bersamaan tidak saling menimpa
		Wallet wallet = walletRepository.findWithLockByUserId(userId)
				.orElseThrow(() -> new WalletNotFoundException(userId));

		if (!wallet.isActive()) {
			throw new WalletInactiveException();
		}

		BigDecimal amount = request.amount().setScale(2);
		wallet.credit(amount);

		String description = request.description() == null || request.description().isBlank()
				? DEFAULT_DESCRIPTION
				: request.description().trim();

		Transaction transaction = transactionRepository.save(new Transaction(
				wallet,
				TransactionType.CREDIT,
				TransactionCategory.TOP_UP,
				amount,
				wallet.getBalance(),
				referenceNumberGenerator.generate(),
				description));

		return TransactionResponse.from(transaction);
	}
}
