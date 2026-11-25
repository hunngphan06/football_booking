package com.example.footballbooking.service;

import com.example.footballbooking.dto.AccountData.Registration;
import com.example.footballbooking.dto.BookingData.Customer;

public interface AccountService {
    Customer register(Registration request);
    void createAdmin(String email, String password);
}
