package com.example.footballbooking.service;

import com.example.footballbooking.dto.PaymentData.Payment;
import com.example.footballbooking.dto.PaymentData.Result;
import com.example.footballbooking.dto.PaymentData.Scenario;

public interface PaymentService {
    Payment get(long bookingId);
    Result pay(long bookingId, Scenario scenario);
    Result reconcile(long bookingId);
    Payment refund(long bookingId);
}
