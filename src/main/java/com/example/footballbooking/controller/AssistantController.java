package com.example.footballbooking.controller;

import com.example.footballbooking.dto.AssistantData.*;
import com.example.footballbooking.service.AssistantService;
import org.springframework.web.bind.annotation.*;

@RestController
public class AssistantController {
    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @PostMapping("/api/assistant/search")
    public SearchResult search(@RequestBody SearchRequest request) {
        return service.search(request);
    }

    @PostMapping("/api/events/{id}/ask")
    public Answer ask(@PathVariable long id, @RequestBody QuestionRequest request) {
        return service.ask(id, request);
    }
}
