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

	public AuthService(UserRepository userRepository,
					   WalletRepository walletRepository,
					   PasswordEncoder passwordEncoder,
					   JwtService jwtService) {
		this.userRepository = userRepository;
		this.walletRepository = walletRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
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

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		String email = request.email().trim().toLowerCase();

		// pesan error sama untuk email tidak ada dan password salah
		User user = userRepository.findByEmail(email)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPassword())) {
			throw new InvalidCredentialsException();
		}

		return new TokenResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpiresInSeconds());
	}
}
