package com.example.footballbooking.service;

import com.example.footballbooking.dto.BookingData.Booking;
import com.example.footballbooking.dto.BookingData.BookingRequest;
import com.example.footballbooking.dto.BookingData.Customer;
import com.example.footballbooking.dto.BookingData.Item;
import com.example.footballbooking.dto.BookingData.ItemRequest;
import com.example.footballbooking.exception.BookingException;
import com.example.footballbooking.exception.InvalidRequestException;
import com.example.footballbooking.repository.BookingRepository;
import com.example.footballbooking.repository.PaymentRepository;
import com.example.footballbooking.dto.PaymentData.Status;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingServiceImpl implements BookingService {
    private final BookingRepository repository;

    private final PaymentRepository payments;
    private final boolean demo;

    public BookingServiceImpl(BookingRepository repository, PaymentRepository payments,
                              @Value("${booking.demo.enabled:false}") boolean demo) {
        this.repository = repository;
        this.payments = payments;
        this.demo = demo;
    }

    @Override
    @Transactional
    public Customer register(String name, String email) {
        String customerName = requiredText(name, "name");
        String address = requiredText(email, "email").toLowerCase(Locale.ROOT);
        if (!address.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new InvalidRequestException("Email format is invalid");
        }
        try {
            return new Customer(repository.insertCustomer(customerName, address), customerName, address);
        } catch (DuplicateKeyException ex) {
            throw new BookingException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Booking> customerBookings(long id) {
        requireCustomer(id);
        return repository.findCustomerBookingIds(id).stream().map(this::get).toList();
    }

    @Override
    @Transactional
    public Booking create(BookingRequest request) {
        if (request.customerId() == null || request.eventId() == null ||
            request.customerId() <= 0 || request.eventId() <= 0) {
            throw new InvalidRequestException("customerId and eventId must be positive");
        }
        if (request.items() == null || request.items().isEmpty() || request.items().size() > 20) {
            throw new InvalidRequestException("Provide between 1 and 20 ticket types");
        }
        // Always update inventory in ID order to avoid conflicting lock orders.
        Map<Long, Integer> quantities = new TreeMap<>();
        for (ItemRequest item : request.items()) {
            if (item == null || item.ticketTypeId() == null || item.ticketTypeId() <= 0 ||
                item.quantity() == null || item.quantity() <= 0) {
                throw new InvalidRequestException("ticketTypeId and quantity must be positive");
            }
            if (quantities.putIfAbsent(item.ticketTypeId(), item.quantity()) != null) {
                throw new InvalidRequestException("List each ticket type only once");
            }
        }
        requireCustomer(request.customerId());
        boolean saleOpen = repository.lockEventSales(request.eventId())
            .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "Event does not exist"));
        if (!saleOpen) {
            throw new BookingException(HttpStatus.CONFLICT, "Ticket sales are closed for this event");
        }
        List<Item> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            long ticketId = entry.getKey();
            int quantity = entry.getValue();
            var ticket = repository.findTicket(ticketId)
                .orElseThrow(() -> new BookingException(HttpStatus.NOT_FOUND, "Ticket type does not exist"));
            if (ticket.matchId() != request.eventId()) {
                throw new InvalidRequestException("All ticket types must belong to the selected event");
            }
            if (!repository.reserve(ticketId, quantity)) {
                throw new BookingException(HttpStatus.CONFLICT, "Insufficient inventory for ticket type " + ticketId);
            }
            items.add(new Item(ticketId, quantity, ticket.price()));
            total = total.add(ticket.price().multiply(BigDecimal.valueOf(quantity)));
        }
        long id = repository.insertBooking(UUID.randomUUID().toString(), request.customerId(), request.eventId(), total);
        for (Item item : items) {
            repository.insertItem(id, item);
        }
        return get(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Booking get(long id) {
        return repository.findBooking(id).orElseThrow(this::missingBooking);
    }

    @Override
    @Transactional
    public Booking cancel(long id) {
        String status = repository.lockStatus(id).orElseThrow(this::missingBooking);
        var payment = payments.find(id);
        if (payment.isPresent() && payment.get().status() == Status.PENDING) {
            throw new BookingException(HttpStatus.CONFLICT, "Reconcile the pending payment before cancelling");
        }
        if (!"CANCELLED".equals(status)) {
            for (Item item : repository.findItems(id)) {
                repository.release(item.ticketTypeId(), item.quantity());
            }
            repository.updateStatus(id, "CANCELLED");
        }
        if (payment.isPresent() && payment.get().status() == Status.SUCCEEDED) {
            if (demo) {
                payments.updateStatus(id, Status.REFUND_PENDING);
            } else {
                payments.recordRefund(id);
            }
        }
        return get(id);
    }

    private BookingException missingBooking() {
        return new BookingException(HttpStatus.NOT_FOUND, "Booking does not exist");
    }

    private void requireCustomer(long id) {
        if (!repository.customerExists(id)) {
            throw new BookingException(HttpStatus.NOT_FOUND, "Customer does not exist");
        }
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank() || value.strip().length() > 255) {
            throw new InvalidRequestException(field + " must contain between 1 and 255 characters");
        }
        return value.strip();
    }
}
