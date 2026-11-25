package com.example.footballbooking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class BookingData {
    private BookingData() {}

    public record Customer(long id, String name, String email) {}
    public record BookingRequest(Long customerId, Long eventId, List<ItemRequest> items) {}
    public record ItemRequest(Long ticketTypeId, Integer quantity) {}
    public record Item(long ticketTypeId, int quantity, BigDecimal unitPrice) {}
    public record Booking(long id, String reference, long customerId, long eventId, String status,
                          BigDecimal totalAmount, OffsetDateTime createdAt, OffsetDateTime paidAt,
                          List<Item> items, PaymentData.Status paymentStatus) {}
}
