package com.ewallet.wallet.controller;

import com.ewallet.common.security.CurrentUser;
import com.ewallet.wallet.dto.WalletResponse;
import com.ewallet.wallet.service.WalletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

	private final WalletService walletService;

	public WalletController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping("/me")
	public WalletResponse me(@AuthenticationPrincipal Jwt jwt) {
		return walletService.getByUserId(CurrentUser.id(jwt));
	}
}
