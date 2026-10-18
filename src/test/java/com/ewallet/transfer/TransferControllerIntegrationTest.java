package com.ewallet.transfer;

import com.ewallet.IntegrationTestBase;
import com.ewallet.common.exception.InsufficientBalanceException;
import com.ewallet.entity.Transaction;
import com.ewallet.entity.TransactionType;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.transfer.dto.TransferRequest;
import com.ewallet.transfer.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferControllerIntegrationTest extends IntegrationTestBase {

	private static final String TRANSFERS = "/api/transfers";

	@Autowired
	private TransferService transferService;

	private ResultActions transfer(User sender, String body) throws Exception {
		return mockMvc.perform(post(TRANSFERS)
				.header(HttpHeaders.AUTHORIZATION, bearer(sender))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private void fund(User user, String amount) {
		Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
		wallet.credit(new BigDecimal(amount));
		walletRepository.save(wallet);
	}

	private BigDecimal balanceOf(User user) {
		return walletRepository.findByUserId(user.getId()).orElseThrow().getBalance();
	}

	@Test
	void transfer_rejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(post(TRANSFERS)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipientEmail\": \"sari@mail.com\", \"amount\": 1000}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void transfer_movesMoneyAndWritesTwoLedgerRows() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "100000");

		transfer(budi, "{\"recipientEmail\": \"SARI@mail.com\", \"amount\": 40000, \"description\": \"Bayar makan\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("DEBIT"))
				.andExpect(jsonPath("$.category").value("TRANSFER"))
				.andExpect(jsonPath("$.amount").value(40000.00))
				.andExpect(jsonPath("$.balanceAfter").value(60000.00));

		assertThat(balanceOf(budi)).isEqualByComparingTo("60000");
		assertThat(balanceOf(sari)).isEqualByComparingTo("40000");

		List<Transaction> rows = transactionRepository.findAll();
		assertThat(rows).hasSize(2);
		assertThat(rows.stream().map(Transaction::getReferenceNumber).distinct()).hasSize(1);
		assertThat(rows.stream().map(Transaction::getTransactionType))
				.containsExactlyInAnyOrder(TransactionType.DEBIT, TransactionType.CREDIT);
	}

	@Test
	void transfer_rejectsInsufficientBalance() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "1000");

		transfer(budi, "{\"recipientEmail\": \"sari@mail.com\", \"amount\": 1000.01}")
				.andExpect(status().isUnprocessableEntity());

		assertThat(balanceOf(budi)).isEqualByComparingTo("1000");
		assertThat(balanceOf(sari)).isEqualByComparingTo("0");
		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void transfer_rejectsTransferToSelf() throws Exception {
		User budi = createUser("budi@mail.com");
		fund(budi, "1000");

		transfer(budi, "{\"recipientEmail\": \"budi@mail.com\", \"amount\": 500}")
				.andExpect(status().isBadRequest());

		assertThat(balanceOf(budi)).isEqualByComparingTo("1000");
		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void transfer_returnsNotFoundForUnknownRecipient() throws Exception {
		User budi = createUser("budi@mail.com");
		fund(budi, "1000");

		transfer(budi, "{\"recipientEmail\": \"tidakada@mail.com\", \"amount\": 500}")
				.andExpect(status().isNotFound());

		assertThat(balanceOf(budi)).isEqualByComparingTo("1000");
	}

	@Test
	void transfer_rejectsInvalidBodies() throws Exception {
		User budi = createUser("budi@mail.com");
		createUser("sari@mail.com");
		fund(budi, "1000");

		String[] bodies = {
				"{\"amount\": 500}",
				"{\"recipientEmail\": \"bukan-email\", \"amount\": 500}",
				"{\"recipientEmail\": \"sari@mail.com\"}",
				"{\"recipientEmail\": \"sari@mail.com\", \"amount\": 0}",
				"{\"recipientEmail\": \"sari@mail.com\", \"amount\": -1}",
				"{\"recipientEmail\": \"sari@mail.com\", \"amount\": 1.234}"
		};

		for (String body : bodies) {
			transfer(budi, body).andExpect(status().isBadRequest());
		}

		assertThat(balanceOf(budi)).isEqualByComparingTo("1000");
		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void transfer_rejectsInactiveSenderAndRecipient() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "1000");

		Wallet sariWallet = walletRepository.findByUserId(sari.getId()).orElseThrow();
		sariWallet.setActive(false);
		walletRepository.save(sariWallet);

		transfer(budi, "{\"recipientEmail\": \"sari@mail.com\", \"amount\": 500}")
				.andExpect(status().isForbidden());

		Wallet budiWallet = walletRepository.findByUserId(budi.getId()).orElseThrow();
		budiWallet.setActive(false);
		walletRepository.save(budiWallet);

		transfer(budi, "{\"recipientEmail\": \"sari@mail.com\", \"amount\": 500}")
				.andExpect(status().isForbidden());

		assertThat(balanceOf(budi)).isEqualByComparingTo("1000");
		assertThat(balanceOf(sari)).isEqualByComparingTo("0");
		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void transfer_parallelRequestsNeverOverdrawBalance() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "500");
		TransferRequest request = new TransferRequest("sari@mail.com", new BigDecimal("100"), null);

		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tasks.add(() -> {
				try {
					transferService.transfer(budi.getId(), request);
					return true;
				} catch (InsufficientBalanceException e) {
					return false;
				}
			});
		}

		int succeeded = 0;
		for (boolean ok : runParallel(tasks)) {
			if (ok) {
				succeeded++;
			}
		}

		assertThat(succeeded).isEqualTo(5);
		assertThat(balanceOf(budi)).isEqualByComparingTo("0");
		assertThat(balanceOf(sari)).isEqualByComparingTo("500");
		assertThat(transactionRepository.count()).isEqualTo(10);
	}

	@Test
	void transfer_oppositeDirectionsDoNotDeadlockAndKeepTotal() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "10000");
		fund(sari, "10000");
		TransferRequest toSari = new TransferRequest("sari@mail.com", new BigDecimal("100"), null);
		TransferRequest toBudi = new TransferRequest("budi@mail.com", new BigDecimal("100"), null);

		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			tasks.add(() -> {
				transferService.transfer(budi.getId(), toSari);
				return true;
			});
			tasks.add(() -> {
				transferService.transfer(sari.getId(), toBudi);
				return true;
			});
		}

		runParallel(tasks);

		assertThat(balanceOf(budi).add(balanceOf(sari))).isEqualByComparingTo("20000");
		assertThat(balanceOf(budi)).isEqualByComparingTo("10000");
		assertThat(transactionRepository.count()).isEqualTo(60);
	}

	private <T> List<T> runParallel(List<Callable<T>> tasks) throws InterruptedException, ExecutionException {
		ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			List<T> results = new ArrayList<>();
			for (Future<T> future : executor.invokeAll(tasks)) {
				results.add(future.get());
			}
			return results;
		} finally {
			executor.shutdown();
		}
	}
}
