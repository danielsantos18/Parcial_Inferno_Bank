package com.inferno.card_service.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@DynamoDbBean
public class Card {
    private String uuid;
    private String userId;
    private String type;        // CREDIT or DEBIT
    private String status;      // PENDING or ACTIVATED
    private Long balance;       // solo DEBIT
    private Long limit;         // solo CREDIT
    private Long usedBalance;   // solo CREDIT
    private Integer score;      // solo CREDIT
    private String createdAt;


    // Partition key
    @DynamoDbPartitionKey
    public String getUuid() {
        return uuid;
    }

    @DynamoDbSortKey
    @DynamoDbSecondaryPartitionKey(indexNames = "createdAt")
    public String getCreatedAt() {
        return createdAt;
    }
}

