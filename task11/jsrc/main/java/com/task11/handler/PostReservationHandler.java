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

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

public class PostReservationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final String TABLE_NAME = "reservations_table";
    private static final String REGION = "REGION";
    private final AmazonDynamoDB dynamoDbClient;
    private final DynamoDB dynamoDB;

    public PostReservationHandler() {
        this.dynamoDbClient = initializeDynamoDBClient();
        this.dynamoDB = new DynamoDB(dynamoDbClient);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            JSONObject requestBody = new JSONObject(input.getBody());
            validateRequestBody(requestBody);

            String reservationId = UUID.randomUUID().toString();
            Item item = new Item()
                    .withPrimaryKey("id", reservationId )
                    .withNumber("tableNumber", requestBody.getInt("tableNumber"))
                    .withString("clientName", requestBody.getString("clientName"))
                    .withString("phoneNumber", requestBody.getString("phoneNumber"))
                    .withString("date", requestBody.getString("date"))
                    .withString("slotTimeStart", requestBody.getString("slotTimeStart"))
                    .withString("slotTimeEnd", requestBody.getString("slotTimeEnd"));

            saveEventToDynamoDB(item);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject().put("reservationId", reservationId).toString());
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
        if (!requestBody.has("tableNumber") || requestBody.getInt("tableNumber") <= 0) {
            throw new IllegalArgumentException("Valid tableNumber is required.");
        }
        if (!requestBody.has("clientName") || requestBody.getString("clientName").isEmpty()) {
            throw new IllegalArgumentException("clientName is required.");
        }
        if (!requestBody.has("phoneNumber") || requestBody.getString("phoneNumber").isEmpty()) {
            throw new IllegalArgumentException("phoneNumber is required.");
        }
        if (!requestBody.has("date") || !isValidDate(requestBody.getString("date"))) {
            throw new IllegalArgumentException("Valid date (yyyy-MM-dd) is required.");
        }
        if (!requestBody.has("slotTimeStart") || !isValidTime(requestBody.getString("slotTimeStart"))) {
            throw new IllegalArgumentException("Valid slotTimeStart (HH:mm) is required.");
        }
        if (!requestBody.has("slotTimeEnd") || !isValidTime(requestBody.getString("slotTimeEnd"))) {
            throw new IllegalArgumentException("Valid slotTimeEnd (HH:mm) is required.");
        }
    }

    private boolean isValidDate(String date) {
        try {
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidTime(String time) {
        try {
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
