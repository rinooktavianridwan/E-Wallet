package com.ewallet.auth.service;

import com.ewallet.auth.dto.LoginRequest;
import com.ewallet.auth.dto.RegisterRequest;
import com.ewallet.auth.dto.RegisterResponse;
import com.ewallet.auth.dto.TokenResponse;
import com.ewallet.common.exception.EmailAlreadyUsedException;
import com.ewallet.common.exception.InvalidCredentialsException;
import com.ewallet.entity.User;
import com.ewallet.entity.Wallet;
import com.ewallet.user.repository.UserRepository;
import com.ewallet.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final WalletRepository walletRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;

	public AuthService(UserRepository userRepository,
					   WalletRepository walletRepository,
					   PasswordEncoder passwordEncoder,
					   JwtService jwtService,
					   RefreshTokenService refreshTokenService) {
		this.userRepository = userRepository;
		this.walletRepository = walletRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		// samakan huruf besar-kecil supaya email tidak dobel
		String email = request.email().trim().toLowerCase();
		String name = request.name().trim();

		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyUsedException(email);
		}

		User user;
		try {
			user = userRepository.save(new User(email, passwordEncoder.encode(request.password()), name));
		} catch (DataIntegrityViolationException ex) {
			// dua request bersamaan bisa lolos existsByEmail, constraint UNIQUE yang menolak
			throw new EmailAlreadyUsedException(email);
		}

		walletRepository.save(new Wallet(user));

		return new RegisterResponse(user.getId(), user.getEmail(), user.getName());
	}

	@Transactional
	public TokenResponse login(LoginRequest request) {
		String email = request.email().trim().toLowerCase();

		// pesan error sama untuk email tidak ada dan password salah
		User user = userRepository.findByEmail(email)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new InvalidCredentialsException();
		}

		return tokenResponse(user, refreshTokenService.issue(user.getId()));
	}

	// sengaja tanpa @Transactional: rotate() punya transaksi sendiri dan boleh commit walaupun melempar exception
	public TokenResponse refresh(String refreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
		return tokenResponse(rotation.user(), rotation.refreshToken());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	private TokenResponse tokenResponse(User user, String refreshToken) {
		return new TokenResponse(
				jwtService.generateToken(user),
				"Bearer",
				jwtService.getExpiresInSeconds(),
				refreshToken,
				refreshTokenService.getExpiresInSeconds());
	}
}
