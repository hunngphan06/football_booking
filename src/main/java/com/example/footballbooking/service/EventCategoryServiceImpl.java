package com.example.footballbooking.service;

import com.example.footballbooking.dto.EventCategoryData.Category;
import com.example.footballbooking.dto.EventCategoryData.CategoryRequest;
import com.example.footballbooking.exception.BookingException;
import com.example.footballbooking.exception.InvalidRequestException;
import com.example.footballbooking.repository.EventCategoryRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventCategoryServiceImpl implements EventCategoryService {
    private final EventCategoryRepository repository;

    public EventCategoryServiceImpl(EventCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Category> list() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public Category create(CategoryRequest request) {
        String code = normalize(request.code());
        Category category = new Category(code, validName(request.name()), !Boolean.FALSE.equals(request.active()));
        try {
            repository.insert(category);
        } catch (DuplicateKeyException ex) {
            throw new BookingException(HttpStatus.CONFLICT, "Category code already exists");
        }
        return category;
    }

    @Override
    @Transactional
    public Category update(String code, CategoryRequest request) {
        String key = normalize(code);
        if (request.code() != null && !normalize(request.code()).equals(key)) {
            throw new InvalidRequestException("Category code cannot be changed");
        }
        boolean active = lockCategory(key);
        Category category = new Category(key, validName(request.name()),
            request.active() == null ? active : request.active());
        repository.update(category);
        return category;
    }

    @Override
    @Transactional
    public void delete(String code) {
        String key = normalize(code);
        lockCategory(key);
        if (repository.hasEvents(key)) {
            throw new BookingException(HttpStatus.CONFLICT, "This category has events. Mark it inactive instead.");
        }
        repository.delete(key);
    }

    private boolean lockCategory(String code) {
        return repository.lockActive(code)
            .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "Event category does not exist"));
    }

    private String normalize(String code) {
        String value = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new InvalidRequestException("Use a category code of 1–32 ASCII letters, digits or underscores");
        }
        if (value.equals("OTHER")) {
            throw new InvalidRequestException("OTHER is reserved for unsupported categories");
        }
        return value;
    }

    private String validName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new InvalidRequestException("Category name must contain between 1 and 100 characters");
        }
        return name.strip();
    }
}
