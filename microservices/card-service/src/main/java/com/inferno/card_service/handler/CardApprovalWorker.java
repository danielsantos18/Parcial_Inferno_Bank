package com.inferno.card_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inferno.card_service.service.CardService;

import java.util.logging.Logger;

public class CardApprovalWorker implements RequestHandler<SQSEvent, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(CardApprovalWorker.class.getName());
    private final CardService cardService;

    public CardApprovalWorker() {
        this.cardService = new CardService();
    }

    @Override
    public String handleRequest(SQSEvent event, Context context) {
        logger.info("Processing " + event.getRecords().size() + " SQS messages");

        for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
            try {
                // 1. Parsear mensaje SQS
                String messageBody = sqsMessage.getBody();
                logger.info("Raw SQS message: " + messageBody);

                JsonNode messageJson = objectMapper.readTree(messageBody);

                String userId = messageJson.get("userId").asText();
                String requestType = messageJson.get("request").asText().toUpperCase();

                logger.info("Processing " + requestType + " card for user: " + userId);

                // 2. Usar el servicio para crear la tarjeta
                cardService.createCard(userId, requestType);

                logger.info("Successfully processed " + requestType + " card for user: " + userId);

            } catch (Exception e) {
                logger.warning("Error processing SQS message: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return "Processed " + event.getRecords().size() + " messages";
    }
}