package com.ewallet.auth.dto;

public record TokenResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		String refreshToken,
		long refreshExpiresIn) {
}
