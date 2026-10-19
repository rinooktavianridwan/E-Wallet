package com.ewallet.auth;

import com.ewallet.auth.dto.LoginRequest;
import com.ewallet.auth.dto.RegisterRequest;
import com.ewallet.auth.dto.RegisterResponse;
import com.ewallet.auth.dto.TokenResponse;
import com.ewallet.auth.service.AuthService;
import com.ewallet.auth.service.JwtService;
import com.ewallet.auth.service.RefreshTokenService;
import com.ewallet.common.exception.EmailAlreadyUsedException;
import com.ewallet.common.exception.InvalidCredentialsException;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.user.repository.UserRepository;
import com.ewallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private RefreshTokenService refreshTokenService;

	@InjectMocks
	private AuthService authService;

	@Test
	void register_savesUserWithHashedPasswordAndCreatesEmptyWallet() {
		when(userRepository.existsByEmail("budi@mail.com")).thenReturn(false);
		when(passwordEncoder.encode("rahasia123")).thenReturn("HASHED");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// huruf besar dan spasi, untuk uji normalisasi
		RegisterResponse response = authService.register(
				new RegisterRequest("  Budi@Mail.com ", "rahasia123", " Budi "));

		assertThat(response.email()).isEqualTo("budi@mail.com");
		assertThat(response.name()).isEqualTo("Budi");

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue().getPassword()).isEqualTo("HASHED");

		ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
		verify(walletRepository).save(walletCaptor.capture());
		assertThat(walletCaptor.getValue().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(walletCaptor.getValue().getUser()).isSameAs(userCaptor.getValue());
	}

	@Test
	void register_rejectsEmailThatAlreadyExists() {
		when(userRepository.existsByEmail("budi@mail.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(
				new RegisterRequest("budi@mail.com", "rahasia123", "Budi")))
				.isInstanceOf(EmailAlreadyUsedException.class);

		verify(userRepository, never()).save(any(User.class));
		verify(walletRepository, never()).save(any(Wallet.class));
	}

	@Test
	void register_convertsDatabaseUniqueViolationIntoEmailAlreadyUsed() {
		// race condition: existsByEmail lolos, insert ditolak constraint UNIQUE
		when(userRepository.existsByEmail("budi@mail.com")).thenReturn(false);
		when(passwordEncoder.encode("rahasia123")).thenReturn("HASHED");
		when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

		assertThatThrownBy(() -> authService.register(
				new RegisterRequest("budi@mail.com", "rahasia123", "Budi")))
				.isInstanceOf(EmailAlreadyUsedException.class);

		verify(walletRepository, never()).save(any(Wallet.class));
	}

	@Test
	void login_returnsTokenWhenCredentialsMatch() {
		User user = new User("budi@mail.com", "HASHED", "Budi");
		when(userRepository.findByEmail("budi@mail.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("rahasia123", "HASHED")).thenReturn(true);
		when(jwtService.generateToken(user)).thenReturn("TOKEN");
		when(jwtService.getExpiresInSeconds()).thenReturn(3600L);
		when(refreshTokenService.issue(any())).thenReturn("REFRESH");
		when(refreshTokenService.getExpiresInSeconds()).thenReturn(604800L);

		TokenResponse response = authService.login(new LoginRequest(" Budi@Mail.com ", "rahasia123"));

		assertThat(response.accessToken()).isEqualTo("TOKEN");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(3600L);
		assertThat(response.refreshToken()).isEqualTo("REFRESH");
		assertThat(response.refreshExpiresIn()).isEqualTo(604800L);
	}

	@Test
	void login_rejectsWrongPassword() {
		User user = new User("budi@mail.com", "HASHED", "Budi");
		when(userRepository.findByEmail("budi@mail.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("salah", "HASHED")).thenReturn(false);

		assertThatThrownBy(() -> authService.login(new LoginRequest("budi@mail.com", "salah")))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(jwtService, never()).generateToken(any(User.class));
	}

	@Test
	void login_rejectsUnknownEmail() {
		when(userRepository.findByEmail("tidakada@mail.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("tidakada@mail.com", "rahasia123")))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(jwtService, never()).generateToken(any(User.class));
	}
}
