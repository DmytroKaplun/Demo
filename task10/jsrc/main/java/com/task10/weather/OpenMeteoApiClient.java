package com.task10.weather;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.task10.exeption.WeatherApiException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class OpenMeteoApiClient {
    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;

    public OpenMeteoApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Map<String, Object> getWeatherForecast()  {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WeatherApiException("Failed to fetch weather data from API", e);
        }
        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
            return GSON.fromJson(response.body(), typeToken.getType());
    }
}

