package com.example.footballbooking.exception;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(Long id) {
        super("Khong tim thay tran dau co id " + id);
    }
}
