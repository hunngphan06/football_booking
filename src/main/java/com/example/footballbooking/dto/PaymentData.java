package com.example.footballbooking.dto;

import com.example.footballbooking.dto.BookingData.Booking;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class PaymentData {
    private PaymentData() {}

    public enum Status { PENDING, FAILED, SUCCEEDED, REFUND_PENDING, REFUNDED }
    public enum Scenario { SUCCESS, FAIL_BEFORE_CHARGE, TIMEOUT, FAIL_AFTER_CHARGE }

    public record PaymentRequest(Scenario scenario) {}
    public record Payment(long bookingId, String reference, BigDecimal amount, Status status,
                          Scenario scenario, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                          OffsetDateTime chargedAt, OffsetDateTime refundedAt) {}
    public record Result(Booking booking, Payment payment) {}
}
