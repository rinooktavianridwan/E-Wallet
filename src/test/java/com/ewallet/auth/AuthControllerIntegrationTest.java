package com.ewallet.auth;

import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.transaction.repository.TransactionRepository;
import com.ewallet.user.repository.UserRepository;
import com.ewallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();

		// urutan hapus mengikuti foreign key
		transactionRepository.deleteAll();
		walletRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void register_createsUserAndEmptyWallet_andNeverReturnsPassword() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("budi@mail.com", "rahasia123", "Budi")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("budi@mail.com"))
				.andExpect(jsonPath("$.name").value("Budi"))
				.andExpect(jsonPath("$.password").doesNotExist());

		User user = userRepository.findByEmail("budi@mail.com").orElseThrow();
		// harus hash, bukan teks asli
		assertThat(user.getPassword()).isNotEqualTo("rahasia123");
		assertThat(passwordEncoder.matches("rahasia123", user.getPassword())).isTrue();

		Wallet wallet = walletRepository.findByUserId(user.getId()).orElseThrow();
		assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(wallet.isActive()).isTrue();
	}

	@Test
	void register_rejectsDuplicateEmail_caseInsensitive() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("budi@mail.com", "rahasia123", "Budi")))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("BUDI@Mail.com", "passwordlain", "Budi Lain")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Email already registered"));

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(walletRepository.count()).isEqualTo(1);
	}

	@Test
	void register_rejectsInvalidInput_withFieldErrors() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("bukan-email", "pendek", " ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.email").exists())
				.andExpect(jsonPath("$.errors.password").exists())
				.andExpect(jsonPath("$.errors.name").exists());

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void register_rejectsPasswordLongerThanBcryptLimit() throws Exception {
		String tooLong = "a".repeat(73);

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("budi@mail.com", tooLong, "Budi")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void login_returnsBearerToken() throws Exception {
		saveUser("budi@mail.com", "rahasia123");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("budi@mail.com", "rahasia123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600));
	}

	@Test
	void login_ignoresEmailLetterCase() throws Exception {
		saveUser("budi@mail.com", "rahasia123");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("BUDI@Mail.com", "rahasia123")))
				.andExpect(status().isOk());
	}

	@Test
	void login_rejectsWrongPasswordAndUnknownEmail_withSameMessage() throws Exception {
		saveUser("budi@mail.com", "rahasia123");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("budi@mail.com", "salah-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Email or password is incorrect"));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson("tidakada@mail.com", "rahasia123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
	}

	@Test
	void login_rejectsBlankFields() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginJson(" ", "")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.email").exists())
				.andExpect(jsonPath("$.errors.password").exists());
	}

	private void saveUser(String email, String rawPassword) {
		userRepository.save(new User(email, passwordEncoder.encode(rawPassword), "Budi"));
	}

	private static String loginJson(String email, String password) {
		return """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
	}

	private static String json(String email, String password, String name) {
		return """
				{"email": "%s", "password": "%s", "name": "%s"}
				""".formatted(email, password, name);
	}
}
