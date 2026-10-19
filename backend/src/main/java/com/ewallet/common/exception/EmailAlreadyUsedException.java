package com.ewallet.common.exception;

public class EmailAlreadyUsedException extends RuntimeException {

	public EmailAlreadyUsedException(String email) {
		super("Email is already registered: " + email);
	}
}
