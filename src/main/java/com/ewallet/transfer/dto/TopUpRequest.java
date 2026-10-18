package com.ewallet.transfer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TopUpRequest(

		@NotNull(message = "Amount is required")
		@DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
		@DecimalMax(value = "100000000.00", message = "Amount must be at most 100000000.00")
		@Digits(integer = 15, fraction = 2, message = "Amount allows at most 2 decimal places")
		BigDecimal amount,

		@Size(max = 255, message = "Description must be at most 255 characters")
		String description
) {
}
