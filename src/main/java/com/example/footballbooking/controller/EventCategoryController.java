package com.example.footballbooking.controller;

import com.example.footballbooking.dto.EventCategoryData.Category;
import com.example.footballbooking.dto.EventCategoryData.CategoryRequest;
import com.example.footballbooking.service.EventCategoryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/event-categories")
public class EventCategoryController {
    private final EventCategoryService service;

    public EventCategoryController(EventCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Category> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Category create(@RequestBody CategoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{code}")
    public Category update(@PathVariable String code, @RequestBody CategoryRequest request) {
        return service.update(code, request);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        service.delete(code);
    }
}
