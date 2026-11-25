package com.example.footballbooking.dto;

import com.example.footballbooking.model.Event;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AssistantData {
    private AssistantData() {}

    public record SearchRequest(String query, Integer page, Integer size) {}
    public record QuestionRequest(String question) {}
    public record Answer(String answer) {}
    public record Criteria(String keyword, String venue, Instant from, Instant to,
                           BigDecimal maxPrice, String category) {}
    public record SearchResult(Criteria criteria, List<Event> content, int page, int size,
                               long totalElements, int totalPages) {}
}
