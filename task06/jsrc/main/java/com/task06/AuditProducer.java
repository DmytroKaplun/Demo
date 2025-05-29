package com.task06;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "table_name", value = "${target_table}"),
		@EnvironmentVariable(key = "region", value = "${region}")}
)
@DynamoDbTriggerEventSource(
		targetTable = "Configuration",
		batchSize = 1
)
@DependsOn(
		name = "Configuration",
		resourceType = ResourceType.DYNAMODB_TABLE
)
public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {
	public static final String ITEM_KEY = "itemKey";
	public static final String MODIFICATION_TIME = "modificationTime";
	public static final String ID = "id";
	public static final String KEY = "key";
	public static final String VALUE = "value";
	public static final String INSERT = "INSERT";
	public static final String MODIFY = "MODIFY";
	private final AmazonDynamoDB dynamoDBClient;
	private final DynamoDB dynamoDB;
	private static final String REGION = "region";
	private static final String NEW_VALUE = "newValue";
	private static final String TABLE_NAME = "table_name";

	public AuditProducer() {
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

	public Void handleRequest(DynamodbEvent event, Context context) {
		for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
			try {
				String eventName = record.getEventName(); // INSERT, MODIFY, REMOVE
				if (INSERT.equals(eventName)) {
					handleInsertEvent(record, context);
				} else if (MODIFY.equals(eventName)) {
					handleModifyEvent(record, context);
				}
			} catch (Exception e) {
				context.getLogger().log("Error processing record: " + e.getMessage());
			}
		}
		return null;
	}

	private void handleModifyEvent(DynamodbEvent.DynamodbStreamRecord record, Context context) {
		// Get OLD_IMAGE and NEW_IMAGE for the modified item
		var oldImage = record.getDynamodb().getOldImage();
		var newImage = record.getDynamodb().getNewImage();

		if (oldImage == null || newImage == null) {
			context.getLogger().log("Missing OLD_IMAGE or NEW_IMAGE for MODIFY event.");
			return;
		}

		// Extract the item key and values
		String key = newImage.get("key").getS(); // The item key
		int oldValue = Integer.parseInt(oldImage.get(VALUE).getN());
		int newValue = Integer.parseInt(newImage.get(VALUE).getN());

		// Check if the "value" attribute was updated
		if (oldValue != newValue) {
			// Create a new audit entry
			String id = UUID.randomUUID().toString();
			String modificationTime = Instant.now().toString();

			Item auditEntry = new Item()
					.withPrimaryKey(ID, id)
					.withString(ITEM_KEY, key)
					.withString(MODIFICATION_TIME, modificationTime)
					.withString("updatedAttribute", VALUE)
					.withNumber("oldValue", oldValue)
					.withNumber(NEW_VALUE, newValue);

			// Save the audit entry to the Audit table
			saveEventToDynamoDB(auditEntry);

			context.getLogger().log("Audit entry created for MODIFY: " + auditEntry.toJSONPretty());
		}
	}

	private void handleInsertEvent(DynamodbEvent.DynamodbStreamRecord record, Context context) {
		// Get the NEW_IMAGE for the inserted item
		var newImage = record.getDynamodb().getNewImage();

		if (newImage == null) {
			context.getLogger().log("Missing NEW_IMAGE for INSERT event.");
			return;
		}
		if (!newImage.containsKey(KEY) || !newImage.containsKey(VALUE)) {
			context.getLogger().log("NEW_IMAGE is missing required attributes: 'key' or 'value'.");
			return;
		}

		// Extract the item key and value
		String key = newImage.get(KEY).getS();
		int value = Integer.parseInt(newImage.get(VALUE).getN());

		// Create the newValue object
		var newValue = new Item()
				.withString(KEY, key)
				.withNumber(VALUE, value);

		// Create audit entry
		String id = UUID.randomUUID().toString();
		String modificationTime = Instant.now().toString();

		Item auditEntry = new Item()
				.withPrimaryKey(ID, id)
				.withString(ITEM_KEY, key)
				.withString(MODIFICATION_TIME, modificationTime)
				.withMap(NEW_VALUE, newValue.asMap());

		// Save the audit entry to the Audit table
		saveEventToDynamoDB(auditEntry);

		context.getLogger().log("Audit entry created: " + auditEntry.toJSONPretty());
	}

	private void saveEventToDynamoDB (Item auditEntry){
		String tableName = Optional.ofNullable(System.getenv(TABLE_NAME))
				.orElseThrow(() -> new IllegalStateException("Missing table_name environment variable"));
		Table auditTable = dynamoDB.getTable(tableName);

		auditTable.putItem(auditEntry);
	}
}
