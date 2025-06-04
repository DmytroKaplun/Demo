package com.task11.handler;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;

import java.util.Map;
import java.util.Optional;

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
            Map<String, AttributeValue> item = itemResult.getItem();

            JSONObject itemJsonObject = new JSONObject()
                    .put("id", item.get("id").getS())
                    .put("number", Integer.parseInt(item.get("number").getN()))
                    .put("places", Integer.parseInt(item.get("places").getN()))
                    .put("isVip", item.get("isVip").getBOOL());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(itemJsonObject.toString());
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

    private static String getTableName() {
        return Optional.ofNullable(System.getenv(TABLE_NAME))
                .orElseThrow(() -> new IllegalStateException("Missing region environment variable"));
    }
}
