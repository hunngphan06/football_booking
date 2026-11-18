package com.example.footballbooking.controller;

import com.example.footballbooking.dto.BookingData.Booking;
import com.example.footballbooking.dto.BookingData.BookingRequest;
import com.example.footballbooking.dto.BookingData.Customer;
import com.example.footballbooking.dto.BookingData.CustomerRequest;
import com.example.footballbooking.service.BookingService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping("/customers")
    public ResponseEntity<Customer> register(@RequestBody CustomerRequest request) {
        Customer customer = service.register(request);
        return ResponseEntity.status(201).body(customer);
    }

    @GetMapping("/customers/{id}/bookings")
    public List<Booking> customerBookings(@PathVariable long id) {
        return service.customerBookings(id);
    }

    @PostMapping("/bookings")
    public ResponseEntity<Booking> create(@RequestBody BookingRequest request) {
        Booking booking = service.create(request);
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/bookings/{id}")
    public Booking get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/bookings/{id}/pay")
    public Booking pay(@PathVariable long id) {
        return service.pay(id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public Booking cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
