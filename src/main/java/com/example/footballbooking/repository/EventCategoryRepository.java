package com.example.footballbooking.repository;

import com.example.footballbooking.dto.EventCategoryData.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EventCategoryRepository {
    private final JdbcClient db;

    public EventCategoryRepository(JdbcClient db) {
        this.db = db;
    }

    public List<Category> findAll() {
        return db.sql("SELECT code, name, active FROM event_categories ORDER BY code")
            .query(Category.class).list();
    }

    public Optional<Boolean> lockActive(String code) {
        return db.sql("SELECT active FROM event_categories WHERE code = ? FOR UPDATE")
            .param(code).query(Boolean.class).optional();
    }

    public void insert(Category category) {
        db.sql("INSERT INTO event_categories (code, name, active) VALUES (?, ?, ?)")
            .params(category.code(), category.name(), category.active()).update();
    }

    public void update(Category category) {
        db.sql("UPDATE event_categories SET name = ?, active = ? WHERE code = ?")
            .params(category.name(), category.active(), category.code()).update();
    }

    public boolean hasEvents(String code) {
        return db.sql("SELECT EXISTS (SELECT 1 FROM events WHERE category = ?)")
            .param(code).query(Boolean.class).single();
    }

    public void delete(String code) {
        db.sql("DELETE FROM event_categories WHERE code = ?").param(code).update();
    }
}
