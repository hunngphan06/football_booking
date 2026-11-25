package com.example.footballbooking;

import com.example.footballbooking.integration.LlmClient;
import com.example.footballbooking.exception.BookingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:assistant_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "assistant.api-key=test-only", "assistant.model=test-model"
})
@AutoConfigureMockMvc
@Sql("/match-fixtures.sql")
class AssistantApiTests {
    private static final ConcurrentLinkedQueue<String> answers = new ConcurrentLinkedQueue<>();
    private static final AtomicReference<String> lastRequest = new AtomicReference<>();
    private static final HttpServer server = startServer();
    @Autowired private MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("assistant.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    private static HttpServer startServer() {
        try {
            var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/v1/chat/completions", exchange -> {
                lastRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String content = answers.poll();
                int status = content == null ? 500 : 200;
                String body = content == null ? "{}" : new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "choices", java.util.List.of(java.util.Map.of("finish_reason", "stop",
                        "message", java.util.Map.of("content", content)))));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            http.start();
            return http;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void searchUsesAiCriteriaToFilterDatabaseByPriceAndDate() throws Exception {
        answers.add("""
            {"keyword":"Sao Mai","venue":null,"from":"2026-12-05T00:00:00Z",
             "to":"2026-12-06T00:00:00Z","maxPrice":200000,"category":"SPORTS"}
            """);
        mvc.perform(post("/api/assistant/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"Sao Mai dưới 200k\",\"size\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.totalElements").value(1));
        assertTrue(lastRequest.get().contains("test-model"));
    }

    @Test
    void cheaperPriceExcludesTicketsAndInvalidProviderOutputReturns502() throws Exception {
        answers.add("{\"category\":\"SPORTS\",\"maxPrice\":100000}");
        mvc.perform(post("/api/assistant/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"vé dưới 100k\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        answers.add("{\"category\":\"SPORTS\",\"from\":\"bad-date\"}");
        mvc.perform(post("/api/assistant/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"ngày mai\"}"))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void askSendsOnlyEventContextAndReturnsTheProvidersAnswer() throws Exception {
        answers.add("{\"answer\":\"Thông tin chưa được cung cấp.\"}");
        mvc.perform(post("/api/events/1/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Có bãi giữ xe không?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Thông tin chưa được cung cấp."));
        assertTrue(lastRequest.get().contains("FC Sao Mai vs FC Song Xanh"));
        assertTrue(lastRequest.get().contains("description"));
        assertFalse(lastRequest.get().contains("customerId"));
    }

    @Test
    void inputAndProviderFailuresHaveConsistentErrors() throws Exception {
        mvc.perform(post("/api/assistant/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/events/999/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Sân nào?\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/events/1/ask").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Sân nào?\"}"))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.message")
                .value("AI provider failed or returned an invalid response"));
    }

    @Test
    void noKeyReturns503BeforeConnecting() {
        var client = new LlmClient("", "http://127.0.0.1:1", "test", new ObjectMapper());
        var exception = assertThrows(BookingException.class, () -> client.complete("JSON", "test"));
        assertEquals(503, exception.getStatus().value());
    }
    private MockHttpServletRequestBuilder post(String path, Object... values) {
        return MockMvcRequestBuilders.post(path, values).with(csrf());
    }
}
