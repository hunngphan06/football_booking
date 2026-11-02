package com.example.footballbooking.exception;

public class MatchNotFoundException extends RuntimeException {
    public MatchNotFoundException(Long id) {
        super("Khong tim thay tran dau co id " + id);
    }
}
