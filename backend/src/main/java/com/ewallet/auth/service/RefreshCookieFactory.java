package com.ewallet.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Refresh token dikirim lewat cookie HttpOnly supaya tidak bisa dibaca JavaScript (aman dari pencurian lewat XSS).
// Path dibatasi ke /api/auth, jadi browser hanya mengirimnya ke endpoint refresh dan logout.
@Component
public class RefreshCookieFactory {

	public static final String NAME = "refresh_token";

	private static final String PATH = "/api/auth";

	private final boolean secure;

	public RefreshCookieFactory(@Value("${app.cookie.secure}") boolean secure) {
		this.secure = secure;
	}

	public ResponseCookie create(String refreshToken, long maxAgeSeconds) {
		return base(refreshToken)
				.maxAge(Duration.ofSeconds(maxAgeSeconds))
				.build();
	}

	public ResponseCookie clear() {
		return base("")
				.maxAge(Duration.ZERO)
				.build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Strict")
				.path(PATH);
	}
}
