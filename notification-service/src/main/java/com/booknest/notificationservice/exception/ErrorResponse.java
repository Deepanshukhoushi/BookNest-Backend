package com.booknest.notificationservice.exception;

/** Standard error response envelope for the notification-service. */
public record ErrorResponse(boolean success, String message, String errorCode) {}
