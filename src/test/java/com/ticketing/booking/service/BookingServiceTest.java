package com.ticketing.booking.service;

import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.shared.exception.PaymentServiceUnavailableException;
import com.ticketing.shared.exception.ResourceNotFoundException;
import com.ticketing.shared.exception.SeatAlreadyReservedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BookingService is now a pure orchestrator over three separately-committed
 * steps in BookingTransactionService, with payment in between. These tests pin
 * down that orchestration: ordering, and that a payment failure reverts the
 * reservation before propagating.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private IdempotentPaymentService idempotentPaymentService;

    @Mock
    private BookingTransactionService bookingTransactionService;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBooking_shouldReserveThenPayThenConfirm_onHappyPath() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;
        String paymentKey = "101:idem-1";
        Booking confirmed = new Booking();

        when(bookingTransactionService.reserveSeat(userId, seatId)).thenReturn(eventId);
        when(idempotentPaymentService.pay(paymentKey, userId, seatId)).thenReturn(true);
        when(bookingTransactionService.confirmBooking(userId, seatId, eventId)).thenReturn(confirmed);

        // Act
        Booking result = bookingService.createBooking(userId, seatId, paymentKey);

        // Assert
        assertSame(confirmed, result);

        InOrder order = inOrder(bookingTransactionService, idempotentPaymentService);
        order.verify(bookingTransactionService).reserveSeat(userId, seatId);
        order.verify(idempotentPaymentService).pay(paymentKey, userId, seatId);
        order.verify(bookingTransactionService).confirmBooking(userId, seatId, eventId);

        verify(bookingTransactionService, never()).revertSeat(seatId);
    }

    @Test
    void createBooking_shouldRevertReservation_whenPaymentThrows() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;
        String paymentKey = "101:idem-1";

        when(bookingTransactionService.reserveSeat(userId, seatId)).thenReturn(eventId);
        doThrow(new PaymentServiceUnavailableException("payment down"))
                .when(idempotentPaymentService).pay(paymentKey, userId, seatId);

        // Act
        assertThrows(PaymentServiceUnavailableException.class,
                () -> bookingService.createBooking(userId, seatId, paymentKey));

        // Assert — reservation freed, confirm never attempted
        verify(bookingTransactionService).revertSeat(seatId);
        verify(bookingTransactionService, never()).confirmBooking(userId, seatId, eventId);
    }

    @Test
    void createBooking_shouldNotPayOrConfirm_whenReserveThrows() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;
        String paymentKey = "101:idem-1";

        when(bookingTransactionService.reserveSeat(userId, seatId))
                .thenThrow(new SeatAlreadyReservedException("already reserved"));

        // Act
        assertThrows(SeatAlreadyReservedException.class,
                () -> bookingService.createBooking(userId, seatId, paymentKey));

        // Assert — no payment, no confirm, no revert (nothing was reserved by us)
        verifyNoInteractions(idempotentPaymentService);
        verify(bookingTransactionService, never()).confirmBooking(any(), any(), any());
        verify(bookingTransactionService, never()).revertSeat(any());
    }

    @Test
    void getBookingById_shouldReturnBooking_whenBookingExists() {
        // Arrange
        Long bookingId = 701L;
        Booking booking = new Booking();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        // Act
        Booking result = bookingService.getBookingById(bookingId);

        // Assert
        assertSame(booking, result);
        verify(bookingRepository).findById(bookingId);
    }

    @Test
    void getBookingById_shouldThrowResourceNotFound_whenBookingMissing() {
        // Arrange
        Long bookingId = 701L;
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        // Act
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> bookingService.getBookingById(bookingId)
        );

        // Assert
        assertEquals("Booking not found with id: " + bookingId, ex.getMessage());
        verify(bookingRepository).findById(bookingId);
    }
}
