package com.ewallet.wallet;

import com.ewallet.IntegrationTestBase;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletControllerIntegrationTest extends IntegrationTestBase {

	private static final String ME = "/api/wallets/me";

	@Test
	void me_rejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(get(ME))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void me_returnsZeroBalanceForNewUser() throws Exception {
		User user = createUser("budi@mail.com");

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void me_returnsOnlyOwnWallet() throws Exception {
		User budi = createUser("budi@mail.com");
		User sari = createUser("sari@mail.com");

		Wallet budiWallet = walletRepository.findByUserId(budi.getId()).orElseThrow();
		budiWallet.credit(new BigDecimal("75000.00"));
		walletRepository.save(budiWallet);

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(budi)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(75000.00));

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(sari)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balance").value(0));
	}

	@Test
	void me_returnsNotFoundWhenUserHasNoWallet() throws Exception {
		User user = createUserWithoutWallet("budi@mail.com");

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isNotFound());
	}
}
