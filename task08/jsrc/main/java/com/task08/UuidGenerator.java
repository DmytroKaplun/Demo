package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.model.Bucket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@LambdaHandler(
    lambdaName = "uuid_generator",
	roleName = "uuid_generator-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "bucket_name", value = "${target_bucket}"),
		@EnvironmentVariable(key = "region", value = "${region}")}
)
@RuleEventSource(
		targetRule = "uuid_trigger"
)
@DependsOn(
		name = "uuid_trigger",
		resourceType = ResourceType.CLOUDWATCH_RULE
)
public class UuidGenerator implements RequestHandler<Object, Map<String, Object>> {
	private static final String TABLE_NAME = "bucket_name";
	public static final String REGION = "region";

	public Map<String, Object> handleRequest(Object request, Context context) {
		try {
			List<String> uuidsList= IntStream.range(0, 10)
					.mapToObj(i -> UUID.randomUUID().toString())
					.collect(Collectors.toList());

			Map<String, Object> jsonData = new HashMap<>();
			jsonData.put("ids", uuidsList);

			ObjectMapper objectMapper = new ObjectMapper();
			String jsonString = objectMapper.writeValueAsString(jsonData);

			String fileName = Instant.now().toString();

			String region = Optional.ofNullable(System.getenv(REGION))
					.orElseThrow(() -> new IllegalStateException("Missing region environment variable"));
			String tableName = Optional.ofNullable(System.getenv(TABLE_NAME))
					.orElseThrow(() -> new IllegalStateException("Missing table_name environment variable"));

			AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(region).build();
			s3Client.putObject(tableName, fileName, jsonString);

			// Log the operation
			context.getLogger().log("File created: " + fileName);

			// Return a success response
			Map<String, Object> resultMap = new HashMap<>();
			resultMap.put("statusCode", 200);
			resultMap.put("body", "UUIDs generated and stored in S3 bucket successfully.");
			return resultMap;
		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());

			Map<String, Object> errorMap = new HashMap<>();
			errorMap.put("statusCode", 500);
			errorMap.put("body", "An error occurred: " + e.getMessage());
			return errorMap;
		}
	}
}
