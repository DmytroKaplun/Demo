package com.task11.handler;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GetTablesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB dynamoDbClient;
    private static final String TABLE_NAME = "tables_table";
    private static final String REGION = "REGION";

    public GetTablesHandler() {
        this.dynamoDbClient = initializeDynamoDBClient();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            ScanResult scanResponse = dynamoDbClient.scan(new ScanRequest()
                    .withTableName(getTableName())
                    .withLimit(100));

            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, Object>> tablesList = new ArrayList<>();

            scanResponse.getItems().forEach(item -> {
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("id", Integer.parseInt(item.get("id").getS())); // Convert `id` to Integer
                table.put("number", Integer.parseInt(item.get("number").getN())); // Convert `number` to Integer
                table.put("places", Integer.parseInt(item.get("places").getN())); // Convert `places` to Integer
                table.put("isVip", item.get("isVip").getBOOL()); // Boolean field
                table.put("minOrder", item.containsKey("minOrder") ? Integer.parseInt(item.get("minOrder").getN()) : null); // Optional field
                tablesList.add(table);
            });

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("tables", tablesList);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
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

    private static String getTableName() {
        return Optional.ofNullable(System.getenv(TABLE_NAME))
                .orElseThrow(() -> new IllegalStateException("Missing region environment variable"));
    }
}
