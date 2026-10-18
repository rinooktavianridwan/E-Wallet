package com.ewallet.transfer.controller;

import com.ewallet.common.security.CurrentUser;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transfer.dto.TransferRequest;
import com.ewallet.transfer.service.TransferService;
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
@RequestMapping("/api/transfers")
public class TransferController {

	private final TransferService transferService;

	public TransferController(TransferService transferService) {
		this.transferService = transferService;
	}

	@PostMapping
	public ResponseEntity<TransactionResponse> transfer(@AuthenticationPrincipal Jwt jwt,
														@Valid @RequestBody TransferRequest request) {
		TransactionResponse response = transferService.transfer(CurrentUser.id(jwt), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
