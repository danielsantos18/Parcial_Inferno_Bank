package com.example.notification_service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Paths;

@Service
public class NotificationService {

    private final S3Client s3Client;
    private final JavaMailSender mailSender;


    @Autowired
    public NotificationService(JavaMailSender mailSender) {
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_2) // 
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.mailSender = mailSender;
    }

    //  Constructor alterno para pruebas (sin contexto de Spring)
    public NotificationService() {
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_2)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.mailSender = null; // no hay envío real de correos en pruebas simples
    }

    // --- Métodos S3 ---
    public void uploadTemplateToS3(String bucketName, String key, String content) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType("text/html")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromString(content));
            System.out.println("✅ Plantilla subida correctamente a S3: " + key);

        } catch (S3Exception e) {
            System.err.println(" Error al subir a S3: " + e.awsErrorDetails().errorMessage());
        } catch (SdkClientException e) {
            System.err.println(" Error de cliente AWS: " + e.getMessage());
        }
    }

    public void downloadFileFromS3(String bucketName, String key, String downloadTo) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.getObject(getObjectRequest, Paths.get(downloadTo));
            System.out.println("✅ Archivo descargado desde S3: " + downloadTo);

        } catch (S3Exception e) {
            System.err.println(" Error al acceder a S3: " + e.awsErrorDetails().errorMessage());
        } catch (SdkClientException e) {
            System.err.println(" Error de cliente AWS: " + e.getMessage());
        }
    }

    // --- Método real para enviar correos ---
    public void sendEmail(String to, String subject, String body) {
        if (mailSender == null) {
            System.out.println(" mailSender es null (no se puede enviar correo en este contexto).");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("tu_correo@gmail.com"); //  debe coincidir con spring.mail.username
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            System.out.println(" Correo enviado a " + to);

        } catch (Exception e) {
            System.err.println(" Error al enviar correo: " + e.getMessage());
        }
    }
}
