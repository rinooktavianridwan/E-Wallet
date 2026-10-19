package com.ewallet.transfer;

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
import com.ewallet.transfer.dto.TopUpRequest;
import com.ewallet.transfer.service.TopUpService;
import com.ewallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopUpServiceTest {

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private TransactionRepository transactionRepository;

	@Mock
	private ReferenceNumberGenerator referenceNumberGenerator;

	private TopUpService newService() {
		return new TopUpService(walletRepository, transactionRepository, referenceNumberGenerator);
	}

	private Wallet newWallet() {
		return new Wallet(new User("budi@mail.com", "hash", "Budi"));
	}

	@Test
	void topUp_creditsWalletAndRecordsLedgerEntry() {
		Wallet wallet = newWallet();
		when(walletRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(wallet));
		when(referenceNumberGenerator.generate()).thenReturn("TRX-20260101-ABCDEF1234");
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

		TransactionResponse response = newService().topUp(1L, new TopUpRequest(new BigDecimal("50000"), "Gaji"));

		assertThat(wallet.getBalance()).isEqualByComparingTo("50000");

		ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
		verify(transactionRepository).save(captor.capture());
		Transaction saved = captor.getValue();
		assertThat(saved.getTransactionType()).isEqualTo(TransactionType.CREDIT);
		assertThat(saved.getCategory()).isEqualTo(TransactionCategory.TOP_UP);
		assertThat(saved.getAmount()).isEqualByComparingTo("50000");
		assertThat(saved.getBalanceAfter()).isEqualByComparingTo("50000");
		assertThat(saved.getReferenceNumber()).isEqualTo("TRX-20260101-ABCDEF1234");
		assertThat(saved.getDescription()).isEqualTo("Gaji");

		assertThat(response.referenceNumber()).isEqualTo("TRX-20260101-ABCDEF1234");
		assertThat(response.amount()).isEqualByComparingTo("50000");
	}

	@Test
	void topUp_usesDefaultDescriptionWhenBlank() {
		Wallet wallet = newWallet();
		when(walletRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(wallet));
		when(referenceNumberGenerator.generate()).thenReturn("TRX-20260101-ABCDEF1234");
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

		newService().topUp(1L, new TopUpRequest(new BigDecimal("10000"), "   "));

		ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
		verify(transactionRepository).save(captor.capture());
		assertThat(captor.getValue().getDescription()).isEqualTo("Top up");
	}

	@Test
	void topUp_rejectsInactiveWallet() {
		Wallet wallet = newWallet();
		wallet.setActive(false);
		when(walletRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(wallet));

		assertThatThrownBy(() -> newService().topUp(1L, new TopUpRequest(new BigDecimal("10000"), null)))
				.isInstanceOf(WalletInactiveException.class);

		assertThat(wallet.getBalance()).isEqualByComparingTo("0");
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void topUp_failsWhenWalletMissing() {
		when(walletRepository.findWithLockByUserId(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> newService().topUp(1L, new TopUpRequest(new BigDecimal("10000"), null)))
				.isInstanceOf(WalletNotFoundException.class);

		verify(transactionRepository, never()).save(any());
	}
}
