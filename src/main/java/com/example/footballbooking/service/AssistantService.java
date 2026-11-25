package com.example.footballbooking.service;

import com.example.footballbooking.dto.AssistantData.*;

public interface AssistantService {
    SearchResult search(SearchRequest request);
    Answer ask(long eventId, QuestionRequest request);
}
