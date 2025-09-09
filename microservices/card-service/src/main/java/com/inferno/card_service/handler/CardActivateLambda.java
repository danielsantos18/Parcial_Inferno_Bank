package com.inferno.card_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inferno.card_service.service.CardService;

import java.util.Map;
import java.util.logging.Logger;

public class CardActivateLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger logger = Logger.getLogger(CardActivateLambda.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final CardService cardActivateService;

    public CardActivateLambda() {
        this.cardActivateService = new CardService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            // 1. Validar y parsear request
            if (event.getBody() == null) {
                return createResponse(400, "Request body is required");
            }

            JsonNode requestJson = objectMapper.readTree(event.getBody());
            logger.info("Received activation request for user: " + requestJson.toString());

            if (!requestJson.has("userId")) {
                return createResponse(400, "Missing required field: userId");
            }

            String userId = requestJson.get("userId").asText();

            // 2. Ejecutar lógica de negocio a través del servicio
            int activatedCards = cardActivateService.activateAllPendingCards(userId);

            if (activatedCards == 0) {
                return createResponse(404, "No pending cards found for user: " + userId);
            }

            // 3. Retornar respuesta exitosa
            return createResponse(200, "Successfully activated " + activatedCards + " cards for user: " + userId);

        } catch (Exception e) {
            logger.severe("Error in card activation: " + e.getMessage());
            return createResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String message) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withBody("{\"message\": \"" + message + "\"}")
                .withHeaders(Map.of("Content-Type", "application/json"));
    }
}