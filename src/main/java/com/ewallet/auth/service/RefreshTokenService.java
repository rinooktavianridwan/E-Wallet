package com.ewallet.auth.service;

import com.ewallet.auth.repository.RefreshTokenRepository;
import com.ewallet.common.exception.InvalidRefreshTokenException;
import com.ewallet.entity.RefreshToken;
import com.ewallet.entity.User;
import com.ewallet.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

	public record Rotation(User user, String refreshToken) {
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final long expirationDays;

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
							   UserRepository userRepository,
							   @Value("${app.jwt.refresh-expiration-days}") long expirationDays) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.expirationDays = expirationDays;
	}

	// token mentah hanya dikembalikan sekali ke klien; yang disimpan di database hanya hash-nya
	@Transactional
	public String issue(Long userId) {
		return create(userId, UUID.randomUUID());
	}

	// noRollbackFor: pencabutan satu keluarga token harus tetap tersimpan walaupun request ini ditolak
	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public Rotation rotate(String rawToken) {
		Instant now = Instant.now();

		RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
				.orElseThrow(InvalidRefreshTokenException::new);

		if (current.isRevoked()) {
			// token lama dipakai lagi: kemungkinan dicuri, seluruh rantai dicabut
			refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
			throw new InvalidRefreshTokenException();
		}
		if (current.isExpired(now)) {
			throw new InvalidRefreshTokenException();
		}

		User user = userRepository.findById(current.getUserId())
				.orElseThrow(InvalidRefreshTokenException::new);

		current.revoke(now);
		String next = create(user.getId(), current.getFamilyId());
		return new Rotation(user, next);
	}

	@Transactional
	public void revoke(String rawToken) {
		refreshTokenRepository.findByTokenHash(hash(rawToken))
				.ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
	}

	public long getExpiresInSeconds() {
		return expirationDays * 24 * 60 * 60;
	}

	private String create(Long userId, UUID familyId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		refreshTokenRepository.save(new RefreshToken(
				userId, hash(raw), familyId, Instant.now().plus(expirationDays, ChronoUnit.DAYS)));
		return raw;
	}

	private static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
