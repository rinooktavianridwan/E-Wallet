package com.ewallet.transaction;

import com.ewallet.IntegrationTestBase;
import com.ewallet.entity.User;
import com.ewallet.transfer.dto.TopUpRequest;
import com.ewallet.transfer.dto.TransferRequest;
import com.ewallet.transfer.service.TopUpService;
import com.ewallet.transfer.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerIntegrationTest extends IntegrationTestBase {

	private static final String HISTORY = "/api/transactions";

	@Autowired
	private TopUpService topUpService;

	@Autowired
	private TransferService transferService;

	private ResultActions history(User user, String query) throws Exception {
		return mockMvc.perform(get(HISTORY + query).header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private void topUp(User user, String amount, String description) {
		topUpService.topUp(user.getId(), new TopUpRequest(new BigDecimal(amount), description));
	}

	@Test
	void history_rejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(get(HISTORY))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void history_isEmptyForNewUser() throws Exception {
		User user = createUser("budi@mail.com");

		history(user, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void history_showsOnlyOwnTransactions() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");
		topUp(budi, "100000", "Isi saldo");
		transferService.transfer(budi.getId(), new TransferRequest("sari@mail.com", new BigDecimal("40000"), "Makan"));

		history(budi, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].type").value("DEBIT"))
				.andExpect(jsonPath("$.content[1].type").value("CREDIT"));

		history(sari, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].type").value("CREDIT"))
				.andExpect(jsonPath("$.content[0].category").value("TRANSFER"))
				.andExpect(jsonPath("$.content[0].balanceAfter").value(40000.00));
	}

	@Test
	void history_isOrderedNewestFirst() throws Exception {
		User user = createUser("budi@mail.com");
		topUp(user, "1000", "pertama");
		topUp(user, "2000", "kedua");
		topUp(user, "3000", "ketiga");

		history(user, "")
				.andExpect(jsonPath("$.content[0].description").value("ketiga"))
				.andExpect(jsonPath("$.content[1].description").value("kedua"))
				.andExpect(jsonPath("$.content[2].description").value("pertama"));
	}

	@Test
	void history_paginatesResults() throws Exception {
		User user = createUser("budi@mail.com");
		for (int i = 1; i <= 5; i++) {
			topUp(user, "1000", "top up " + i);
		}

		history(user, "?page=0&size=2")
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3))
				.andExpect(jsonPath("$.content[0].description").value("top up 5"));

		history(user, "?page=2&size=2")
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].description").value("top up 1"));

		history(user, "?page=9&size=2")
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.totalElements").value(5));
	}

	@Test
	void history_clampsInvalidPageAndSize() throws Exception {
		User user = createUser("budi@mail.com");

		history(user, "?page=-3&size=1000")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(100));

		history(user, "?size=0")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(1));
	}

	@Test
	void history_returnsNotFoundWhenUserHasNoWallet() throws Exception {
		User user = createUserWithoutWallet("budi@mail.com");

		history(user, "")
				.andExpect(status().isNotFound());
	}
}
