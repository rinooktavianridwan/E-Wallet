package com.ewallet.transfer;

import com.ewallet.common.exception.InsufficientBalanceException;
import com.ewallet.common.exception.RecipientNotFoundException;
import com.ewallet.common.exception.SelfTransferException;
import com.ewallet.common.exception.WalletInactiveException;
import com.ewallet.entity.Transaction;
import com.ewallet.entity.TransactionCategory;
import com.ewallet.entity.TransactionType;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transaction.repository.TransactionRepository;
import com.ewallet.transaction.service.ReferenceNumberGenerator;
import com.ewallet.transfer.dto.TransferRequest;
import com.ewallet.transfer.service.TransferService;
import com.ewallet.user.repository.UserRepository;
import com.ewallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

	private static final Long SENDER_USER_ID = 1L;
	private static final Long RECIPIENT_USER_ID = 2L;
	private static final Long SENDER_WALLET_ID = 10L;
	private static final Long RECIPIENT_WALLET_ID = 20L;

	@Mock
	private UserRepository userRepository;

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private ReferenceNumberGenerator referenceNumberGenerator;

	private TransferService service;
	private User recipientUser;
	private Wallet senderWallet;
	private Wallet recipientWallet;

	@BeforeEach
	void setUp() {
		service = new TransferService(userRepository, walletRepository, transactionRepository, referenceNumberGenerator);

		recipientUser = new User("sari@mail.com", "hash", "Sari");
		ReflectionTestUtils.setField(recipientUser, "id", RECIPIENT_USER_ID);

		senderWallet = new Wallet(new User("budi@mail.com", "hash", "Budi"));
		ReflectionTestUtils.setField(senderWallet, "id", SENDER_WALLET_ID);
		senderWallet.credit(new BigDecimal("100000.00"));

		recipientWallet = new Wallet(recipientUser);
		ReflectionTestUtils.setField(recipientWallet, "id", RECIPIENT_WALLET_ID);
	}

	private void stubLookups() {
		when(userRepository.findByEmail("sari@mail.com")).thenReturn(Optional.of(recipientUser));
		when(walletRepository.findIdByUserId(SENDER_USER_ID)).thenReturn(Optional.of(SENDER_WALLET_ID));
		when(walletRepository.findIdByUserId(RECIPIENT_USER_ID)).thenReturn(Optional.of(RECIPIENT_WALLET_ID));
		when(walletRepository.lockAllByIdOrdered(List.of(SENDER_WALLET_ID, RECIPIENT_WALLET_ID)))
				.thenReturn(List.of(senderWallet, recipientWallet));
	}

	@Test
	void transfer_movesMoneyAndWritesTwoLedgerRows() {
		stubLookups();
		when(referenceNumberGenerator.generate()).thenReturn("TRX-20260101-ABCDEF1234");
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

		TransactionResponse response = service.transfer(SENDER_USER_ID,
				new TransferRequest(" Sari@Mail.com ", new BigDecimal("30000"), "Bayar makan"));

		assertThat(senderWallet.getBalance()).isEqualByComparingTo("70000");
		assertThat(recipientWallet.getBalance()).isEqualByComparingTo("30000");

		ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
		verify(transactionRepository, times(2)).save(captor.capture());
		Transaction debit = captor.getAllValues().get(0);
		Transaction credit = captor.getAllValues().get(1);

		assertThat(debit.getTransactionType()).isEqualTo(TransactionType.DEBIT);
		assertThat(debit.getCategory()).isEqualTo(TransactionCategory.TRANSFER);
		assertThat(debit.getBalanceAfter()).isEqualByComparingTo("70000");
		assertThat(credit.getTransactionType()).isEqualTo(TransactionType.CREDIT);
		assertThat(credit.getCategory()).isEqualTo(TransactionCategory.TRANSFER);
		assertThat(credit.getBalanceAfter()).isEqualByComparingTo("30000");
		assertThat(debit.getReferenceNumber()).isEqualTo(credit.getReferenceNumber());
		assertThat(debit.getDescription()).isEqualTo("Bayar makan");

		assertThat(response.type()).isEqualTo(TransactionType.DEBIT);
		assertThat(response.referenceNumber()).isEqualTo("TRX-20260101-ABCDEF1234");
	}

	@Test
	void transfer_usesDefaultDescriptionWhenBlank() {
		stubLookups();
		when(referenceNumberGenerator.generate()).thenReturn("TRX-20260101-ABCDEF1234");
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

		service.transfer(SENDER_USER_ID, new TransferRequest("sari@mail.com", new BigDecimal("1000"), null));

		ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
		verify(transactionRepository, times(2)).save(captor.capture());
		assertThat(captor.getAllValues()).allMatch(tx -> "Transfer".equals(tx.getDescription()));
	}

	@Test
	void transfer_rejectsInsufficientBalance() {
		stubLookups();

		assertThatThrownBy(() -> service.transfer(SENDER_USER_ID,
				new TransferRequest("sari@mail.com", new BigDecimal("100000.01"), null)))
				.isInstanceOf(InsufficientBalanceException.class);

		assertThat(senderWallet.getBalance()).isEqualByComparingTo("100000");
		assertThat(recipientWallet.getBalance()).isEqualByComparingTo("0");
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void transfer_rejectsTransferToSelf() {
		when(userRepository.findByEmail("sari@mail.com")).thenReturn(Optional.of(recipientUser));

		assertThatThrownBy(() -> service.transfer(RECIPIENT_USER_ID,
				new TransferRequest("sari@mail.com", new BigDecimal("1000"), null)))
				.isInstanceOf(SelfTransferException.class);

		verify(transactionRepository, never()).save(any());
	}

	@Test
	void transfer_failsWhenRecipientUnknown() {
		when(userRepository.findByEmail("sari@mail.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.transfer(SENDER_USER_ID,
				new TransferRequest("sari@mail.com", new BigDecimal("1000"), null)))
				.isInstanceOf(RecipientNotFoundException.class);

		verify(transactionRepository, never()).save(any());
	}

	@Test
	void transfer_rejectsInactiveRecipientWallet() {
		stubLookups();
		recipientWallet.setActive(false);

		assertThatThrownBy(() -> service.transfer(SENDER_USER_ID,
				new TransferRequest("sari@mail.com", new BigDecimal("1000"), null)))
				.isInstanceOf(WalletInactiveException.class);

		assertThat(senderWallet.getBalance()).isEqualByComparingTo("100000");
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void transfer_rejectsInactiveSenderWallet() {
		stubLookups();
		senderWallet.setActive(false);

		assertThatThrownBy(() -> service.transfer(SENDER_USER_ID,
				new TransferRequest("sari@mail.com", new BigDecimal("1000"), null)))
				.isInstanceOf(WalletInactiveException.class);

		verify(transactionRepository, never()).save(any());
	}
}
