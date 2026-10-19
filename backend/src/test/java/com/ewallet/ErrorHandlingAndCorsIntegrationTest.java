package com.ewallet;

import com.ewallet.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorHandlingAndCorsIntegrationTest extends IntegrationTestBase {

	@Value("${app.cors.allowed-origins}")
	private List<String> allowedOrigins;

	@Test
	void malformedJson_returnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{bukan json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void wrongHttpMethod_returnsMethodNotAllowed() throws Exception {
		User user = createUser("budi@mail.com");

		mockMvc.perform(get("/api/transfers").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void unknownPath_returnsNotFound() throws Exception {
		User user = createUser("budi@mail.com");

		mockMvc.perform(get("/api/tidak-ada").header(HttpHeaders.AUTHORIZATION, bearer(user)))
				.andExpect(status().isNotFound());
	}

	@Test
	void validationError_stillListsFieldErrors() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"bukan-email\", \"password\": \"pendek\", \"name\": \"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.email").exists())
				.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void preflight_isAllowedForConfiguredOrigin() throws Exception {
		String origin = allowedOrigins.get(0);

		mockMvc.perform(options("/api/transfers")
						.header(HttpHeaders.ORIGIN, origin)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
	}

	@Test
	void preflight_isRejectedForUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/transfers")
						.header(HttpHeaders.ORIGIN, "http://situs-asing.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}
}
