package com.booknest.reviewservice.exception;

/**
 * Standard error response envelope used by the GlobalExceptionHandler.
 * All error responses from the review-service use this format:
 * {@code {"success": false, "message": "...", "errorCode": "..."}}
 */
public record ErrorResponse(boolean success, String message, String errorCode) {}
