package com.inferno.card_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CreateCardLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger logger = Logger.getLogger(CreateCardLambda.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SqsClient sqsClient = SqsClient.create();
    private static final String SQS_QUEUE_URL = System.getenv("SQS_QUEUE_URL");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            // 1. Parsear el cuerpo de la solicitud
            String body = event.getBody();
            logger.info("Received request: " + body);

            JsonNode requestJson = objectMapper.readTree(body);
            String userId = requestJson.get("userId").asText();
            String requestType = requestJson.get("request").asText().toUpperCase();

            // 2. Validar el tipo de solicitud
            if (!"CREDIT".equals(requestType) && !"DEBIT".equals(requestType)) {
                return createResponse(400, "Invalid request type. Must be CREDIT or DEBIT");
            }

            // 3. Crear mensaje para SQS
            Map<String, String> sqsMessage = new HashMap<>();
            sqsMessage.put("userId", userId);
            sqsMessage.put("request", requestType);

            String messageBody = objectMapper.writeValueAsString(sqsMessage);

            // 4. Enviar a SQS
            SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(SQS_QUEUE_URL)
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(sendMessageRequest);
            logger.info("Message sent to SQS: " + messageBody);

            return createResponse(200, "Card request sent for processing");

        } catch (Exception e) {
            logger.warning("Error processing request: " + e.getMessage());
            return createResponse(500, "Internal server error");
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, String message) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withBody("{\"message\": \"" + message + "\"}")
                .withHeaders(Map.of("Content-Type", "application/json"));
    }
}