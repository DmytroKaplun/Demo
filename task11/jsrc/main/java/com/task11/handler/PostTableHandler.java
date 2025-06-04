package com.task11.handler;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import java.util.Optional;
import java.util.UUID;

public class PostTableHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final String TABLE_NAME = "tables_table";
    private static final String REGION = "REGION";
    private final AmazonDynamoDB dynamoDbClient;
    private final DynamoDB dynamoDB;

    public PostTableHandler() {
        this.dynamoDbClient = initializeDynamoDBClient();
        this.dynamoDB = new DynamoDB(dynamoDbClient);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            JSONObject requestBody = new JSONObject(input.getBody());
            validateRequestBody(requestBody);


            Item item = new Item()
                .withPrimaryKey("id", String.valueOf(requestBody.getInt("id")))
                .withNumber("number", requestBody.getInt("number"))
                .withNumber("places", requestBody.getInt("places"))
                .withBoolean("isVip", requestBody.getBoolean("isVip"));
            if (requestBody.has("minOrder")) {
                item.withNumber("minOrder", requestBody.getInt("minOrder"));
            }

            saveEventToDynamoDB(item);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject().put("id", requestBody.getInt("id")).toString());
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("error", e.getMessage()).toString());
        }
    }

    private AmazonDynamoDB initializeDynamoDBClient() {
        String region = Optional.ofNullable(System.getenv(REGION))
                .orElseThrow(() -> new IllegalStateException("Missing region environment variable"));

        return AmazonDynamoDBClientBuilder.standard()
                .withRegion(region)
                .withClientConfiguration(new ClientConfiguration()
                        .withConnectionTimeout(2000)
                        .withRequestTimeout(5000))
                .build();
    }

    private void saveEventToDynamoDB (Item auditEntry){
        String tableName = getTableName();
        Table auditTable = dynamoDB.getTable(tableName);

        auditTable.putItem(auditEntry);
    }

    private static String getTableName() {
        return Optional.ofNullable(System.getenv(TABLE_NAME))
                .orElseThrow(() -> new IllegalStateException("Missing region environment variable"));
    }

    private void validateRequestBody(JSONObject requestBody) {
        String[] requiredFields = {"number", "places", "isVip"};
        for (String field : requiredFields) {
            if (!requestBody.has(field)) {
                throw new IllegalArgumentException("Missing required field: " + field);
            }
        }
    }
}