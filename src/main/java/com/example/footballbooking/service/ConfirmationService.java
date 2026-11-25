package com.example.footballbooking.service;

public interface ConfirmationService {
    byte[] createPdf(long bookingId);
}
