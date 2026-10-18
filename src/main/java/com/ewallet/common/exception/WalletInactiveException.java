package com.ewallet.common.exception;

public class WalletInactiveException extends RuntimeException {

	public WalletInactiveException() {
		super("Wallet is inactive");
	}

	public WalletInactiveException(String message) {
		super(message);
	}
}
