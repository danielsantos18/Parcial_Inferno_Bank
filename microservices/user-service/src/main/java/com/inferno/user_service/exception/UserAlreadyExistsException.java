package com.inferno.user_service.exception;

public class UserAlreadyExistsException extends ValidationException {
    public UserAlreadyExistsException(String message) {
        super(message, 409); // 409 Conflict
    }

    public UserAlreadyExistsException(String field, String value) {
        super(field + " already exists: " + value, 409);
    }
}