package com.booknest.walletservice.exception;

/**
 * Exception triggered when a transaction fails due to inadequate funds in the wallet.
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
