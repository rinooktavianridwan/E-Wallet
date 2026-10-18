package com.ewallet.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(Long id, BigDecimal balance, boolean active, Instant updatedAt) {
}
