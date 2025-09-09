package com.inferno.user_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class User {

    private String uuid;
    private String name;
    private String lastName;
    private String email;
    private String password;
    private String documentNumber;
    private String imageUrl;
    private String createdAt;
    private String updatedAt;

    // Partition key
    @DynamoDbPartitionKey
    public String getUuid() {
        return uuid;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "EmailIndex")
    public String getEmail() {
        return email;
    }

    @DynamoDbSortKey
    @DynamoDbSecondaryPartitionKey(indexNames = "DocumentNumberIndex")
    public String getDocumentNumber() {
        return documentNumber;
    }
}