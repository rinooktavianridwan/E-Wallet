package com.ewallet.transfer.service;

import com.ewallet.common.exception.InsufficientBalanceException;
import com.ewallet.common.exception.RecipientNotFoundException;
import com.ewallet.common.exception.SelfTransferException;
import com.ewallet.common.exception.WalletInactiveException;
import com.ewallet.common.exception.WalletNotFoundException;
import com.ewallet.entity.Transaction;
import com.ewallet.entity.TransactionCategory;
import com.ewallet.entity.TransactionType;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transaction.repository.TransactionRepository;
import com.ewallet.transaction.service.ReferenceNumberGenerator;
import com.ewallet.transfer.dto.TransferRequest;
import com.ewallet.user.repository.UserRepository;
import com.ewallet.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class TransferService {

	private static final String DEFAULT_DESCRIPTION = "Transfer";

	private final UserRepository userRepository;
	private final WalletRepository walletRepository;
	private final TransactionRepository transactionRepository;
	private final ReferenceNumberGenerator referenceNumberGenerator;

	public TransferService(UserRepository userRepository,
						   WalletRepository walletRepository,
						   TransactionRepository transactionRepository,
						   ReferenceNumberGenerator referenceNumberGenerator) {
		this.userRepository = userRepository;
		this.walletRepository = walletRepository;
		this.transactionRepository = transactionRepository;
		this.referenceNumberGenerator = referenceNumberGenerator;
	}

	@Transactional
	public TransactionResponse transfer(Long senderUserId, TransferRequest request) {
		String email = request.recipientEmail().trim().toLowerCase(Locale.ROOT);
		User recipient = userRepository.findByEmail(email)
				.orElseThrow(RecipientNotFoundException::new);

		if (recipient.getId().equals(senderUserId)) {
			throw new SelfTransferException();
		}

		Long senderWalletId = walletRepository.findIdByUserId(senderUserId)
				.orElseThrow(() -> new WalletNotFoundException(senderUserId));
		Long recipientWalletId = walletRepository.findIdByUserId(recipient.getId())
				.orElseThrow(RecipientNotFoundException::new);

		// kedua wallet dikunci sekaligus, urut berdasarkan id, lalu dibaca ulang dari hasil kunci
		List<Wallet> locked = walletRepository.lockAllByIdOrdered(List.of(senderWalletId, recipientWalletId));
		Wallet sender = findById(locked, senderWalletId);
		Wallet receiver = findById(locked, recipientWalletId);

		if (!sender.isActive()) {
			throw new WalletInactiveException();
		}
		if (!receiver.isActive()) {
			throw new WalletInactiveException("Recipient wallet is inactive");
		}

		BigDecimal amount = request.amount().setScale(2);
		if (sender.getBalance().compareTo(amount) < 0) {
			throw new InsufficientBalanceException();
		}

		sender.debit(amount);
		receiver.credit(amount);

		String reference = referenceNumberGenerator.generate();
		String description = request.description() == null || request.description().isBlank()
				? DEFAULT_DESCRIPTION
				: request.description().trim();

		Transaction debit = transactionRepository.save(new Transaction(
				sender, TransactionType.DEBIT, TransactionCategory.TRANSFER,
				amount, sender.getBalance(), reference, description));
		transactionRepository.save(new Transaction(
				receiver, TransactionType.CREDIT, TransactionCategory.TRANSFER,
				amount, receiver.getBalance(), reference, description));

		return TransactionResponse.from(debit);
	}

	private static Wallet findById(List<Wallet> wallets, Long id) {
		return wallets.stream()
				.filter(wallet -> wallet.getId().equals(id))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Wallet not locked: " + id));
	}
}
