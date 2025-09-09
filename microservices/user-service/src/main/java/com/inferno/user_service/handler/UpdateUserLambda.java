package com.inferno.user_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inferno.user_service.dto.UserResponse;
import com.inferno.user_service.dto.UserUpdateRequest;
import com.inferno.user_service.exception.ValidationException;
import com.inferno.user_service.model.User;
import com.inferno.user_service.service.UserService;
import com.inferno.user_service.service.ValidationService;

import java.util.HashMap;
import java.util.Map;

public class UpdateUserLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserService userService = new UserService();
    private final ValidationService validationService = new ValidationService(userService);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        Map<String, String> headers = createHeaders();

        try {
            context.getLogger().log("Update request body: " + request.getBody());

            // Obtener UUID del path parameters
            String uuid = request.getPathParameters().get("uuid");
            if (uuid == null) {
                return createErrorResponse(400, "User UUID is required in path", headers);
            }

            UserUpdateRequest updateRequest = objectMapper.readValue(
                    request.getBody(), UserUpdateRequest.class);

            validationService.validate(updateRequest);

            // Buscar usuario existente
            User existingUser = userService.getUserById(uuid, updateRequest.getDocument());
            if (existingUser == null) {
                return createErrorResponse(404, "User not found", headers);
            }

            // Actualizar campos permitidos
            if (updateRequest.getName() != null) {
                existingUser.setName(updateRequest.getName());
            }
            if (updateRequest.getLastName() != null) {
                existingUser.setLastName(updateRequest.getLastName());
            }

            User updatedUser = userService.updateUser(existingUser);
            UserResponse userResponse = createUserResponse(updatedUser);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(204)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(userResponse));

        } catch (ValidationException e) {
            return createErrorResponse(e.getStatusCode(), e.getMessage(), headers);
        } catch (Exception e) {
            context.getLogger().log("Update error: " + e.getMessage());
            return createErrorResponse(500, "Internal server error", headers);
        }
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