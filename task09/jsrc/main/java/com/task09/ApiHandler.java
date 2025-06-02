package com.task09;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		// Use LinkedHashMap to preserve field order in the top-level response
		Map<String, Object> response = new LinkedHashMap<>();

		// Extract request context and HTTP details
		Map<String, Object> requestContext = (Map<String, Object>) request.get("requestContext");
		Map<String, Object> http = (Map<String, Object>) requestContext.get("http");
		String method = (String) http.get("method");
		String path = (String) http.get("path");

		// Use LinkedHashMap for headers to maintain order
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Access-Control-Allow-Origin", "*");

		// Validate HTTP method and path
		if (!"/weather".equals(path) || !"GET".equalsIgnoreCase(method)) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("statusCode", 400);
			body.put("message", String.format(
					"Bad request syntax or unsupported method. Request path: %s. HTTP method: %s",
					path, method
			));

			response.put("statusCode", 400);
			response.put("headers", headers);
			response.put("body", body);
			return response;
		}

		// Use LinkedHashMap for ordered weather data
		Map<String, Object> orderedWeatherData = new LinkedHashMap<>();
		try {
			// Extract query parameters
			Map<String, String> queryParams = (Map<String, String>) request.get("queryStringParameters");
			double latitude = Double.parseDouble(queryParams.get("latitude"));
			double longitude = Double.parseDouble(queryParams.get("longitude"));

			// Fetch weather data from Open-Meteo API
			Map<String, Object> weatherData = openMeteoApiClient.getWeatherForecast(latitude, longitude);

			// Add fields in the specified order
			orderedWeatherData.put("latitude", latitude);
			orderedWeatherData.put("longitude", longitude);
			orderedWeatherData.put("generationtime_ms", weatherData.get("generationtime_ms"));
			orderedWeatherData.put("utc_offset_seconds", 7200); // Explicitly set timezone offset
			orderedWeatherData.put("timezone", "Europe/Kiev");
			orderedWeatherData.put("timezone_abbreviation", "EET");
			orderedWeatherData.put("elevation", weatherData.get("elevation"));

			// Add hourly_units
			Map<String, Object> hourlyUnits = new LinkedHashMap<>();
			hourlyUnits.put("time", "iso8601");
			hourlyUnits.put("temperature_2m", "°C");
			hourlyUnits.put("relative_humidity_2m", "%");
			hourlyUnits.put("wind_speed_10m", "km/h");
			orderedWeatherData.put("hourly_units", hourlyUnits);

			// Add hourly with truncated data
			Map<String, Object> hourlyData = (Map<String, Object>) weatherData.get("hourly");
			Map<String, Object> truncatedHourlyData = new LinkedHashMap<>();
			truncatedHourlyData.put("time", truncateWithEllipsis((List<String>) hourlyData.get("time"), 3));
			truncatedHourlyData.put("temperature_2m", truncateWithEllipsis((List<Double>) hourlyData.get("temperature_2m"), 3));
			truncatedHourlyData.put("relative_humidity_2m", truncateWithEllipsis((List<Integer>) hourlyData.get("relative_humidity_2m"), 3));
			truncatedHourlyData.put("wind_speed_10m", truncateWithEllipsis((List<Double>) hourlyData.get("wind_speed_10m"), 3));
			orderedWeatherData.put("hourly", truncatedHourlyData);

			// Add current_units
			Map<String, Object> currentUnits = new LinkedHashMap<>();
			currentUnits.put("time", "iso8601");
			currentUnits.put("interval", "seconds");
			currentUnits.put("temperature_2m", "°C");
			currentUnits.put("wind_speed_10m", "km/h");
			orderedWeatherData.put("current_units", currentUnits);

			// Add current weather
			Map<String, Object> currentWeather = (Map<String, Object>) weatherData.get("current");
			orderedWeatherData.put("current", currentWeather);

		} catch (Exception e) {
			// Handle exceptions
			orderedWeatherData.put("message", String.format("Error: %s", e.getMessage()));
		}

		Gson gson = new Gson();
		String jsonResponse = gson.toJson(orderedWeatherData);
		response.put("statusCode", 200); // HTTP status code
		response.put("message", jsonResponse);

		Map<String, Object> finalResponse = new LinkedHashMap<>();
		finalResponse.put("statusCode", 200);
		finalResponse.put("headers", headers);
		finalResponse.put("body", response);
		return finalResponse; // Return the LinkedHashMap response
	}

	private List<Object> truncateWithEllipsis(List<?> originalList, int limit) {
		List<Object> truncatedList = new ArrayList<>();
		for (int i = 0; i < Math.min(limit, originalList.size()); i++) {
			truncatedList.add(originalList.get(i));
		}
		truncatedList.add("...");
		return truncatedList;
	}
}
