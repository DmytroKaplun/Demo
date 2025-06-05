package com.task12.handler;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.task12.ApiHandler.createResponse;

public class GetReservationsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final AmazonDynamoDB dynamoDbClient;
    private static final String TABLE_NAME = "reservations_table";
    private static final String REGION = "REGION";

    public GetReservationsHandler() {
        this.dynamoDbClient = initializeDynamoDBClient();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            ScanResult scanResponse = dynamoDbClient.scan(new ScanRequest()
                    .withTableName(getTableName())
                    .withLimit(100));

            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, Object>> tablesList = new ArrayList<>();

            scanResponse.getItems().forEach(item -> {
                Map<String, Object> table = new LinkedHashMap<>();
                table.put("tableNumber", Integer.parseInt(item.get("tableNumber").getN())); // Convert to Integer
                table.put("clientName", item.get("clientName").getS());
                table.put("phoneNumber", item.get("phoneNumber").getS());
                table.put("date", item.get("date").getS());
                table.put("slotTimeStart", item.get("slotTimeStart").getS());
                table.put("slotTimeEnd", item.get("slotTimeEnd").getS());
                tablesList.add(table);
            });
            tablesList.sort(Comparator.comparingInt(o -> (Integer) o.get("tableNumber")));

            Map<String, Object> response = new HashMap<>();
            response.put("reservations", tablesList);

            return createResponse(200, response.toString());
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