package com.ewallet.common.exception;

public class RecipientNotFoundException extends RuntimeException {

	public RecipientNotFoundException() {
		super("Recipient not found");
	}
}
