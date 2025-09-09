package com.inferno.user_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inferno.user_service.dto.UserResponse;
import com.inferno.user_service.model.User;
import com.inferno.user_service.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GetUserProfileLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final UserService userService = new UserService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        Map<String, String> headers = createHeaders();

        try {
            // Obtener UUID del path
            String uuid = request.getPathParameters().get("uuid");
            if (uuid == null || uuid.isBlank()) {
                return createErrorResponse(400, "User UUID is required in path", headers);
            }

            // Llamar al servicio
            List<User> users = userService.getUsersByUuid(uuid);

            if (users == null || users.isEmpty()) {
                return createErrorResponse(404, "User not found", headers);
            }

            // Mapear a DTO
            List<UserResponse> responses = users.stream()
                    .map(this::createUserResponse)
                    .collect(Collectors.toList());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(responses));

        } catch (Exception e) {
            context.getLogger().log("Get user error: " + e.getMessage());
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
        response.setImageUrl(user.getImageUrl());
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
