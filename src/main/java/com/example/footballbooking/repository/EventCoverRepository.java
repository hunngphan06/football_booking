package com.example.footballbooking.repository;

import com.example.footballbooking.model.EventCover;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EventCoverRepository {
    private final JdbcClient db;

    public EventCoverRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<EventCover> find(long eventId) {
        return db.sql("SELECT content_type, image_data FROM event_covers WHERE event_id = ?")
            .param(eventId).query((row, number) -> new EventCover(row.getString("content_type"),
                row.getBytes("image_data"))).optional();
    }

    public void save(long eventId, EventCover cover) {
        int updated = db.sql("UPDATE event_covers SET content_type = ?, image_data = ? WHERE event_id = ?")
            .params(cover.contentType(), cover.data(), eventId).update();
        if (updated == 0) {
            db.sql("INSERT INTO event_covers (event_id, content_type, image_data) VALUES (?, ?, ?)")
                .params(eventId, cover.contentType(), cover.data()).update();
        }
    }

    public void delete(long eventId) {
        db.sql("DELETE FROM event_covers WHERE event_id = ?").param(eventId).update();
    }
}
