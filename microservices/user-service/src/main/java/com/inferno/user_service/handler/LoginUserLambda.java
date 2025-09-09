package com.inferno.user_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inferno.user_service.dto.LoginRequest;
import com.inferno.user_service.dto.LoginResponse;
import com.inferno.user_service.model.User;
import com.inferno.user_service.service.UserService;
import com.inferno.user_service.service.ValidationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

public class LoginUserLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserService userService = new UserService();
    private final ValidationService validationService = new ValidationService(userService);

    // Nombre del secreto en Secrets Manager (definido en Terraform)
    private static final String SECRET_NAME = "jwtSecret";

    private String getJwtSecret() {
        try (SecretsManagerClient client = SecretsManagerClient.create()) {
            GetSecretValueResponse response = client.getSecretValue(
                    GetSecretValueRequest.builder()
                            .secretId(SECRET_NAME)
                            .build()
            );
            // el secreto lo guardamos como JSON { "JWT_SECRET": "valor" }
            Map<String, String> secretMap = objectMapper.readValue(response.secretString(), Map.class);
            return secretMap.get("JWT_SECRET");
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo JWT_SECRET desde Secrets Manager", e);
        }
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {

            context.getLogger().log("Request body: " + request.getBody());

            // 1. Parsear y validar request
            LoginRequest loginRequest = objectMapper.readValue(request.getBody(), LoginRequest.class);
            validationService.validate(loginRequest);


            // 2. Autenticar usuario contra DynamoDB
            User user = userService.authenticateUser(loginRequest.getEmail(), loginRequest.getPassword());
            if (user == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("{\"error\":\"Credenciales inválidas\"}");
            }

            //Obtener secreto desde AWS Secrets Manager
            String jwtSecret = getJwtSecret();

            // 3. Generar token JWT
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            String token = Jwts.builder()
                    .setSubject(user.getUuid()) // usamos uuid como subject
                    .claim("email", user.getEmail())
                    .claim("role", "USER")
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 3600 * 1000)) // 1 hora
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();

            // 4. Crear response DTO
            LoginResponse response = new LoginResponse(token);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
