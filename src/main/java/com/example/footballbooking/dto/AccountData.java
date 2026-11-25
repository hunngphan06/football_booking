package com.example.footballbooking.dto;

public final class AccountData {
    private AccountData() {}

    public record Registration(String name, String email, String password) {}
    public record SignedIn(String role, String name, Long customerId) {}
    public record Session(SignedIn account, boolean demo) {}
}
