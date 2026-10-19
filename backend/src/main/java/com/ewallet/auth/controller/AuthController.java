package com.ewallet.auth.controller;

import com.ewallet.auth.dto.AccessTokenResponse;
import com.ewallet.auth.dto.LoginRequest;
import com.ewallet.auth.dto.RegisterRequest;
import com.ewallet.auth.dto.RegisterResponse;
import com.ewallet.auth.dto.TokenResponse;
import com.ewallet.auth.service.AuthService;
import com.ewallet.auth.service.RefreshCookieFactory;
import com.ewallet.common.exception.InvalidRefreshTokenException;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@SecurityRequirements
public class AuthController {

	private final AuthService authService;
	private final RefreshCookieFactory cookies;

	public AuthController(AuthService authService, RefreshCookieFactory cookies) {
		this.authService = authService;
		this.cookies = cookies;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return withRefreshCookie(authService.login(request));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AccessTokenResponse> refresh(
			@CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		return withRefreshCookie(authService.refresh(refreshToken));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			authService.logout(refreshToken);
		}
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
				.build();
	}

	private ResponseEntity<AccessTokenResponse> withRefreshCookie(TokenResponse tokens) {
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookies.create(tokens.refreshToken(), tokens.refreshExpiresIn()).toString())
				.body(AccessTokenResponse.from(tokens));
	}
}
