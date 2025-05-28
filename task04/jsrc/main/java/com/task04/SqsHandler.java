package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.syndicate.deployment.annotations.events.SqsTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;
import java.util.Map;

@DependsOn(
        name = "async_queue",
        resourceType = ResourceType.SQS_QUEUE
)
@LambdaHandler(
    lambdaName = "sqs_handler",
    roleName = "sqs_handler-role",
    isPublishVersion = true,
    aliasName = "${lambdas_alias_name}",
    logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@SqsTriggerEventSource(
    targetQueue = "async_queue",
    batchSize = 10
)
public class SqsHandler implements RequestHandler<SQSEvent, Map<String, Object>> {

	public Map<String, Object> handleRequest(SQSEvent event, Context context) {
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
                String messageBody = record.getBody();
                context.getLogger().log(String.format(
                    "Processing message ID: %s, body: %s",
                    record.getMessageId(),
                    messageBody
                ));
                
            } catch (Exception e) {
                context.getLogger().log(String.format("Error processing message: %s", e.getMessage()));
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