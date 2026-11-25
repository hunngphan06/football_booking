package com.example.footballbooking.controller;

import com.example.footballbooking.dto.AccountData.Registration;
import com.example.footballbooking.dto.AccountData.Session;
import com.example.footballbooking.dto.BookingData.Customer;
import com.example.footballbooking.security.AccountPrincipal;
import com.example.footballbooking.service.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController {
    private final AccountService accounts;
    private final boolean demo;

    public AccountController(AccountService accounts, @Value("${booking.demo.enabled:false}") boolean demo) {
        this.accounts = accounts;
        this.demo = demo;
    }

    @GetMapping("/api/auth/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/api/auth/me")
    public Session current(@AuthenticationPrincipal AccountPrincipal principal) {
        return new Session(principal == null ? null : principal.account(), demo);
    }

    @PostMapping({"/api/auth/register", "/api/customers"})
    @ResponseStatus(HttpStatus.CREATED)
    public Customer register(@RequestBody Registration request) {
        return accounts.register(request);
    }
}
