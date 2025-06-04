package com.task11.handler;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
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

            int tableNumber = requestBody.getInt("tableNumber");
            String clientName = requestBody.getString("clientName");
            String phoneNumber = requestBody.getString("phoneNumber");
            String date = requestBody.getString("date");
            String slotTimeStart = requestBody.getString("slotTimeStart");
            String slotTimeEnd = requestBody.getString("slotTimeEnd");

            if (!doesTableExist(tableNumber)) {
                throw new IllegalArgumentException("Table not found.");
            }

            if (hasConflictingReservation(tableNumber, date, slotTimeStart, slotTimeEnd)) {
                throw new IllegalArgumentException("Conflicting reservation exists for the given table and time slot.");
            }

            String reservationId = UUID.randomUUID().toString();

            saveEventToDynamoDB(new Item()
                    .withPrimaryKey("id", reservationId )
                    .withNumber("tableNumber", tableNumber)
                    .withString("clientName", clientName)
                    .withString("phoneNumber", phoneNumber)
                    .withString("date", date)
                    .withString("slotTimeStart", slotTimeStart)
                    .withString("slotTimeEnd", slotTimeEnd));

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

    private boolean doesTableExist(int tableNumber) {
        GetItemResult getItemResponse = dynamoDbClient.getItem(
                getTableName(),
                Map.of("number", new AttributeValue(String.valueOf(tableNumber)))
        );
        return getItemResponse.getItem() != null;
    }

private boolean hasConflictingReservation(int tableNumber, String date, String slotTimeStart, String slotTimeEnd) {
    try {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":tableNumber", new AttributeValue().withN(String.valueOf(tableNumber)));
        expressionAttributeValues.put(":date", new AttributeValue().withS(date));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#date", "date");

        QueryRequest queryRequest = new QueryRequest()
                .withTableName(getTableName())
                .withKeyConditionExpression("tableNumber = :tableNumber and #date = :date")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        QueryResult queryResult = dynamoDbClient.query(queryRequest);

        for (Map<String, AttributeValue> reservation : queryResult.getItems()) {
            String existingStart = reservation.get("slotTimeStart").getS();
            String existingEnd = reservation.get("slotTimeEnd").getS();

            if (timeSlotsOverlap(slotTimeStart, slotTimeEnd, existingStart, existingEnd)) {
                return true;
            }
        }

        return false;
    } catch (AmazonDynamoDBException e) {
        throw new RuntimeException("Failed to check for conflicting reservations", e);
    }
}

    private boolean timeSlotsOverlap(String start1, String end1, String start2, String end2) {
        LocalTime startTime1 = LocalTime.parse(start1, DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime1 = LocalTime.parse(end1, DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime startTime2 = LocalTime.parse(start2, DateTimeFormatter.ofPattern("HH:mm"));
        LocalTime endTime2 = LocalTime.parse(end2, DateTimeFormatter.ofPattern("HH:mm"));

        return !(endTime1.isBefore(startTime2) || startTime1.isAfter(endTime2));
    }
}