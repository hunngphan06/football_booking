package com.example.footballbooking.integration;

import com.example.footballbooking.exception.BookingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LlmClient {
    private final RestClient http;
    private final String key;
    private final String model;
    private final ObjectMapper json;

    public LlmClient(@Value("${assistant.api-key:}") String key,
                     @Value("${assistant.base-url:https://api.groq.com/openai/v1}") String baseUrl,
                     @Value("${assistant.model:qwen/qwen3.8-27b}") String model,
                     ObjectMapper json) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.key = key;
        this.model = model;
        this.json = json;
    }

    public JsonNode complete(String instructions, String input) {
        if (key.isBlank()) {
            throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình khóa API cho trợ lý.");
        }
        try {
            var body = Map.of("model", model, "max_completion_tokens", 1200, "reasoning_effort", "none",
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "system", "content", instructions),
                    Map.of("role", "user", "content", input)));
            JsonNode response = http.post().uri("/chat/completions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if (response == null || !response.path("choices").isArray() || response.path("choices").isEmpty()) {
                throw unavailable();
            }
            JsonNode choice = response.path("choices").get(0);
            if (!choice.path("finish_reason").asText().equals("stop")) {
                throw unavailable();
            }
            String answer = choice.path("message").path("content").asText();
            if (answer.isBlank() || answer.length() > 16000) {
                throw unavailable();
            }
            JsonNode result = json.readTree(answer);
            if (!result.isObject()) {
                throw unavailable();
            }
            return result;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                throw new BookingException(HttpStatus.TOO_MANY_REQUESTS, "Đã chạm hạn mức của trợ lý AI. Hãy thử lại sau.");
            }
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new BookingException(HttpStatus.BAD_GATEWAY, "Không xác thực được dịch vụ AI. Hãy kiểm tra khóa API.");
            }
            throw unavailable();
        } catch (RestClientException | java.io.IOException ex) {
            throw unavailable();
        }
    }

    private BookingException unavailable() {
        return new BookingException(HttpStatus.BAD_GATEWAY, "AI provider failed or returned an invalid response");
    }
}
