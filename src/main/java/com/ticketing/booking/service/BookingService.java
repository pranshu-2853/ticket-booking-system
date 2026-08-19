package com.ticketing.booking.service;

import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private final IdempotentPaymentService idempotentPaymentService;
    private final BookingTransactionService bookingTransactionService;
    private final BookingRepository bookingRepository;

    public BookingService(
            IdempotentPaymentService idempotentPaymentService,
            BookingTransactionService bookingTransactionService,
            BookingRepository bookingRepository) {

        this.idempotentPaymentService = idempotentPaymentService;
        this.bookingTransactionService = bookingTransactionService;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Orchestrates the booking flow across three separately-committed steps so
     * the pessimistic seat lock is NOT held across payment:
     *
     * <ol>
     *   <li>T1 {@code reserveSeat}: lock, validate, mark RESERVED, commit (lock released).</li>
     *   <li>Payment: runs here with no lock and no open transaction, now made durably
     *       idempotent via {@link IdempotentPaymentService} keyed by {@code paymentKey}.</li>
     *   <li>T2 {@code confirmBooking}: re-lock, re-check RESERVED, mark BOOKED, insert booking.</li>
     * </ol>
     *
     * If payment throws, the reserved seat is reverted immediately (a try/finally
     * so the revert also runs on {@code PaymentServiceUnavailableException});
     * otherwise the sweeper would eventually reclaim it.
     *
     * <p>This method is intentionally NOT {@code @Transactional}: each step above
     * manages its own transaction via {@link BookingTransactionService} /
     * {@link IdempotentPaymentService}.
     *
     * @param paymentKey the payment idempotency key, derived by the controller from
     *                   the booking Idempotency-Key so it is stable across retries.
     */
    public Booking createBooking(Long userId, Long seatId, String paymentKey) {

        // T1 — reserve and release the row lock before touching payment.
        Long eventId = bookingTransactionService.reserveSeat(userId, seatId);

        // Payment — no lock held, no open transaction. Durably idempotent per
        // paymentKey; returns true on success or throws (its Resilience4j fallback
        // throws PaymentServiceUnavailableException).
        boolean paymentSucceeded = false;
        try {
            idempotentPaymentService.pay(paymentKey, userId, seatId);
            paymentSucceeded = true;
        } finally {
            if (!paymentSucceeded) {
                bookingTransactionService.revertSeat(seatId);
            }
        }

        // T2 — confirm in a fresh transaction, re-checking the reservation holds.
        return bookingTransactionService.confirmBooking(userId, seatId, eventId);
    }

    public Booking getBookingById(Long bookingId) {

        return bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Booking not found with id: " + bookingId
                        ));
    }
}
