package com.inferno.user_service.exception;

public class ValidationException extends RuntimeException {
    private final int statusCode;

    public ValidationException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ValidationException(String message) {
        this(message, 400); // Por defecto 400 Bad Request
    }

    public int getStatusCode() {
        return statusCode;
    }
}