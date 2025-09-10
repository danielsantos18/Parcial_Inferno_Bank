package com.inferno.card_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.inferno.card_service.service.CardService;

public class CardPaidCreditCardLambda implements RequestHandler<String, String> {

    private final CardService cardService = new CardService();

    @Override
    public String handleRequest(String cardId, Context context) {
        cardService.payCard(cardId);
        return "Card " + cardId + " paid successfully!";
    }
}
