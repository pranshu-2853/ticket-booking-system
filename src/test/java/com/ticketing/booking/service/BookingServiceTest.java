package com.ticketing.booking.service;

import com.ticketing.auth.entity.User;
import com.ticketing.auth.service.UserService;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.event.entity.Event;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.service.SeatHoldService;
import com.ticketing.seat.service.SeatService;
import com.ticketing.shared.exception.PaymentFailedException;
import com.ticketing.shared.exception.ResourceNotFoundException;
import com.ticketing.shared.exception.SeatAlreadyBookedException;
import com.ticketing.shared.exception.SeatNotHeldByUserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private SeatService seatService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private SeatHoldService seatHoldService;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBooking_shouldCreateBookingAndReleaseSeatHold_whenValidRequest() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;

        Seat seat = buildSeat(SeatStatus.AVAILABLE, eventId);
        User user = org.mockito.Mockito.mock(User.class);
        Booking savedBooking = new Booking();

        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);
        when(seatHoldService.getSeatHolder(eventId, seatId)).thenReturn(userId.toString());
        when(paymentService.process()).thenReturn(true);
        when(userService.getUserEntityById(userId)).thenReturn(user);
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        // Act
        Booking result = bookingService.createBooking(userId, seatId);

        // Assert
        assertSame(savedBooking, result);
        assertEquals(SeatStatus.BOOKED, seat.getStatus());

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        assertSame(user, bookingCaptor.getValue().getUser());
        assertSame(seat, bookingCaptor.getValue().getSeat());

        inOrder(bookingRepository, seatHoldService)
                .verify(bookingRepository)
                .save(any(Booking.class));
        inOrder(bookingRepository, seatHoldService)
                .verify(seatHoldService)
                .releaseSeat(eventId, seatId);
    }

    @Test
    void createBooking_shouldThrowSeatAlreadyBooked_whenSeatAlreadyBooked() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;

        Seat seat = buildSeat(SeatStatus.BOOKED, 301L);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);

        // Act
        assertThrows(SeatAlreadyBookedException.class, () -> bookingService.createBooking(userId, seatId));

        // Assert
        verify(seatHoldService, never()).getSeatHolder(any(), any());
        verifyNoInteractions(paymentService, userService, bookingRepository);
        verify(seatHoldService, never()).releaseSeat(any(), any());
    }

    @Test
    void createBooking_shouldThrowPaymentFailed_whenPaymentReturnsFalse() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;

        Seat seat = buildSeat(SeatStatus.AVAILABLE, eventId);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);
        when(seatHoldService.getSeatHolder(eventId, seatId)).thenReturn(userId.toString());
        when(paymentService.process()).thenReturn(false);

        // Act
        assertThrows(PaymentFailedException.class, () -> bookingService.createBooking(userId, seatId));

        // Assert
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        verifyNoInteractions(userService, bookingRepository);
        verify(seatHoldService, never()).releaseSeat(any(), any());
    }

    @Test
    void createBooking_shouldThrowSeatNotHeldByUser_whenSeatIsHeldByAnotherUser() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;

        Seat seat = buildSeat(SeatStatus.AVAILABLE, eventId);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);
        when(seatHoldService.getSeatHolder(eventId, seatId)).thenReturn("999");

        // Act
        assertThrows(SeatNotHeldByUserException.class, () -> bookingService.createBooking(userId, seatId));

        // Assert
        verifyNoInteractions(paymentService, userService, bookingRepository);
        verify(seatHoldService, never()).releaseSeat(any(), any());
    }

    @Test
    void createBooking_shouldThrowResourceNotFound_whenSeatDoesNotExist() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;

        when(seatService.getSeatWithLock(seatId)).thenThrow(new ResourceNotFoundException("Seat not found"));

        // Act
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(userId, seatId));

        // Assert
        verifyNoInteractions(seatHoldService, paymentService, userService, bookingRepository);
    }

    @Test
    void createBooking_shouldThrowResourceNotFound_whenUserDoesNotExist() {
        // Arrange
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;

        Seat seat = buildSeat(SeatStatus.AVAILABLE, eventId);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);
        when(seatHoldService.getSeatHolder(eventId, seatId)).thenReturn(null);
        when(paymentService.process()).thenReturn(true);
        when(userService.getUserEntityById(userId)).thenThrow(new ResourceNotFoundException("User not found"));

        // Act
        assertThrows(ResourceNotFoundException.class, () -> bookingService.createBooking(userId, seatId));

        // Assert
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(seatHoldService, never()).releaseSeat(any(), any());
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

    private Seat buildSeat(SeatStatus status, Long eventId) {
        Event event = new Event();
        event.setId(eventId);

        Seat seat = new Seat();
        seat.setStatus(status);
        seat.setEvent(event);
        return seat;
    }
}
