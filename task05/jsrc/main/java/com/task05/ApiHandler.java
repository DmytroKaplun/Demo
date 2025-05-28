package com.task05;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.task05.dto.EventRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "table_name", value = "${target_table}"),
		@EnvironmentVariable(key = "region", value = "${region}")}
)
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	private static final ObjectMapper objectMapper = new ObjectMapper();
	private static final String PRINCIPAL_ID = "principalId";
	private static final String CREATED_AT = "createdAt";
	public static final String ID = "id";
	public static final String BODY = "body";
	public static final String TABLE_NAME = "table_name";
	public static final String REGION = "region";
	public static final String EVENT = "event";
	public static final String STATUS_CODE = "statusCode";
	public static final String ERROR = "error";
	private final AmazonDynamoDB dynamoDBClient;
	private final DynamoDB dynamoDB;

	public ApiHandler() {
		this.dynamoDBClient = initializeDynamoDBClient();
		this.dynamoDB = new DynamoDB(dynamoDBClient);
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


	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		try {
			EventRequest eventRequest = objectMapper.convertValue(input, EventRequest.class);
			Map<String, Object> content = eventRequest.getContent();
			int principalId = eventRequest.getPrincipalId();

			String id = UUID.randomUUID().toString();
			String createdAt = Instant.now().toString();

			Map<String, Object> event = new HashMap<>();
			event.put(ID, id);
			event.put(PRINCIPAL_ID, principalId);
			event.put(CREATED_AT, createdAt);
			event.put(BODY, content);

			saveEventToDynamoDB(event, content);

			Map<String, Object> response = new HashMap<>();
			response.put(STATUS_CODE, 201);
			response.put(EVENT, event);
			return response;

		} catch (IllegalStateException e) {
			context.getLogger().log("Configuration Error: " + e.getMessage());
			return createErrorResponse(500, "Service Configuration Error");
		} catch (IllegalArgumentException e) {
			context.getLogger().log("Validation Error: " + e.getMessage());
			return createErrorResponse(400, e.getMessage());
		} catch (AmazonDynamoDBException e) {
			context.getLogger().log("DynamoDB Error: " + e.getMessage());
			return createErrorResponse(503, "Database Service Unavailable");
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			return createErrorResponse(500, "Internal Server Error");
		}

	}

	private void saveEventToDynamoDB(Map<String, Object> event, Map<String, Object> content) {
		String tableName = Optional.ofNullable(System.getenv(TABLE_NAME))
				.orElseThrow(() -> new IllegalStateException("Missing table_name environment variable"));
		Table table = dynamoDB.getTable(tableName);

		table.putItem(new Item()
				.withPrimaryKey(ID, event.get(ID))
				.withNumber(PRINCIPAL_ID, (Integer) event.get(PRINCIPAL_ID))
				.withString(CREATED_AT, (String) event.get(CREATED_AT))
				.withMap(BODY, content));
	}

	private Map<String, Object> createErrorResponse(int statusCode, String message) {
		Map<String, Object> errorResponse = new HashMap<>();
		errorResponse.put(STATUS_CODE, statusCode);
		errorResponse.put(ERROR, message);
		return errorResponse;
	}
}