package com.ewallet.common.exception;

public class InsufficientBalanceException extends RuntimeException {

	public InsufficientBalanceException() {
		super("Insufficient balance");
	}
}
