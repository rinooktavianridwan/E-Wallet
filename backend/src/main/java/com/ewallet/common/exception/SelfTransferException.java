package com.ewallet.common.exception;

public class SelfTransferException extends RuntimeException {

	public SelfTransferException() {
		super("Cannot transfer to your own wallet");
	}
}
