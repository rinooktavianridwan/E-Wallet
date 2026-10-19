package com.ewallet.transfer.controller;

import com.ewallet.common.security.CurrentUser;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transfer.dto.TransferRequest;
import com.ewallet.transfer.service.IdempotentTransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

	private final IdempotentTransferService transferService;

	public TransferController(IdempotentTransferService transferService) {
		this.transferService = transferService;
	}

	@PostMapping
	public ResponseEntity<TransactionResponse> transfer(
			@AuthenticationPrincipal Jwt jwt,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody TransferRequest request) {
		IdempotentTransferService.Result result = transferService.transfer(CurrentUser.id(jwt), idempotencyKey, request);

		if (result.replayed()) {
			return ResponseEntity.ok()
					.header("Idempotent-Replayed", "true")
					.body(result.transaction());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(result.transaction());
	}
}
