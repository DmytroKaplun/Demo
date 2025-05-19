package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.LambdaUrlConfig;
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

		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		Map<String, Object> http = (Map<String, Object>) requestContext.get("http");
		String httpMethod = (String) http.get("method");
		String path = (String) http.get("path");


		Map<String, Object> response = new HashMap<>();
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Access-Control-Allow-Origin", "*");

		if ("GET".equalsIgnoreCase(httpMethod) && "/hello".equals(path)) {
			response.put("statusCode", 200);
			response.put("headers", headers);
			response.put("body", "{\"message\": \"Hello from Lambda\"}");
		} else {
			response.put("statusCode", 400);
			response.put("headers", headers);
			response.put("body", String.format(
					"{\"statusCode\": 400, \"message\": \"Bad request syntax or unsupported method. Request path: %s. HTTP method: %s\"}",
					path, httpMethod
			));
		}
		return response;
	}
}
