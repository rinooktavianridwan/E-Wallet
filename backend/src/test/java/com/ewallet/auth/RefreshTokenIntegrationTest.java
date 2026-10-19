package com.ewallet.auth;

import com.ewallet.IntegrationTestBase;
import com.ewallet.auth.service.RefreshCookieFactory;
import com.ewallet.auth.service.RefreshTokenService;
import com.ewallet.common.exception.InvalidRefreshTokenException;
import com.ewallet.entity.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
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

	private MvcResult login() throws Exception {
		return mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"budi@mail.com\", \"password\": \"rahasia123\"}"))
				.andExpect(status().isOk())
				.andReturn();
	}

	private static String setCookieHeader(MvcResult result) {
		return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
	}

	private static String cookieValue(MvcResult result) {
		String header = setCookieHeader(result);
		assertThat(header).startsWith(RefreshCookieFactory.NAME + "=");
		return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
	}

	private String loginAndGetRefreshToken() throws Exception {
		return cookieValue(login());
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie(RefreshCookieFactory.NAME, refreshToken)));
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/logout")
				.cookie(new Cookie(RefreshCookieFactory.NAME, refreshToken)));
	}

	@Test
	void login_setsHttpOnlyRefreshCookieAndKeepsItOutOfTheBody() throws Exception {
		createUser("budi@mail.com");

		MvcResult result = login();

		String header = setCookieHeader(result);
		assertThat(header).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");
		assertThat(cookieValue(result)).isNotBlank();

		String body = result.getResponse().getContentAsString();
		assertThat((Object) JsonPath.read(body, "$.accessToken")).isNotNull();
		assertThat(body).doesNotContain("refreshToken");
	}

	@Test
	void refresh_returnsNewAccessTokenAndRotatesCookie() throws Exception {
		createUser("budi@mail.com");
		String oldRefresh = loginAndGetRefreshToken();

		MvcResult result = refresh(oldRefresh)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		assertThat(cookieValue(result)).isNotEqualTo(oldRefresh);

		String newAccess = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("budi@mail.com"));
	}

	@Test
	void refresh_rejectsMissingAndUnknownCookies() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
		refresh("token-ngawur").andExpect(status().isUnauthorized());
	}

	@Test
	void refresh_reusingRotatedTokenRevokesTheWholeChain() throws Exception {
		createUser("budi@mail.com");
		String first = loginAndGetRefreshToken();

		MvcResult rotated = refresh(first).andExpect(status().isOk()).andReturn();
		String second = cookieValue(rotated);

		refresh(first).andExpect(status().isUnauthorized());
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void logout_revokesRefreshTokenAndClearsCookie() throws Exception {
		createUser("budi@mail.com");
		String refreshToken = loginAndGetRefreshToken();

		MvcResult result = logout(refreshToken).andExpect(status().isNoContent()).andReturn();

		assertThat(setCookieHeader(result)).contains("Max-Age=0");
		refresh(refreshToken).andExpect(status().isUnauthorized());
	}

	@Test
	void logout_withoutCookieStillSucceeds() throws Exception {
		mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
		logout("token-ngawur").andExpect(status().isNoContent());
	}

	@Test
	void logout_onlyAffectsTheSessionItBelongsTo() throws Exception {
		createUser("budi@mail.com");
		String laptop = loginAndGetRefreshToken();
		String phone = loginAndGetRefreshToken();

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
