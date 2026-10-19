package com.ewallet.user;

import com.ewallet.auth.service.JwtService;
import com.ewallet.entity.User;
import com.ewallet.transaction.repository.TransactionRepository;
import com.ewallet.user.repository.UserRepository;
import com.ewallet.wallet.repository.WalletRepository;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class UserControllerIntegrationTest {

	private static final String ME = "/api/users/me";

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

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JwtEncoder jwtEncoder;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();

		transactionRepository.deleteAll();
		walletRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void me_rejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(get(ME))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void me_rejectsMalformedToken() throws Exception {
		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer bukan-token-yang-valid"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void me_rejectsExpiredToken() throws Exception {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject("1")
				.issuedAt(now.minus(2, ChronoUnit.HOURS))
				.expiresAt(now.minus(1, ChronoUnit.HOURS))
				.build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header(), claims)).getTokenValue();

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void me_rejectsTokenSignedWithAnotherKey() throws Exception {
		SecretKeySpec otherKey = new SecretKeySpec(
				"kunci-lain-yang-juga-minimal-32-karakter-abc".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		NimbusJwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject("1")
				.issuedAt(now)
				.expiresAt(now.plus(1, ChronoUnit.HOURS))
				.build();
		String token = otherEncoder.encode(JwtEncoderParameters.from(header(), claims)).getTokenValue();

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void me_returnsProfileForValidToken() throws Exception {
		User user = userRepository.save(new User("budi@mail.com", passwordEncoder.encode("rahasia123"), "Budi"));
		String token = jwtService.generateToken(user);

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()))
				.andExpect(jsonPath("$.email").value("budi@mail.com"))
				.andExpect(jsonPath("$.name").value("Budi"))
				.andExpect(jsonPath("$.password").doesNotExist());
	}

	@Test
	void me_returnsNotFoundWhenUserNoLongerExists() throws Exception {
		User user = userRepository.save(new User("budi@mail.com", passwordEncoder.encode("rahasia123"), "Budi"));
		String token = jwtService.generateToken(user);
		userRepository.deleteAll();

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void registerThenLoginThenAccessProfile() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"budi@mail.com\", \"password\": \"rahasia123\", \"name\": \"Budi\"}"))
				.andExpect(status().isCreated());

		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"budi@mail.com\", \"password\": \"rahasia123\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(body, "$.accessToken");

		mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("budi@mail.com"));
	}

	private static JwsHeader header() {
		return JwsHeader.with(MacAlgorithm.HS256).build();
	}
}
