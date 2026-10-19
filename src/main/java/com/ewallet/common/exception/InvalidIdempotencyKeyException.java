package com.ewallet.common.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {

	public InvalidIdempotencyKeyException() {
		super("Idempotency-Key must be between 1 and 100 characters");
	}
}
