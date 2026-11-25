package com.example.footballbooking.repository;

import com.example.footballbooking.dto.BookingData.Booking;
import com.example.footballbooking.dto.BookingData.Item;
import com.example.footballbooking.dto.BookingData.Customer;
import com.example.footballbooking.dto.PaymentData.Status;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {
    private final JdbcClient db;

    public BookingRepository(JdbcClient db) {
        this.db = db;
    }

    public record Ticket(long matchId, BigDecimal price) {}

    public long insertCustomer(String name, String email) {
        var key = new GeneratedKeyHolder();
        db.sql("INSERT INTO customers (name, email) VALUES (?, ?)").params(name, email).update(key, "id");
        return key.getKey().longValue();
    }

    public boolean customerExists(long id) {
        return db.sql("SELECT id FROM customers WHERE id = ?").param(id).query(Long.class).optional().isPresent();
    }

    public Optional<Customer> findCustomer(long id) {
        return db.sql("SELECT id, name, email FROM customers WHERE id = ?").param(id)
            .query((rs, row) -> new Customer(rs.getLong("id"), rs.getString("name"), rs.getString("email")))
            .optional();
    }

    public Optional<Boolean> lockEventSales(long id) {
        return db.sql("SELECT sale_open FROM events WHERE id = ? FOR UPDATE")
            .param(id).query(Boolean.class).optional();
    }

    public boolean hasBookings(long eventId) {
        return db.sql("SELECT EXISTS (SELECT 1 FROM bookings WHERE match_id = ?)")
            .param(eventId).query(Boolean.class).single();
    }

    public List<Long> findCustomerBookingIds(long customerId) {
        return db.sql("SELECT id FROM bookings WHERE customer_id = ? ORDER BY id DESC")
            .param(customerId).query(Long.class).list();
    }

    public Optional<Ticket> findTicket(long id) {
        return db.sql("SELECT match_id, price FROM ticket_types WHERE id = ?").param(id)
            .query((rs, row) -> new Ticket(rs.getLong("match_id"), rs.getBigDecimal("price"))).optional();
    }

    public boolean reserve(long ticketId, int quantity) {
        return db.sql("""
            UPDATE ticket_types SET allocated_quantity = allocated_quantity + ?
            WHERE id = ? AND total_quantity - allocated_quantity >= ?
            """).params(quantity, ticketId, quantity).update() == 1;
    }

    public void release(long ticketId, int quantity) {
        db.sql("UPDATE ticket_types SET allocated_quantity = allocated_quantity - ? WHERE id = ?")
            .params(quantity, ticketId).update();
    }

    public long insertBooking(String reference, long customerId, long eventId, BigDecimal total) {
        var key = new GeneratedKeyHolder();
        db.sql("""
            INSERT INTO bookings (reference, customer_id, match_id, status, total_amount)
            VALUES (?, ?, ?, 'PENDING', ?)
            """).params(reference, customerId, eventId, total).update(key, "id");
        return key.getKey().longValue();
    }

    public void insertItem(long bookingId, Item item) {
        db.sql("INSERT INTO booking_items (booking_id, ticket_type_id, quantity, unit_price) VALUES (?, ?, ?, ?)")
            .params(bookingId, item.ticketTypeId(), item.quantity(), item.unitPrice()).update();
    }

    public Optional<Booking> findBooking(long id) {
        return db.sql("""
            SELECT bookings.*, payments.status AS payment_status
            FROM bookings LEFT JOIN payments ON payments.booking_id = bookings.id
            WHERE bookings.id = ?
            """).param(id)
            .query((rs, row) -> new Booking(id, rs.getString("reference"), rs.getLong("customer_id"),
                rs.getLong("match_id"), rs.getString("status"), rs.getBigDecimal("total_amount"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("paid_at", OffsetDateTime.class),
                List.of(), paymentStatus(rs)))
            .optional()
            .map(booking -> new Booking(booking.id(), booking.reference(), booking.customerId(), booking.eventId(),
                booking.status(), booking.totalAmount(), booking.createdAt(), booking.paidAt(), findItems(id),
                booking.paymentStatus()));
    }

    private Status paymentStatus(ResultSet row) throws SQLException {
        String status = row.getString("payment_status");
        return status == null ? null : Status.valueOf(status);
    }

    public List<Item> findItems(long bookingId) {
        return db.sql("""
            SELECT ticket_type_id, quantity, unit_price FROM booking_items
            WHERE booking_id = ? ORDER BY ticket_type_id
            """).param(bookingId).query((rs, row) -> new Item(rs.getLong("ticket_type_id"),
                rs.getInt("quantity"), rs.getBigDecimal("unit_price"))).list();
    }

    public Optional<String> lockStatus(long id) {
        return db.sql("SELECT status FROM bookings WHERE id = ? FOR UPDATE").param(id)
            .query(String.class).optional();
    }

    public void markPaid(long id, OffsetDateTime chargedAt) {
        db.sql("UPDATE bookings SET status = 'PAID', paid_at = ? WHERE id = ?")
            .params(chargedAt, id).update();
    }

    public void updateStatus(long id, String status) {
        db.sql("UPDATE bookings SET status = ? WHERE id = ?").params(status, id).update();
    }
}
