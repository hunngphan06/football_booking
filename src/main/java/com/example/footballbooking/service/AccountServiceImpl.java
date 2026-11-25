package com.example.footballbooking.service;

import com.example.footballbooking.dto.AccountData.Registration;
import com.example.footballbooking.dto.BookingData.Customer;
import com.example.footballbooking.exception.BookingException;
import com.example.footballbooking.exception.InvalidRequestException;
import com.example.footballbooking.repository.AccountRepository;
import com.example.footballbooking.repository.AccountRepository.Account;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accounts;
    private final BookingService bookings;
    private final PasswordEncoder passwords;

    public AccountServiceImpl(AccountRepository accounts, BookingService bookings, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.bookings = bookings;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public Customer register(Registration request) {
        validatePassword(request.password());
        Customer customer = bookings.register(request.name(), request.email());
        try {
            accounts.insert(new Account(customer.email(), passwords.encode(request.password()),
                "USER", customer.name(), customer.id()));
        } catch (DuplicateKeyException ex) {
            throw new BookingException(HttpStatus.CONFLICT, "Email is already registered");
        }
        return customer;
    }

    @Override
    @Transactional
    public void createAdmin(String email, String password) {
        String address = email.strip().toLowerCase(Locale.ROOT);
        if (address.length() > 255 || !address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new InvalidRequestException("Administrator email format is invalid");
        }
        var existing = accounts.find(address);
        if (existing.isPresent()) {
            if (!"ADMIN".equals(existing.get().role())) {
                throw new IllegalStateException("Administrator email is already used by a customer account");
            }
            return;
        }
        validatePassword(password);
        accounts.insert(new Account(address, passwords.encode(password), "ADMIN", "Quản trị viên", null));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("Password must contain at least 8 characters and at most 72 UTF-8 bytes");
        }
    }
}
