package com.booknest.bookservice.exception;

/**
 * Exception triggered when an order cannot be fulfilled due to lack of inventory.
 */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
