package com.ewallet.transfer.controller;

import com.ewallet.common.security.CurrentUser;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transfer.dto.TopUpRequest;
import com.ewallet.transfer.service.TopUpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallets")
public class TopUpController {

	private final TopUpService topUpService;

	public TopUpController(TopUpService topUpService) {
		this.topUpService = topUpService;
	}

	@PostMapping("/top-up")
	public ResponseEntity<TransactionResponse> topUp(@AuthenticationPrincipal Jwt jwt,
													 @Valid @RequestBody TopUpRequest request) {
		TransactionResponse response = topUpService.topUp(CurrentUser.id(jwt), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
