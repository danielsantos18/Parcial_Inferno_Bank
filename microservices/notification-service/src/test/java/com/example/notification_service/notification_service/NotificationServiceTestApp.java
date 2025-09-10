package com.example.notification_service.notification_service;

import com.example.notification_service.service.NotificationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication(scanBasePackages = "com.example.notification_service")
public class NotificationServiceTestApp {
    public static void main(String[] args) {
        // 🚀 Arrancar el contexto de Spring para que inyecte JavaMailSender y demás beans
        ApplicationContext context = SpringApplication.run(NotificationServiceTestApp.class, args);

        // Obtener el bean NotificationService de Spring
        NotificationService service = context.getBean(NotificationService.class);

        // 👇 Cambia estos valores por los de tu cuenta AWS
        String bucket = "inferno-bank-notifications";  // Reemplázalo con tu bucket real
        String key = "plantillas/notificacion.html";   // Ruta dentro del bucket
        String fileLocal = "C:\\Users\\Luis Miguel Miranda\\Desktop\\notificacion_descargada.html";

        // Contenido de prueba que se subirá al S3
        String contenido = """
            <html>
              <body>
                <h1>🔥 Notificación InfernoBank</h1>
                <p>Esto es una prueba de integración con AWS S3 🚀</p>
              </body>
            </html>
        """;

        try {
            // 1️⃣ Subir archivo de prueba a S3
            System.out.println("📤 Subiendo plantilla al bucket...");
            service.uploadTemplateToS3(bucket, key, contenido);

            // 2️⃣ Descargar el archivo desde S3 a tu PC
            System.out.println("📥 Descargando plantilla desde el bucket...");
            service.downloadFileFromS3(bucket, key, fileLocal);

            // 3️⃣ Enviar correo real (si configuraste spring.mail en application.properties)
            System.out.println("📧 Enviando correo de prueba...");
            service.sendEmail(
                    "destinatario@gmail.com",         // ⚠️ Cambia por un correo válido
                    "Prueba de notificación ✅",
                    "Hola 👋, este es un correo de prueba enviado desde InfernoBank 🚀"
            );

            System.out.println("🎉 Todo salió bien, revisa tu bucket, el archivo local y tu correo.");
        } catch (Exception e) {
            System.err.println("❌ Error ejecutando la prueba: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
