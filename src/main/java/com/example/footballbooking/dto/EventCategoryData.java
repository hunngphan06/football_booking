package com.example.footballbooking.dto;

public final class EventCategoryData {
    private EventCategoryData() {}

    public record Category(String code, String name, boolean active) {}
    public record CategoryRequest(String code, String name, Boolean active) {}
}
