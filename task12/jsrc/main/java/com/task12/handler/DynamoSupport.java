package com.task12.handler;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

public abstract class DynamoSupport {
    private final AmazonDynamoDB dynamoDbClient;

    protected DynamoSupport(String region) {
        this.dynamoDbClient = initializeDynamoDBClient(System.getenv(region));
    }

    public AmazonDynamoDB getDynamoDbClient() {
        return dynamoDbClient;
    }

    private AmazonDynamoDB initializeDynamoDBClient(String region) {

        return AmazonDynamoDBClientBuilder.standard()
                .withRegion(region)
                .withClientConfiguration(new ClientConfiguration()
                        .withConnectionTimeout(2000)
                        .withRequestTimeout(5000))
                .build();
    }
}
