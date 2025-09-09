package com.inferno.user_service.service;

import com.inferno.user_service.exception.UserAlreadyExistsException;
import jakarta.validation.*;

import java.util.Set;
import java.util.stream.Collectors;

public class ValidationService {

    private final Validator validator;
    private final UserService userService;

    public ValidationService(UserService userService) {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
        this.userService = userService;
    }

    public <T> void validate(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);

        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                    .map(violation -> violation.getMessage())
                    .collect(Collectors.joining(", "));

            throw new jakarta.validation.ValidationException(errorMessage);
        }
    }

    public void validateBusinessRules(String email, String documentNumber) {
        if (userService.isEmailExists(email)) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        if (userService.isDocumentNumberExists(documentNumber)) {
            throw new UserAlreadyExistsException("Document number already exists");
        }
    }
}