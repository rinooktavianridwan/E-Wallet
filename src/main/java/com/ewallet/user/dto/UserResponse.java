package com.ewallet.user.dto;

import java.time.Instant;

public record UserResponse(Long id, String email, String name, Instant createdAt) {
}
