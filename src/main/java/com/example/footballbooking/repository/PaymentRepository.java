package com.example.footballbooking.repository;

import com.example.footballbooking.dto.BookingData.Booking;
import com.example.footballbooking.dto.PaymentData.Payment;
import com.example.footballbooking.dto.PaymentData.Scenario;
import com.example.footballbooking.dto.PaymentData.Status;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {
    private final JdbcClient db;

    public PaymentRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<Payment> find(long bookingId) {
        return db.sql("SELECT * FROM payments WHERE booking_id = ?").param(bookingId)
            .query((rs, row) -> new Payment(rs.getLong("booking_id"), rs.getString("reference"),
                rs.getBigDecimal("amount"), Status.valueOf(rs.getString("status")),
                Scenario.valueOf(rs.getString("scenario")), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class), rs.getObject("charged_at", OffsetDateTime.class),
                rs.getObject("refunded_at", OffsetDateTime.class))).optional();
    }

    public void create(Booking booking, Scenario scenario) {
        db.sql("""
            INSERT INTO payments (booking_id, reference, amount, status, scenario)
            VALUES (?, ?, ?, 'PENDING', ?)
            """).params(booking.id(), UUID.randomUUID().toString(), booking.totalAmount(), scenario.name()).update();
    }

    public void retry(long bookingId, Scenario scenario) {
        db.sql("""
            UPDATE payments SET status = 'PENDING', scenario = ?, updated_at = CURRENT_TIMESTAMP
            WHERE booking_id = ?
            """).params(scenario.name(), bookingId).update();
    }

    public void recordCharge(long bookingId) {
        db.sql("""
            UPDATE payments SET status = 'SUCCEEDED', charged_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP WHERE booking_id = ?
            """).param(bookingId).update();
    }

    public void updateStatus(long bookingId, Status status) {
        db.sql("UPDATE payments SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE booking_id = ?")
            .params(status.name(), bookingId).update();
    }

    public void recordRefund(long bookingId) {
        db.sql("""
            UPDATE payments SET status = 'REFUNDED', refunded_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP WHERE booking_id = ?
            """).param(bookingId).update();
    }
}
