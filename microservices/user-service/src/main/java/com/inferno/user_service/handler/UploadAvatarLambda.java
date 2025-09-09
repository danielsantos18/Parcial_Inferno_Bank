package com.inferno.user_service.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.inferno.user_service.model.User;
import com.inferno.user_service.service.UserService;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Base64;

public class UploadAvatarLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final S3Client s3 = S3Client.builder()
            .region(Region.US_EAST_2)
            .build();

    private final S3Presigner presigner = S3Presigner.builder()
            .region(Region.US_EAST_2)
            .build();

    private final UserService userService = new UserService();

    private final String bucket = System.getenv("infernoavatarimagebucket");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // 1. Obtener el UUID desde path param
            String uuid = request.getPathParameters().get("uuid");

            // 2. Buscar usuario en DynamoDB
            User user = userService.getUsersByUuid(uuid).stream().findFirst().orElse(null);
            if (user == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withBody("{\"error\": \"Usuario no encontrado\"}");
            }

            // 3. Decodificar imagen Base64
            byte[] imageBytes = Base64.getDecoder().decode(request.getBody());

            // 4. Definir key en S3 -> {uuid}/{username}.png
            String key = uuid + "/" + user.getName() + ".png";

            // 5. Subir la imagen al bucket
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("image/png")
                    .build();

            s3.putObject(putRequest, RequestBody.fromBytes(imageBytes));

            // 6. Crear un pre-signed URL (válido 10 min)
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(getObjectRequest)
                    .build();

            String presignedUrl = presigner.presignGetObject(presignRequest).url().toString();

            // 7. Actualizar usuario con la nueva URL
            user.setImageUrl(presignedUrl);
            userService.updateUser(user);

            // 8. Respuesta
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"avatarUrl\": \"" + presignedUrl + "\"}");
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
