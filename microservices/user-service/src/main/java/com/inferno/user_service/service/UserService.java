package com.inferno.user_service.service;

import com.inferno.user_service.model.User;
import com.inferno.user_service.security.PasswordService;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public class UserService {
    private final DynamoDbTable<User> userTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final PasswordService passwordService = new PasswordService();

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern DOCUMENT_PATTERN = Pattern.compile("^\\d+$");

    public UserService() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.create();
        this.enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        this.userTable = enhancedClient.table("user-table", TableSchema.fromBean(User.class));
    }

    public User createUser(User user) {
        user.setUuid(UUID.randomUUID().toString());
        user.setPassword(passwordService.encryptPassword(user.getPassword()));
        user.setCreatedAt(Instant.now().toString());
        user.setUpdatedAt(Instant.now().toString());

        userTable.putItem(user);
        return user;
    }

    public User authenticateUser(String email, String plainPassword) {
        User user = getUserByEmail(email);
        if (user != null && passwordService.verifyPassword(plainPassword, user.getPassword())) {
            return user;
        }
        return null;
    }

    public boolean isEmailExists(String email) {
        try {
            // Usar el índice secundario EmailIndex
            return enhancedClient.table("user-table", TableSchema.fromBean(User.class))
                    .index("EmailIndex")
                    .query(r -> r.queryConditional(
                            QueryConditional.keyEqualTo(Key.builder().partitionValue(email).build())
                    ))
                    .stream().flatMap(page -> page.items().stream())
                    .iterator()
                    .hasNext();

        } catch (Exception e) {
            System.err.println("Error checking email existence: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean isDocumentNumberExists(String documentNumber) {
        try {
            // Usar el índice secundario DocumentNumberIndex
            return enhancedClient.table("user-table", TableSchema.fromBean(User.class))
                    .index("DocumentNumberIndex")
                    .query(r -> r.queryConditional(
                            QueryConditional.keyEqualTo(Key.builder().partitionValue(documentNumber).build())
                    ))
                    .stream().flatMap(page -> page.items().stream())
                    .iterator()
                    .hasNext();

        } catch (Exception e) {
            System.err.println("Error checking document existence: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public User getUserByEmail(String email) {
        try {
            // CORREGIDO: Usar el índice EmailIndex para buscar por email
            return enhancedClient.table("user-table", TableSchema.fromBean(User.class))
                    .index("EmailIndex")
                    .query(QueryEnhancedRequest.builder()
                            .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                                    .partitionValue(email)
                                    .build()))
                            .build())
                    .stream().flatMap(page -> page.items().stream())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            System.err.println("Error getting user by email: " + e.getMessage());
            return null;
        }
    }

    public User getUserByDocumentNumber(String documentNumber) {
        try {
            return enhancedClient.table("user-table", TableSchema.fromBean(User.class))
                    .index("DocumentNumberIndex")
                    .query(QueryEnhancedRequest.builder()
                            .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                                    .partitionValue(documentNumber)
                                    .build()))
                            .build())
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            System.err.println("Error getting user by document: " + e.getMessage());
            return null;
        }
    }

    // Validar formato de email
    public boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    // Validar formato de documentNumber
    public boolean isValidDocumentNumber(String documentNumber) {
        return documentNumber != null && DOCUMENT_PATTERN.matcher(documentNumber).matches();
    }

    // Validar fortaleza de password (mínimo 8 caracteres)
    public boolean isValidPassword(String password) {
        return password != null && password.length() >= 8;
    }

    //DynamoDb exige la PK y SK
    public User getUserById(String uuid, String documentNumber) {
        Key key = Key.builder()
                .partitionValue(uuid)
                .sortValue(documentNumber)
                .build();
        return userTable.getItem(key);
    }

    public List<User> getUsersByUuid(String uuid) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(uuid).build()
        );

        return userTable.query(r -> r.queryConditional(queryConditional))
                .items()
                .stream()
                .toList();
    }

    public User updateUser(User user) {
        user.setUpdatedAt(Instant.now().toString());
        userTable.updateItem(user);
        return user;
    }

    public void deleteUser(String uuid, String email) {
        Key key = Key.builder()
                .partitionValue(uuid)
                .sortValue(email)
                .build();
        userTable.deleteItem(key);
    }
}