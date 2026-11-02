package com.example.footballbooking.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MatchNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(MatchNotFoundException exception,
                                                  HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidId(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Id tran dau phai la so nguyen hop le", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status).body(
            new ApiError(Instant.now(), status.value(), message, request.getRequestURI()));
    }
}
