package com.ewallet.auth;

import com.ewallet.auth.repository.RefreshTokenRepository;
import com.ewallet.auth.service.RefreshTokenService;
import com.ewallet.common.exception.InvalidRefreshTokenException;
import com.ewallet.entity.RefreshToken;
import com.ewallet.entity.User;
import com.ewallet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

	private static final Long USER_ID = 7L;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private UserRepository userRepository;

	private RefreshTokenService service;
	private User user;

	@BeforeEach
	void setUp() {
		service = new RefreshTokenService(refreshTokenRepository, userRepository, 7);
		user = new User("budi@mail.com", "hash", "Budi");
		ReflectionTestUtils.setField(user, "id", USER_ID);
	}

	private RefreshToken storedToken(UUID family, Instant expiresAt) {
		return new RefreshToken(USER_ID, "hash-lama", family, expiresAt);
	}

	@Test
	void issue_storesOnlyTheHashOfTheToken() {
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

		String raw = service.issue(USER_ID);

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		RefreshToken saved = captor.getValue();
		assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(raw);
		assertThat(saved.getUserId()).isEqualTo(USER_ID);
		assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
	}

	@Test
	void rotate_revokesOldTokenAndIssuesNewOneInSameFamily() {
		UUID family = UUID.randomUUID();
		RefreshToken current = storedToken(family, Instant.now().plus(1, ChronoUnit.DAYS));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(current));
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

		RefreshTokenService.Rotation rotation = service.rotate("token-lama");

		assertThat(current.isRevoked()).isTrue();
		assertThat(rotation.user()).isSameAs(user);
		assertThat(rotation.refreshToken()).isNotEqualTo("token-lama");

		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		verify(refreshTokenRepository).save(captor.capture());
		assertThat(captor.getValue().getFamilyId()).isEqualTo(family);
		assertThat(captor.getValue().isRevoked()).isFalse();
	}

	@Test
	void rotate_reuseOfRevokedTokenRevokesWholeFamily() {
		UUID family = UUID.randomUUID();
		RefreshToken current = storedToken(family, Instant.now().plus(1, ChronoUnit.DAYS));
		current.revoke(Instant.now());
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(current));

		assertThatThrownBy(() -> service.rotate("token-lama"))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository).revokeFamily(any(UUID.class), any(Instant.class));
		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
	}

	@Test
	void rotate_rejectsExpiredToken() {
		RefreshToken current = storedToken(UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.MINUTES));
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(current));

		assertThatThrownBy(() -> service.rotate("token-lama"))
				.isInstanceOf(InvalidRefreshTokenException.class);

		verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
	}

	@Test
	void rotate_rejectsUnknownToken() {
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.rotate("tidak-dikenal"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void revoke_ignoresUnknownToken() {
		when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		service.revoke("tidak-dikenal");

		verify(refreshTokenRepository, never()).revokeFamily(any(UUID.class), any(Instant.class));
	}
}
