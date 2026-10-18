package com.ewallet.transfer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
		@NotBlank @Email String recipientEmail,
		@NotNull @DecimalMin("0.01") @DecimalMax("100000000.00") @Digits(integer = 15, fraction = 2) BigDecimal amount,
		@Size(max = 255) String description) {
}
