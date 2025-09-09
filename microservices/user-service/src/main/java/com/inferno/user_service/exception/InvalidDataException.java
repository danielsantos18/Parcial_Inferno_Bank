package com.inferno.user_service.exception;

public class InvalidDataException extends ValidationException {
    public InvalidDataException(String message) {
        super(message, 400); // 400 Bad Request
    }
}