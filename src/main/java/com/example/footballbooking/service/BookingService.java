package com.example.footballbooking.service;

import com.example.footballbooking.dto.BookingData.Booking;
import com.example.footballbooking.dto.BookingData.BookingRequest;
import com.example.footballbooking.dto.BookingData.Customer;
import java.util.List;

public interface BookingService {
    Customer register(String name, String email);
    List<Booking> customerBookings(long id);
    Booking create(BookingRequest request);
    Booking get(long id);
    Booking cancel(long id);
}
