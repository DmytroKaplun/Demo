package com.task12.handler;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.task12.ApiHandler.createResponse;

public class GetTableByIdHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB dynamoDbClient;
    private static final String TABLE_NAME = "tables_table";
    private static final String REGION = "REGION";

    public GetTableByIdHandler() {
        this.dynamoDbClient = initializeDynamoDBClient();
    }


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, String> pathParameters = input.getPathParameters();
            if (pathParameters == null || !pathParameters.containsKey("tableId")) {
                throw new IllegalArgumentException("tableId is required in the path.");
            }
            String tableId = pathParameters.get("tableId");

            GetItemResult itemResult = dynamoDbClient.getItem(
                    getTableName(),
                    Map.of("id", new AttributeValue(tableId))
            );
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, AttributeValue> item = itemResult.getItem();

            Map<String, Object> table = new LinkedHashMap<>();
            table.put("id", Integer.parseInt(item.get("id").getS())); // Convert `id` to Integer
            table.put("number", Integer.parseInt(item.get("number").getN())); // Convert `number` to Integer
            table.put("places", Integer.parseInt(item.get("places").getN())); // Convert `places` to Integer
            table.put("isVip", item.get("isVip").getBOOL()); // Boolean
            if (item.containsKey("minOrder")) {
                table.put("minOrder", Integer.parseInt(item.get("minOrder").getN()));
            }
            String jsonResponse = objectMapper.writeValueAsString(table);
            return createResponse(200, jsonResponse);
        } catch (Exception e) {
            JSONObject errorResponse = new JSONObject().put("error", e.getMessage());
            return createResponse(400, errorResponse.toString());
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

    private static String getTableName() {
        return Optional.ofNullable(System.getenv(TABLE_NAME))
                .orElseThrow(() -> new IllegalStateException("Missing region environment variable"));
    }
}
