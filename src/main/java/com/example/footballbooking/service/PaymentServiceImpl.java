package com.example.footballbooking.service;

import com.example.footballbooking.dto.BookingData.Booking;
import com.example.footballbooking.dto.PaymentData.Payment;
import com.example.footballbooking.dto.PaymentData.Result;
import com.example.footballbooking.dto.PaymentData.Scenario;
import com.example.footballbooking.dto.PaymentData.Status;
import com.example.footballbooking.exception.BookingException;
import com.example.footballbooking.repository.BookingRepository;
import com.example.footballbooking.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentServiceImpl implements PaymentService {
    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final PaymentRepository payments;
    private final TransactionTemplate transaction;

    public PaymentServiceImpl(BookingRepository bookings, BookingService bookingService, PaymentRepository payments,
                              PlatformTransactionManager manager) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.payments = payments;
        this.transaction = new TransactionTemplate(manager);
    }

    @Override
    public Payment get(long bookingId) {
        return payments.find(bookingId).orElseThrow(() ->
            new BookingException(HttpStatus.NOT_FOUND, "Payment does not exist"));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result pay(long bookingId, Scenario scenario) {
        Result result = transaction.execute(tx -> {
            String bookingStatus = lockBooking(bookingId);
            if ("CANCELLED".equals(bookingStatus)) {
                throw conflict("Cancelled booking cannot be paid");
            }

            Booking booking = bookingService.get(bookingId);
            var payment = payments.find(bookingId);
            if (payment.isPresent() && payment.get().status() != Status.FAILED) {
                return new Result(booking, payment.get());
            }
            if (payment.isEmpty()) {
                payments.create(booking, scenario);
            } else {
                payments.retry(bookingId, scenario);
            }
            switch (scenario) {
                case FAIL_BEFORE_CHARGE -> payments.updateStatus(bookingId, Status.FAILED);
                case SUCCESS, FAIL_AFTER_CHARGE -> payments.recordCharge(bookingId);
                case TIMEOUT -> { /* Leave the payment pending until reconciliation. */ }
            }
            return new Result(bookingService.get(bookingId), get(bookingId));
        });
        return confirmChargedBooking(result);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result reconcile(long bookingId) {
        Result result = transaction.execute(tx -> {
            lockBooking(bookingId);
            Payment payment = get(bookingId);
            if (payment.status() == Status.PENDING) {
                // The simulated provider reports that the timed-out charge succeeded.
                payments.recordCharge(bookingId);
                payment = get(bookingId);
            }
            return new Result(bookingService.get(bookingId), payment);
        });
        return confirmChargedBooking(result);
    }

    private Result confirmChargedBooking(Result result) {
        String status = result.booking().status();
        if (result.payment().status() != Status.SUCCEEDED || !("PENDING".equals(status) || "PAID".equals(status))) {
            return result;
        }
        long id = result.booking().id();
        try {
            return transaction.execute(tx -> {
                String bookingStatus = lockBooking(id);
                Payment payment = get(id);
                if (payment.status() == Status.SUCCEEDED && !"CONFIRMED".equals(bookingStatus)
                        && !"CANCELLED".equals(bookingStatus)) {
                    bookings.markPaid(id, payment.chargedAt());
                    if (payment.scenario() == Scenario.FAIL_AFTER_CHARGE) {
                        throw new SimulatedConfirmationFailure();
                    }
                    bookings.updateStatus(id, "CONFIRMED");
                }
                return new Result(bookingService.get(id), payment);
            });
        } catch (SimulatedConfirmationFailure ex) {
            // A permanent confirmation failure is simulated. The previous transaction rolled back.
            return transaction.execute(tx -> new Result(bookingService.cancel(id), get(id)));
        }
    }

    @Override
    public Payment refund(long bookingId) {
        return transaction.execute(tx -> {
            lockBooking(bookingId);
            Payment payment = get(bookingId);
            if (payment.status() == Status.REFUNDED) {
                return payment;
            }
            if (payment.status() != Status.REFUND_PENDING) {
                throw conflict("There is no pending refund for this payment");
            }
            payments.recordRefund(bookingId);
            return get(bookingId);
        });
    }

    private String lockBooking(long id) {
        return bookings.lockStatus(id).orElseThrow(() ->
            new BookingException(HttpStatus.NOT_FOUND, "Booking does not exist"));
    }

    private BookingException conflict(String message) {
        return new BookingException(HttpStatus.CONFLICT, message);
    }

    private static final class SimulatedConfirmationFailure extends RuntimeException {}
}
