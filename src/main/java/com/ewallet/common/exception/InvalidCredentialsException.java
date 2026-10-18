package com.ewallet.common.exception;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Email or password is incorrect");
	}
}
