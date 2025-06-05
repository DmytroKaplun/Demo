package com.task11.handler;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PostReservationHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostReservationHandler.class);
    private static final String RESERVATIONS_TABLE = "reservations_table";
    private static final String TABLES_NAME = "tables_table";
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
                    .withPrimaryKey("id",reservationId)
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
            context.getLogger().log("Error: " + e.getMessage());
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            String stackTrace = stringWriter.toString();

            LOGGER.error("Error occurred: {}", e.getMessage(), e);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"error\": \"Internal Server Error\", \"message\": \"" + e.getMessage() + "\", \"stackTrace\": \"" + stackTrace.replace("\n", "\\n") + "\"}");
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
        String tableName = getReservationsName();
        Table auditTable = dynamoDB.getTable(tableName);

        auditTable.putItem(auditEntry);
    }

    private static String getTablesName() {
        return Optional.ofNullable(System.getenv(TABLES_NAME))
                .orElseThrow(() -> new IllegalStateException("Missing region environment variable"));
    }

    private static String getReservationsName() {
        return Optional.ofNullable(System.getenv(RESERVATIONS_TABLE))
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
        try {
            LOGGER.debug("Preparing ScanRequest with FilterExpression 'number = :tableNumber'");

            Map<String, String> expressionAttributeNames = Map.of(
                    "#number", "number" // Escape the reserved keyword `number`
            );

            // Define Expression Attribute Values
            Map<String, AttributeValue> expressionAttributeValues = Map.of(
                    ":tableNumber", new AttributeValue().withN(String.valueOf(tableNumber))
            );

            ScanRequest scanRequest = new ScanRequest()
                    .withTableName(getTablesName())
                    .withFilterExpression("#number = :tableNumber")
                    .withExpressionAttributeNames(expressionAttributeNames)
                    .withExpressionAttributeValues(expressionAttributeValues);
            LOGGER.debug("Executing ScanRequest: {}", scanRequest);

            ScanResult scanResult = dynamoDbClient.scan(scanRequest);

            LOGGER.debug("ScanResult: {}", scanResult.getItems());
            boolean exists = !scanResult.getItems().isEmpty();
            LOGGER.info("Table exists for tableNumber {}: {}", tableNumber, exists);

            return !scanResult.getItems().isEmpty();
        } catch (AmazonDynamoDBException e) {
            LOGGER.error("Failed to check if the table exists for tableNumber: {}", tableNumber, e);
            throw new RuntimeException("Failed to check if the table exists", e.getCause() != null ? e.getCause() : e);
        }
    }

private boolean hasConflictingReservation(int tableNumber, String date, String slotTimeStart, String slotTimeEnd) {
    try {
        Map<String, String> expressionAttributeNames = Map.of(
                "#tableNumber", "tableNumber", // Escape the reserved keyword `tableNumber`
                "#reservationDate", "reservationDate" // Escape the reserved keyword `reservationDate`
        );
        // Define Expression Attribute Values
        Map<String, AttributeValue> expressionAttributeValues = Map.of(
                ":tableNumber", new AttributeValue().withN(String.valueOf(tableNumber)),
                ":date", new AttributeValue().withS(date)
        );
        // Use a Scan operation
        ScanRequest scanRequest = new ScanRequest()
                .withTableName(getTablesName())
                .withFilterExpression("#tableNumber = :tableNumber and #reservationDate = :date")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

        ScanResult scanResult = dynamoDbClient.scan(scanRequest);

        for (Map<String, AttributeValue> reservation : scanResult.getItems()) {
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
        try {
            LocalTime startTime1 = LocalTime.parse(start1, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime endTime1 = LocalTime.parse(end1, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime startTime2 = LocalTime.parse(start2, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime endTime2 = LocalTime.parse(end2, DateTimeFormatter.ofPattern("HH:mm"));
            return !(endTime1.isBefore(startTime2) || startTime1.isAfter(endTime2));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time format provided", e);
        }
    }
}