package com.ewallet.transfer;

import com.ewallet.IntegrationTestBase;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.transfer.dto.TransferRequest;
import com.ewallet.transfer.service.IdempotentTransferService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferIdempotencyIntegrationTest extends IntegrationTestBase {

	private static final String TRANSFERS = "/api/transfers";

	@Autowired
	private IdempotentTransferService idempotentTransferService;

	private ResultActions transfer(User sender, String key, String amount) throws Exception {
		var request = post(TRANSFERS)
				.header(HttpHeaders.AUTHORIZATION, bearer(sender))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"recipientEmail\": \"sari@mail.com\", \"amount\": " + amount + "}");
		if (key != null) {
			request.header("Idempotency-Key", key);
		}
		return mockMvc.perform(request);
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
	void sameKeyTwice_transfersOnlyOnceAndReturnsSameResult() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "100000");

		MvcResult first = transfer(budi, "kunci-1", "40000")
				.andExpect(status().isCreated())
				.andReturn();
		MvcResult second = transfer(budi, "kunci-1", "40000")
				.andExpect(status().isOk())
				.andExpect(header().string("Idempotent-Replayed", "true"))
				.andReturn();

		String firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
		String secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.id");
		assertThat(secondId).isEqualTo(firstId);

		assertThat(balanceOf(budi)).isEqualByComparingTo("60000");
		assertThat(balanceOf(sari)).isEqualByComparingTo("40000");
		assertThat(transactionRepository.count()).isEqualTo(2);
	}

	@Test
	void sameKeyWithDifferentRequest_isRejected() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "100000");

		transfer(budi, "kunci-1", "40000").andExpect(status().isCreated());
		transfer(budi, "kunci-1", "50000").andExpect(status().isUnprocessableEntity());

		assertThat(balanceOf(budi)).isEqualByComparingTo("60000");
		assertThat(balanceOf(sari)).isEqualByComparingTo("40000");
	}

	@Test
	void differentKeys_createSeparateTransfers() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "100000");

		transfer(budi, "kunci-1", "10000").andExpect(status().isCreated());
		transfer(budi, "kunci-2", "10000").andExpect(status().isCreated());

		assertThat(balanceOf(budi)).isEqualByComparingTo("80000");
		assertThat(balanceOf(sari)).isEqualByComparingTo("20000");
	}

	@Test
	void sameKeyFromDifferentUsers_doesNotCollide() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		User andi = createUser("andi@mail.com");
		fund(budi, "50000");
		fund(andi, "50000");

		transfer(budi, "kunci-sama", "10000").andExpect(status().isCreated());
		mockMvc.perform(post(TRANSFERS)
						.header(HttpHeaders.AUTHORIZATION, bearer(andi))
						.header("Idempotency-Key", "kunci-sama")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"recipientEmail\": \"sari@mail.com\", \"amount\": 10000}"))
				.andExpect(status().isCreated());

		assertThat(balanceOf(sari)).isEqualByComparingTo("20000");
	}

	@Test
	void invalidKey_isRejected() throws Exception {
		User budi = createUser("budi@mail.com");
		createUser("sari@mail.com");
		fund(budi, "50000");

		transfer(budi, "x".repeat(101), "1000").andExpect(status().isBadRequest());
		transfer(budi, "   ", "1000").andExpect(status().isBadRequest());

		assertThat(balanceOf(budi)).isEqualByComparingTo("50000");
	}

	@Test
	void failedTransfer_doesNotConsumeKey() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "1000");

		transfer(budi, "kunci-1", "5000").andExpect(status().isUnprocessableEntity());

		fund(budi, "9000");
		transfer(budi, "kunci-1", "5000").andExpect(status().isCreated());

		assertThat(balanceOf(budi)).isEqualByComparingTo("5000");
		assertThat(balanceOf(sari)).isEqualByComparingTo("5000");
	}

	@Test
	void requestWithoutKey_stillWorks() throws Exception {
		User budi = createUser("budi@mail.com");
		createUser("sari@mail.com");
		fund(budi, "10000");

		transfer(budi, null, "1000").andExpect(status().isCreated());
		transfer(budi, null, "1000").andExpect(status().isCreated());

		assertThat(balanceOf(budi)).isEqualByComparingTo("8000");
	}

	@Test
	void parallelRequestsWithSameKey_transferOnlyOnce() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		fund(budi, "100");
		TransferRequest request = new TransferRequest("sari@mail.com", new BigDecimal("100"), null);

		List<Callable<IdempotentTransferService.Result>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tasks.add(() -> idempotentTransferService.transfer(budi.getId(), "kunci-paralel", request));
		}

		Set<Object> transactionIds = new HashSet<>();
		long replayed = 0;
		ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			for (Future<IdempotentTransferService.Result> future : executor.invokeAll(tasks)) {
				IdempotentTransferService.Result result = future.get();
				transactionIds.add(result.transaction().id());
				if (result.replayed()) {
					replayed++;
				}
			}
		} finally {
			executor.shutdown();
		}

		assertThat(transactionIds).hasSize(1);
		assertThat(replayed).isEqualTo(9);
		assertThat(balanceOf(budi)).isEqualByComparingTo("0");
		assertThat(balanceOf(sari)).isEqualByComparingTo("100");
		assertThat(transactionRepository.count()).isEqualTo(2);
	}
}
