package com.inferno.notification_service;

import org.springframework.stereotype.Service;

@Service
public class NotificationHandler {

    public void sendNotification(String message) {
        // Simulacion de envio de notificacion
        System.out.println("Notificacion enviada: " + message);
    }
}
