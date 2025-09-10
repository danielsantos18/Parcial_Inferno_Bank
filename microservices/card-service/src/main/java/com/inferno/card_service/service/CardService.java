package com.inferno.card_service.service;

import com.inferno.card_service.model.Card;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class CardService {

    private final DynamoDbTable<Card> cardTable;
    private final HttpClient httpClient;
    private static final Logger logger = Logger.getLogger(CardService.class.getName());
    private static final String USER_SERVICE_URL = System.getenv("USER_SERVICE_URL");

    public CardService() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.create();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        this.cardTable = enhancedClient.table("card-table", TableSchema.fromBean(Card.class));
        this.httpClient = HttpClient.newHttpClient();
    }

    public Card createCard(String userId, String requestType) {
        // 1. Primero verificar que el usuario existe
        if (!userExists(userId)) {
            throw new IllegalArgumentException("User does not exist: " + userId);
        }

        // 2. Crear la tarjeta
        Card card = new Card();
        card.setUuid(UUID.randomUUID().toString());
        card.setUserId(userId);
        card.setType(requestType.toUpperCase());
        card.setCreatedAt(Instant.now().toString());

        if ("DEBIT".equalsIgnoreCase(requestType)) {
            createDebitCard(card);
        } else if ("CREDIT".equalsIgnoreCase(requestType)) {
            createCreditCard(card);
        } else {
            throw new IllegalArgumentException("Invalid card type: " + requestType);
        }

        cardTable.putItem(card);
        logger.info("Card created successfully for user: " + userId);
        return card;
    }

    private boolean userExists(String userId) {
        try {
            String url = USER_SERVICE_URL + "/users/profile/" + userId;
            logger.info("Checking user existence: " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            logger.info("User service response: " + response.statusCode());

            // Si responde 200 OK, el usuario existe
            return response.statusCode() == 200;

        } catch (Exception e) {
            logger.severe("Error checking user existence: " + e.getMessage());
            throw new RuntimeException("Unable to verify user existence: " + e.getMessage());
        }
    }

    private void createDebitCard(Card card) {
        card.setStatus("ACTIVATED");
        card.setBalance(0L);
        card.setScore(0);
    }

    private void createCreditCard(Card card) {
        int score = ThreadLocalRandom.current().nextInt(0, 101);
        long limit = computeCreditLimit(score);

        card.setStatus("PENDING");
        card.setLimit(limit);
        card.setUsedBalance(0L);
        card.setScore(score);
        card.setBalance(0L);
    }

    private long computeCreditLimit(int score) {
        double amount = 100 + (score / 100.0) * (10000000 - 100);
        return Math.round(amount);
    }

    /**
     * Busca todas las tarjetas PENDING de un usuario - CORREGIDO
     */
    public List<Card> findPendingCardsByUser(String userId) {
        try {
            // Método más simple: scan y filtrar manualmente
            var items = cardTable.scan().items().iterator();
            List<Card> pendingCards = new ArrayList<>();

            while (items.hasNext()) {
                Card card = items.next();
                if (userId.equals(card.getUserId()) && "PENDING".equals(card.getStatus())) {
                    pendingCards.add(card);
                }
            }

            logger.info("Found " + pendingCards.size() + " pending cards for user: " + userId);
            return pendingCards;

        } catch (Exception e) {
            logger.severe("Error finding pending cards: " + e.getMessage());
            throw new RuntimeException("Error searching pending cards", e);
        }
    }

    /**
     * Activa una tarjeta (cambia status de PENDING a ACTIVATED)
     */
    public void activateCard(Card card) {
        try {
            card.setStatus("ACTIVATED");
            cardTable.updateItem(card);
            logger.info("Card activated: " + card.getUuid());

        } catch (Exception e) {
            logger.severe("Error activating card: " + card.getUuid() + " - " + e.getMessage());
            throw new RuntimeException("Error activating card", e);
        }
    }

    /**
     * Activa todas las tarjetas pendientes de un usuario
     */
    public int activateAllPendingCards(String userId) {
        List<Card> pendingCards = findPendingCardsByUser(userId);

        if (pendingCards.isEmpty()) {
            return 0;
        }

        int activatedCount = 0;
        for (Card card : pendingCards) {
            activateCard(card);
            activatedCount++;
        }

        logger.info("Activated " + activatedCount + " cards for user: " + userId);
        return activatedCount;
    }

    // === Métodos para transacciones y pagos ===

    public void purchase(String cardId) {
        logger.info("Purchase executed for card " + cardId);
        // TODO: lógica de compra (validar saldo, descontar, guardar transacción en
        // DynamoDB)
    }

    public void saveTransaction(String cardId) {
        logger.info("Transaction saved for card " + cardId);
        // TODO: lógica para guardar transacción en DynamoDB
    }

    public void payCard(String cardId) {
        logger.info("Card " + cardId + " marked as paid.");
        // TODO: lógica para registrar pago en DynamoDB
    }

    public String getCardReport(String cardId) {
        logger.info("Generating report for card " + cardId);
        return "Report for card " + cardId;
        // TODO: devolver JSON o reporte real con info de tarjeta y transacciones
    }

}