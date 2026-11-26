package com.example.footballbooking.controller;

import com.example.footballbooking.dto.BookingData.Booking;
import com.example.footballbooking.dto.BookingData.BookingRequest;
import com.example.footballbooking.dto.PaymentData.Payment;
import com.example.footballbooking.dto.PaymentData.PaymentRequest;
import com.example.footballbooking.dto.PaymentData.Result;
import com.example.footballbooking.dto.PaymentData.Scenario;
import com.example.footballbooking.dto.PaymentData.Status;
import com.example.footballbooking.exception.BookingException;
import com.example.footballbooking.service.BookingService;
import com.example.footballbooking.service.PaymentService;
import com.example.footballbooking.security.AccountPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import com.example.footballbooking.service.ConfirmationService;
import org.springframework.http.MediaType;
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
    private final ConfirmationService confirmations;

    private final PaymentService payments;
    private final boolean demo;
    private final boolean paymentScenariosEnabled;

    public BookingController(BookingService service, ConfirmationService confirmations, PaymentService payments,
                             @Value("${booking.demo.enabled:false}") boolean demo,
                             @Value("${booking.demo.payment-scenarios-enabled:false}") boolean paymentScenariosEnabled) {
        this.service = service;
        this.confirmations = confirmations;
        this.payments = payments;
        this.demo = demo;
        this.paymentScenariosEnabled = paymentScenariosEnabled;
    }

    @GetMapping("/me/bookings")
    public List<Booking> myBookings(@AuthenticationPrincipal AccountPrincipal account) {
        return service.customerBookings(account.customerId());
    }

    @GetMapping("/customers/{id}/bookings")
    public List<Booking> customerBookings(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal account) {
        if (!account.customerId().equals(id)) {
            throw new BookingException(HttpStatus.NOT_FOUND, "Customer does not exist");
        }
        return service.customerBookings(id);
    }

    @PostMapping("/bookings")
    public ResponseEntity<Booking> create(@RequestBody BookingRequest request,
                                          @AuthenticationPrincipal AccountPrincipal account) {
        if (request.customerId() != null && !account.customerId().equals(request.customerId())) {
            throw new BookingException(HttpStatus.FORBIDDEN, "You can only create a booking for your own account");
        }
        Booking booking = service.create(new BookingRequest(account.customerId(), request.eventId(), request.items()));
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.id())).body(booking);
    }

    @GetMapping("/bookings/{id}")
    public Booking get(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal account) {
        return ownBooking(id, account);
    }

    @PostMapping("/bookings/{id}/pay")
    public ResponseEntity<Booking> pay(@PathVariable long id, @RequestBody(required = false) PaymentRequest request,
                                       @AuthenticationPrincipal AccountPrincipal account) {
        ownBooking(id, account);
        Scenario scenario = request == null || request.scenario() == null ? Scenario.SUCCESS : request.scenario();
        if (scenario != Scenario.SUCCESS && (!demo || !paymentScenariosEnabled)) {
            throw new BookingException(HttpStatus.FORBIDDEN, "Kịch bản thanh toán lỗi chỉ dùng khi bật kiểm thử.");
        }
        return paymentResponse(payments.pay(id, scenario));
    }

    @GetMapping("/bookings/{id}/payment")
    public Payment payment(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal account) {
        ownBooking(id, account);
        return payments.get(id);
    }

    @PostMapping("/bookings/{id}/payment/reconcile")
    public ResponseEntity<Booking> reconcile(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal account) {
        ownBooking(id, account);
        requireDemo();
        return paymentResponse(payments.reconcile(id));
    }

    @PostMapping("/bookings/{id}/refund")
    public Payment refund(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal account) {
        ownBooking(id, account);
        requireDemo();
        return payments.refund(id);
    }

    private ResponseEntity<Booking> paymentResponse(Result result) {
        Status status = result.payment().status();
        if (status == Status.FAILED) {
            throw new BookingException(HttpStatus.CONFLICT, "Payment failed before the charge. You can retry.");
        }
        if (status == Status.PENDING || status == Status.REFUND_PENDING) {
            return ResponseEntity.accepted().body(result.booking());
        }
        return ResponseEntity.ok(result.booking());
    }

    @PostMapping("/bookings/{id}/cancel")
    public Booking cancel(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal account) {
        ownBooking(id, account);
        return service.cancel(id);
    }

    @GetMapping(value = "/bookings/{id}/confirmation", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> confirmation(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal account) {
        ownBooking(id, account);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=booking-" + id + ".pdf")
            .body(confirmations.createPdf(id));
    }

    private Booking ownBooking(long id, AccountPrincipal account) {
        Booking booking = service.get(id);
        if (!account.customerId().equals(booking.customerId())) {
            throw new BookingException(HttpStatus.NOT_FOUND, "Booking does not exist");
        }
        return booking;
    }

    private void requireDemo() {
        if (!demo) {
            throw new BookingException(HttpStatus.FORBIDDEN, "This payment scenario is only available in demo mode");
        }
    }
}
