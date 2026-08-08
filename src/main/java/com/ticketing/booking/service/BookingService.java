package com.ticketing.booking.service;

import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    private final PaymentService paymentService;
    private final BookingTransactionService bookingTransactionService;
    private final BookingRepository bookingRepository;

    public BookingService(
            PaymentService paymentService,
            BookingTransactionService bookingTransactionService,
            BookingRepository bookingRepository) {

        this.paymentService = paymentService;
        this.bookingTransactionService = bookingTransactionService;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Orchestrates the booking flow across three separately-committed steps so
     * the pessimistic seat lock is NOT held across payment:
     *
     * <ol>
     *   <li>T1 {@code reserveSeat}: lock, validate, mark RESERVED, commit (lock released).</li>
     *   <li>Payment: runs here with no lock and no open transaction.</li>
     *   <li>T2 {@code confirmBooking}: re-lock, re-check RESERVED, mark BOOKED, insert booking.</li>
     * </ol>
     *
     * If payment throws, the reserved seat is reverted immediately (a try/finally
     * so the revert also runs on {@code PaymentServiceUnavailableException});
     * otherwise the sweeper would eventually reclaim it.
     *
     * <p>This method is intentionally NOT {@code @Transactional}: each step above
     * manages its own transaction via {@link BookingTransactionService}.
     */
    public Booking createBooking(Long userId, Long seatId) {

        // T1 — reserve and release the row lock before touching payment.
        Long eventId = bookingTransactionService.reserveSeat(userId, seatId);

        // Payment — no lock held, no open transaction. process() returns true or
        // throws (its Resilience4j fallback throws PaymentServiceUnavailableException);
        // it never returns false, so there is no unreachable "payment failed" branch here.
        boolean paymentSucceeded = false;
        try {
            paymentService.process();
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
