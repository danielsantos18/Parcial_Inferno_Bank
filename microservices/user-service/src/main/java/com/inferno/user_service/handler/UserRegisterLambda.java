package com.inferno.user_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inferno.user_service.dto.UserResponse;
import com.inferno.user_service.exception.ValidationException;
import com.inferno.user_service.model.User;
import com.inferno.user_service.service.UserService;

import java.util.HashMap;
import java.util.Map;

import com.inferno.user_service.dto.UserRegistrationRequest;
import com.inferno.user_service.service.ValidationService;

public class UserRegisterLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserService userService = new UserService();
    private final ValidationService validationService = new ValidationService(userService);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        Map<String, String> headers = createHeaders();

        try {
            context.getLogger().log("Request body: " + request.getBody());

            // Parsear y validar el request
            UserRegistrationRequest registrationRequest = objectMapper.readValue(
                    request.getBody(), UserRegistrationRequest.class);

            validationService.validate(registrationRequest);
            validationService.validateBusinessRules(
                    registrationRequest.getEmail(),
                    registrationRequest.getDocument()
            );

            // Crear y guardar usuario
            User user = createUserFromRequest(registrationRequest);
            User createdUser = userService.createUser(user);

            context.getLogger().log("User created successfully: " + createdUser.getUuid());

            // Crear respuesta solo con el DTO
            UserResponse userResponse = createUserResponse(createdUser);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(userResponse));

        } catch (ValidationException e) {
            context.getLogger().log("Validation error: " + e.getMessage());
            return createErrorResponse(e.getStatusCode(), e.getMessage(), headers);

        } catch (Exception e) {
            context.getLogger().log("Unexpected error: " + e.getMessage());
            return createErrorResponse(500, "Internal server error", headers);
        }
    }

    private User createUserFromRequest(UserRegistrationRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setDocumentNumber(request.getDocument());
        return user;
    }

    private UserResponse createUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUuid(user.getUuid());
        response.setName(user.getName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setDocument(user.getDocumentNumber());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    private Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        return headers;
    }

    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message, Map<String, String> headers) {
        try {
            Map<String, String> errorBody = new HashMap<>();
            errorBody.put("error", message);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(errorBody));

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers)
                    .withBody("{\"error\": \"" + message + "\"}");
        }
    }
}