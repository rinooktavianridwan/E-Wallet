package com.ewallet.transfer;

import com.ewallet.IntegrationTestBase;
import com.ewallet.entity.Transaction;
import com.ewallet.entity.TransactionCategory;
import com.ewallet.entity.TransactionType;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.transfer.dto.TopUpRequest;
import com.ewallet.transfer.service.TopUpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TopUpControllerIntegrationTest extends IntegrationTestBase {

	private static final String TOP_UP = "/api/wallets/top-up";

	@Autowired
	private TopUpService topUpService;

	private ResultActions topUp(User user, String body) throws Exception {
		return mockMvc.perform(post(TOP_UP)
				.header(HttpHeaders.AUTHORIZATION, bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private BigDecimal balanceOf(User user) {
		return walletRepository.findByUserId(user.getId()).orElseThrow().getBalance();
	}

	@Test
	void topUp_rejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(post(TOP_UP)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\": 10000}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void topUp_increasesBalanceAndRecordsLedger() throws Exception {
		User user = createUser("budi@mail.com");

		topUp(user, "{\"amount\": 50000, \"description\": \"Gaji\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("CREDIT"))
				.andExpect(jsonPath("$.category").value("TOP_UP"))
				.andExpect(jsonPath("$.amount").value(50000.00))
				.andExpect(jsonPath("$.balanceAfter").value(50000.00))
				.andExpect(jsonPath("$.description").value("Gaji"))
				.andExpect(jsonPath("$.referenceNumber").isNotEmpty());

		assertThat(balanceOf(user)).isEqualByComparingTo("50000");

		List<Transaction> rows = transactionRepository.findAll();
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getTransactionType()).isEqualTo(TransactionType.CREDIT);
		assertThat(rows.get(0).getCategory()).isEqualTo(TransactionCategory.TOP_UP);
		assertThat(rows.get(0).getBalanceAfter()).isEqualByComparingTo("50000");
	}

	@Test
	void topUp_accumulatesAndUsesDifferentReferences() throws Exception {
		User user = createUser("budi@mail.com");

		topUp(user, "{\"amount\": 10000}").andExpect(status().isCreated());
		topUp(user, "{\"amount\": 25000.50}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.balanceAfter").value(35000.50))
				.andExpect(jsonPath("$.description").value("Top up"));

		assertThat(balanceOf(user)).isEqualByComparingTo("35000.50");

		List<Transaction> rows = transactionRepository.findAll();
		assertThat(rows).hasSize(2);
		assertThat(rows.stream().map(Transaction::getReferenceNumber).distinct()).hasSize(2);
	}

	@Test
	void topUp_rejectsInvalidAmounts() throws Exception {
		User user = createUser("budi@mail.com");

		String[] bodies = {
				"{\"amount\": 0}",
				"{\"amount\": -5}",
				"{\"amount\": 10.123}",
				"{}",
				"{\"amount\": 100000000.01}"
		};

		for (String body : bodies) {
			topUp(user, body).andExpect(status().isBadRequest());
		}

		assertThat(balanceOf(user)).isEqualByComparingTo("0");
		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void topUp_rejectsInactiveWallet() throws Exception {
		User user = createUser("budi@mail.com");
		Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
		wallet.setActive(false);
		walletRepository.save(wallet);

		topUp(user, "{\"amount\": 10000}")
				.andExpect(status().isForbidden());

		assertThat(balanceOf(user)).isEqualByComparingTo("0");
		assertThat(transactionRepository.count()).isZero();
	}

	@Test
	void topUp_returnsNotFoundWhenUserHasNoWallet() throws Exception {
		User user = createUserWithoutWallet("budi@mail.com");

		topUp(user, "{\"amount\": 10000}")
				.andExpect(status().isNotFound());
	}

	@Test
	void topUp_parallelRequestsDoNotLoseUpdates() throws Exception {
		User user = createUser("budi@mail.com");
		int requests = 20;
		TopUpRequest request = new TopUpRequest(new BigDecimal("1000"), null);

		ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			List<Callable<Object>> tasks = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				tasks.add(() -> topUpService.topUp(user.getId(), request));
			}
			for (Future<Object> future : executor.invokeAll(tasks)) {
				future.get();
			}
		} finally {
			executor.shutdown();
		}

		assertThat(balanceOf(user)).isEqualByComparingTo("20000");

		List<Transaction> rows = transactionRepository.findAll();
		assertThat(rows).hasSize(requests);
		assertThat(rows.stream().map(Transaction::getReferenceNumber).distinct()).hasSize(requests);
	}
}
