package com.task10;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task10.exeption.WeatherApiException;
import com.task10.weather.OpenMeteoApiClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "processor",
	roleName = "processor-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	tracingMode = TracingMode.Active,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "table_name", value = "${target_table}"),
		@EnvironmentVariable(key = "region", value = "${region}")}
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class Processor implements RequestHandler<Map<String, Object>, Void> {
	public static final String HOURLY = "hourly";
	public static final String HOURLY_UNITS = "hourly_units";
	public static final String GENERATIONTIME_MS = "generationtime_ms";
	public static final String TEMPERATURE_2_M = "temperature_2m";
	public static final String ELEVATION = "elevation";
	public static final String LATITUDE = "latitude";
	public static final String LONGITUDE = "longitude";
	public static final String TIMEZONE = "timezone";
	public static final String TIMEZONE_ABBREVIATION = "timezone_abbreviation";
	public static final String UTC_OFFSET_SECONDS = "utc_offset_seconds";
	public static final String TIME = "time";
	private final OpenMeteoApiClient openMeteoApiClient;
	private static final String REGION = "region";
	private static final String TABLE_NAME = "table_name";
	private final AmazonDynamoDB dynamoDBClient;
	private final DynamoDB dynamoDB;

	public Processor() {
		this.openMeteoApiClient = new OpenMeteoApiClient();
		this.dynamoDBClient = initializeDynamoDBClient();
		this.dynamoDB = new DynamoDB(dynamoDBClient);
	}

	public Void handleRequest(Map<String, Object> request, Context context) {
		try {
			context.getLogger().log("Request received: " + new Gson().toJson(request));

			Map<String, Object> weatherData = openMeteoApiClient.getWeatherForecast();
			Item weatherItem = createWeatherItem(weatherData);
			saveEventToDynamoDB(weatherItem);

			context.getLogger().log("Weather data successfully saved to DynamoDB.");
			
		} catch (AmazonDynamoDBException e) {
            context.getLogger().log("DynamoDB error: " + e.getMessage());
            throw e;
		} catch (WeatherApiException e) {
            context.getLogger().log("Weather API error: " + e.getMessage());
		}
		return null;
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
		String tableName = Optional.ofNullable(System.getenv(TABLE_NAME))
				.orElseThrow(() -> new IllegalStateException("Missing table_name environment variable"));
		Table auditTable = dynamoDB.getTable(tableName);

		auditTable.putItem(auditEntry);
	}

	private Item createWeatherItem(Map<String, Object> weatherData) {
		Map<String, Object> forecast = Map.of(
				ELEVATION, weatherData.get(ELEVATION),
				GENERATIONTIME_MS, weatherData.get(GENERATIONTIME_MS),
				HOURLY, Map.of(
						TEMPERATURE_2_M, ((Map<String, Object>) weatherData.get(HOURLY)).get(TEMPERATURE_2_M),
						TIME, ((Map<String, Object>) weatherData.get(HOURLY)).get(TIME)
				),
				HOURLY_UNITS, Map.of(
						TEMPERATURE_2_M, ((Map<String, Object>) weatherData.get(HOURLY_UNITS)).get(TEMPERATURE_2_M),
						TIME, ((Map<String, Object>) weatherData.get(HOURLY_UNITS)).get(TIME)
				),
				LATITUDE, weatherData.get(LATITUDE),
				LONGITUDE, weatherData.get(LONGITUDE),
				TIMEZONE, weatherData.get(TIMEZONE),
				TIMEZONE_ABBREVIATION, weatherData.get(TIMEZONE_ABBREVIATION),
				UTC_OFFSET_SECONDS, weatherData.get(UTC_OFFSET_SECONDS)
		);
		// Create a new Item for DynamoDB and populate it with weather data
		return new Item()
				.withPrimaryKey("id", UUID.randomUUID().toString()) // Generate UUID for the id
				.withMap("forecast", forecast);
	}
}