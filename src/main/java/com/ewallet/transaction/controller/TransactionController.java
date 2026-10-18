package com.ewallet.transaction.controller;

import com.ewallet.common.dto.PageResponse;
import com.ewallet.common.security.CurrentUser;
import com.ewallet.transaction.dto.TransactionResponse;
import com.ewallet.transaction.service.TransactionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

	private final TransactionService transactionService;

	public TransactionController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	@GetMapping
	public PageResponse<TransactionResponse> history(@AuthenticationPrincipal Jwt jwt,
													 @RequestParam(defaultValue = "0") int page,
													 @RequestParam(defaultValue = "20") int size) {
		return transactionService.getHistory(CurrentUser.id(jwt), page, size);
	}
}
