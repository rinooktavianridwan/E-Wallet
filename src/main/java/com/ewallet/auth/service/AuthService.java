package com.ewallet.auth.service;

import com.ewallet.auth.dto.RegisterRequest;
import com.ewallet.auth.dto.RegisterResponse;
import com.ewallet.common.exception.EmailAlreadyUsedException;
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

	public AuthService(UserRepository userRepository,
					   WalletRepository walletRepository,
					   PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.walletRepository = walletRepository;
		this.passwordEncoder = passwordEncoder;
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
}
