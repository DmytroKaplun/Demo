package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.SnsEventSource;

import java.util.HashMap;
import java.util.Map;

@DependsOn(
		name = "lambda_topic",
		resourceType = ResourceType.SNS_TOPIC
)
@LambdaHandler(
    lambdaName = "sns_handler",
	roleName = "sns_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@SnsEventSource(
		targetTopic = "lambda_topic"
)
public class SnsHandler implements RequestHandler<SNSEvent, Map<String, Object>> {

	public Map<String, Object> handleRequest(SNSEvent event, Context context) {
		try {
			if (event == null || event.getRecords() == null) {
				context.getLogger().log("Received null event or records");
				return createResponse(400, "Invalid input");
			}

			event.getRecords().forEach(record -> {
				try {
					if (record == null) {
						context.getLogger().log("Received null record");
						return;
					}
					String message = record.getSNS().getMessage();
					context.getLogger().log("Received SNS message: " + message);
				} catch (Exception e) {
					context.getLogger().log("Error processing SNS message: " + e.getMessage());
				}
			});

			return createResponse(200, "Messages processed successfully");
		} catch (Exception e) {
			context.getLogger().log("Fatal error in handler: " + e.getMessage());
			return createResponse(500, "Internal server error");
		}
	}

	private Map<String, Object> createResponse(int statusCode, String body) {
		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", statusCode);
		response.put("message", body);
		return response;
	}
}