package com.ewallet.auth.dto;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {

	public static AccessTokenResponse from(TokenResponse tokens) {
		return new AccessTokenResponse(tokens.accessToken(), tokens.tokenType(), tokens.expiresIn());
	}
}
