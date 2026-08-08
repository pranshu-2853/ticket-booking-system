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
import com.ticketing.shared.exception.SeatAlreadyBookedException;
import com.ticketing.shared.exception.SeatAlreadyReservedException;
import com.ticketing.shared.exception.SeatNotHeldByUserException;
import com.ticketing.shared.exception.SeatReservationLostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingTransactionServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private SeatService seatService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatHoldService seatHoldService;

    @InjectMocks
    private BookingTransactionService service;

    // ---------- reserveSeat (T1) ----------

    @Test
    void reserveSeat_shouldMarkReservedWithDeadline_whenAvailableAndHeldBySameUser() {
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;
        Seat seat = buildSeat(SeatStatus.AVAILABLE, eventId);

        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);
        when(seatHoldService.getSeatHolder(eventId, seatId)).thenReturn(userId.toString());

        Long returnedEventId = service.reserveSeat(userId, seatId);

        assertEquals(eventId, returnedEventId);
        assertEquals(SeatStatus.RESERVED, seat.getStatus());
        assertNotNull(seat.getReservedUntil());
    }

    @Test
    void reserveSeat_shouldSucceed_whenNoHolderExists() {
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;
        Seat seat = buildSeat(SeatStatus.AVAILABLE, eventId);

        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);
        when(seatHoldService.getSeatHolder(eventId, seatId)).thenReturn(null);

        service.reserveSeat(userId, seatId);

        assertEquals(SeatStatus.RESERVED, seat.getStatus());
    }

    @Test
    void reserveSeat_shouldSucceed_whenRedisThrowsDuringHolderCheck() {
        // A real SeatHoldService whose Redis GET blows up must fail open (holder
        // treated as null), so the reservation still proceeds. Preserves the
        // item-2 guarantee now that the holder check lives in reserveSeat.
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;
        Seat seat = buildSeat(SeatStatus.AVAILABLE, eventId);

        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.RedisTemplate.class);
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenThrow(new RuntimeException("redis down"));

        com.ticketing.seat.service.SeatHoldService realHoldService =
                new com.ticketing.seat.service.SeatHoldService(redisTemplate);
        BookingTransactionService serviceWithRealHold =
                new BookingTransactionService(userService, seatService, bookingRepository, realHoldService);

        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);

        Long returnedEventId = serviceWithRealHold.reserveSeat(userId, seatId);

        assertEquals(eventId, returnedEventId);
        assertEquals(SeatStatus.RESERVED, seat.getStatus());
    }

    @Test
    void reserveSeat_shouldThrowSeatAlreadyBooked_whenBooked() {
        Long seatId = 201L;
        Seat seat = buildSeat(SeatStatus.BOOKED, 301L);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);

        assertThrows(SeatAlreadyBookedException.class, () -> service.reserveSeat(101L, seatId));
        verifyNoInteractions(seatHoldService);
    }

    @Test
    void reserveSeat_shouldThrowSeatAlreadyReserved_whenAlreadyReserved() {
        Long seatId = 201L;
        Seat seat = buildSeat(SeatStatus.RESERVED, 301L);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);

        assertThrows(SeatAlreadyReservedException.class, () -> service.reserveSeat(101L, seatId));
        verifyNoInteractions(seatHoldService);
    }

    @Test
    void reserveSeat_shouldThrowSeatNotHeld_whenHeldByAnotherUser() {
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;
        Seat seat = buildSeat(SeatStatus.AVAILABLE, eventId);

        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);
        when(seatHoldService.getSeatHolder(eventId, seatId)).thenReturn("999");

        assertThrows(SeatNotHeldByUserException.class, () -> service.reserveSeat(userId, seatId));
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
    }

    // ---------- confirmBooking (T2) ----------

    @Test
    void confirmBooking_shouldBookAndReleaseHold_whenStillReserved() {
        Long userId = 101L;
        Long seatId = 201L;
        Long eventId = 301L;
        Seat seat = buildSeat(SeatStatus.RESERVED, eventId);
        seat.setReservedUntil(java.time.LocalDateTime.now().plusMinutes(2));
        User user = org.mockito.Mockito.mock(User.class);
        Booking saved = new Booking();

        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);
        when(userService.getUserEntityById(userId)).thenReturn(user);
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        Booking result = service.confirmBooking(userId, seatId, eventId);

        assertSame(saved, result);
        assertEquals(SeatStatus.BOOKED, seat.getStatus());
        assertNull(seat.getReservedUntil());
        verify(seatHoldService).releaseSeat(eventId, seatId);
    }

    @Test
    void confirmBooking_shouldThrowReservationLost_whenSeatAvailableAgain() {
        // Sweeper won the race: seat reverted to AVAILABLE before confirm.
        Long seatId = 201L;
        Seat seat = buildSeat(SeatStatus.AVAILABLE, 301L);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);

        assertThrows(SeatReservationLostException.class,
                () -> service.confirmBooking(101L, seatId, 301L));

        verify(bookingRepository, never()).save(any(Booking.class));
        verify(seatHoldService, never()).releaseSeat(any(), any());
    }

    @Test
    void confirmBooking_shouldThrowReservationLost_whenSeatAlreadyBooked() {
        Long seatId = 201L;
        Seat seat = buildSeat(SeatStatus.BOOKED, 301L);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);

        assertThrows(SeatReservationLostException.class,
                () -> service.confirmBooking(101L, seatId, 301L));

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    // ---------- revertSeat ----------

    @Test
    void revertSeat_shouldSetAvailable_whenReserved() {
        Long seatId = 201L;
        Seat seat = buildSeat(SeatStatus.RESERVED, 301L);
        seat.setReservedUntil(java.time.LocalDateTime.now());
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);

        service.revertSeat(seatId);

        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        assertNull(seat.getReservedUntil());
    }

    @Test
    void revertSeat_shouldLeaveBookedUntouched() {
        Long seatId = 201L;
        Seat seat = buildSeat(SeatStatus.BOOKED, 301L);
        when(seatService.getSeatWithLock(seatId)).thenReturn(seat);

        service.revertSeat(seatId);

        assertEquals(SeatStatus.BOOKED, seat.getStatus());
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
