package com.ewallet.auth;

import com.ewallet.IntegrationTestBase;
import com.ewallet.auth.service.RefreshTokenService;
import com.ewallet.common.exception.InvalidRefreshTokenException;
import com.ewallet.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenIntegrationTest extends IntegrationTestBase {

	@Autowired
	private RefreshTokenService refreshTokenService;

	private String json(String key, String value) {
		return "{\"" + key + "\": \"" + value + "\"}";
	}

	private String loginAndGet(String field) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"budi@mail.com\", \"password\": \"rahasia123\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$." + field);
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("refreshToken", refreshToken)));
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("refreshToken", refreshToken)));
	}

	@Test
	void login_returnsRefreshToken() throws Exception {
		createUser("budi@mail.com");

		String refreshToken = loginAndGet("refreshToken");

		assertThat(refreshToken).isNotBlank();
	}

	@Test
	void refresh_returnsNewTokensAndNewAccessTokenWorks() throws Exception {
		createUser("budi@mail.com");
		String oldRefresh = loginAndGet("refreshToken");

		String body = refresh(oldRefresh)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		String newRefresh = JsonPath.read(body, "$.refreshToken");
		String newAccess = JsonPath.read(body, "$.accessToken");
		assertThat(newRefresh).isNotEqualTo(oldRefresh);

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("budi@mail.com"));
	}

	@Test
	void refresh_rejectsUnknownAndBlankTokens() throws Exception {
		refresh("token-ngawur").andExpect(status().isUnauthorized());
		refresh("").andExpect(status().isBadRequest());
	}

	@Test
	void refresh_reusingRotatedTokenRevokesTheWholeChain() throws Exception {
		createUser("budi@mail.com");
		String first = loginAndGet("refreshToken");

		String body = refresh(first).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String second = JsonPath.read(body, "$.refreshToken");

		refresh(first).andExpect(status().isUnauthorized());
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void logout_revokesRefreshToken() throws Exception {
		createUser("budi@mail.com");
		String refreshToken = loginAndGet("refreshToken");

		logout(refreshToken).andExpect(status().isNoContent());

		refresh(refreshToken).andExpect(status().isUnauthorized());
	}

	@Test
	void logout_withUnknownTokenStillSucceeds() throws Exception {
		logout("token-ngawur").andExpect(status().isNoContent());
	}

	@Test
	void logout_onlyAffectsTheSessionItBelongsTo() throws Exception {
		createUser("budi@mail.com");
		String laptop = loginAndGet("refreshToken");
		String phone = loginAndGet("refreshToken");

		logout(laptop).andExpect(status().isNoContent());

		refresh(phone).andExpect(status().isOk());
	}

	@Test
	void parallelRefreshWithSameToken_allowsOnlyOneSuccess() throws Exception {
		User user = createUser("budi@mail.com");
		String token = refreshTokenService.issue(user.getId());

		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			tasks.add(() -> {
				try {
					refreshTokenService.rotate(token);
					return true;
				} catch (InvalidRefreshTokenException e) {
					return false;
				}
			});
		}

		int succeeded = 0;
		ExecutorService executor = Executors.newFixedThreadPool(6);
		try {
			for (Future<Boolean> future : executor.invokeAll(tasks)) {
				if (future.get()) {
					succeeded++;
				}
			}
		} finally {
			executor.shutdown();
		}

		assertThat(succeeded).isEqualTo(1);
	}
}
