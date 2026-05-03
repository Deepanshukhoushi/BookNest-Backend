package com.booknest.wishlistservice.exception;

/** Standard error response envelope for the wishlist-service. */
public record ErrorResponse(boolean success, String message, String errorCode) {}
