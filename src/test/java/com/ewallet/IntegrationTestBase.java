package com.ewallet;

import com.ewallet.auth.service.JwtService;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.transaction.repository.TransactionRepository;
import com.ewallet.user.repository.UserRepository;
import com.ewallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected WalletRepository walletRepository;

	@Autowired
	protected TransactionRepository transactionRepository;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	protected JwtService jwtService;

	protected MockMvc mockMvc;

	@BeforeEach
	void resetState() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();

		transactionRepository.deleteAll();
		walletRepository.deleteAll();
		userRepository.deleteAll();
	}

	protected User createUser(String email) {
		User user = userRepository.save(new User(email, passwordEncoder.encode("rahasia123"), "Tester"));
		walletRepository.save(new Wallet(user));
		return user;
	}

	protected User createUserWithoutWallet(String email) {
		return userRepository.save(new User(email, passwordEncoder.encode("rahasia123"), "Tester"));
	}

	protected String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}
}
