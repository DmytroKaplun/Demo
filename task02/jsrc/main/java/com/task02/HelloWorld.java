package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "hello_world",
	roleName = "hello_world-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class HelloWorld implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		// Extract HTTP method and path from the event object
		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		Map<String, Object> http = (Map<String, Object>) requestContext.get("http");
		String httpMethod = (String) http.get("method");
		String path = (String) http.get("path");

		// Create headers for the response
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Access-Control-Allow-Origin", "*"); // Optional: for CORS support

		// Create the top-level response object
		Map<String, Object> response = new HashMap<>();

		if ("GET".equalsIgnoreCase(httpMethod) && "/hello".equals(path)) {
			// Create the body for the successful response (with statusCode included inside the body)
			Map<String, Object> body = new HashMap<>();
			body.put("statusCode", 200);
			body.put("message", "Hello from Lambda");

			response.put("statusCode", 200); // HTTP status code
			response.put("headers", headers); // Headers
			response.put("body", body); // Body containing the expected response structure
		} else {
			// Create the body for the error response
			Map<String, Object> body = new HashMap<>();
			body.put("statusCode", 400);
			body.put("message", String.format(
					"Bad request syntax or unsupported method. Request path: %s. HTTP method: %s",
					path, httpMethod
			));

			response.put("statusCode", 400); // HTTP status code
			response.put("headers", headers); // Headers
			response.put("body", body); // Body containing the error response structure
		}

		return response;
	}
}
