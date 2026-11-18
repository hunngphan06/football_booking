package com.example.footballbooking.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BookingException.class)
    public ResponseEntity<ApiError> handleBooking(BookingException exception, HttpServletRequest request) {
        return error(exception.getStatus(), exception.getMessage(), request);
    }

    @ExceptionHandler(MatchNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(MatchNotFoundException exception,
                                                  HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(MethodArgumentTypeMismatchException exception,
                                                          HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid format for parameter " + exception.getName(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException exception,
                                                        HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleInvalidJson(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request JSON or field format", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status).body(
            new ApiError(Instant.now(), status.value(), message, request.getRequestURI()));
    }
}
