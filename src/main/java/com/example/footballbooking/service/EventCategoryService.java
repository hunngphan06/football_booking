package com.example.footballbooking.service;

import com.example.footballbooking.dto.EventCategoryData.Category;
import com.example.footballbooking.dto.EventCategoryData.CategoryRequest;
import java.util.List;

public interface EventCategoryService {
    List<Category> list();
    Category create(CategoryRequest request);
    Category update(String code, CategoryRequest request);
    void delete(String code);
}
