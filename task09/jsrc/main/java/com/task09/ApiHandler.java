package com.task09;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.Architecture;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import org.example.weather.OpenMeteoApiClient;
import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
		layers = {"open-meteo-layer"},
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaLayer(
		layerName = "open-meteo-layer",
		libraries = {"lib/open-meteo-layer-1.0-SNAPSHOT-jar-with-dependencies.jar"},
		runtime = DeploymentRuntime.JAVA11,
		architectures = {Architecture.ARM64},
		artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	private final OpenMeteoApiClient openMeteoApiClient;

	public ApiHandler() {
		this.openMeteoApiClient = new OpenMeteoApiClient();
	}

	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		Map<String, Object> response = new HashMap<>();
		String path = (String) request.get("path");
		String method = (String) request.get("httpMethod");

		if (!"/weather".equals(path) || !"GET".equalsIgnoreCase(method)) {
			// Return 400 Bad Request for unsupported paths or methods
			response.put("statusCode", 400);
			response.put("body", String.format("{\"statusCode\": 400, \"message\": \"Bad request syntax or unsupported method. Request path: %s. HTTP method: %s\"}", path, method));
			return response;
		}

		try {
			// Retrieve latitude and longitude from query parameters
			Map<String, String> queryParams = (Map<String, String>) request.get("queryStringParameters");
			double latitude = Double.parseDouble(queryParams.get("latitude"));
			double longitude = Double.parseDouble(queryParams.get("longitude"));

			// Fetch weather data using the SDK
			Map<String, Object> weatherData = openMeteoApiClient.getWeatherForecast(latitude, longitude);
			response.put("statusCode", 200);
			response.put("body", weatherData);
		} catch (Exception e) {
			// Handle errors gracefully
			response.put("statusCode", 500);
			response.put("body", String.format("{\"statusCode\": 500, \"message\": \"Internal server error. Error: %s\"}", e.getMessage()));
		}
		return response;
	}
}
