package com.ticketing.booking.service;

import com.ticketing.auth.entity.User;
import com.ticketing.auth.service.UserService;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.service.SeatHoldService;
import com.ticketing.seat.service.SeatService;
import com.ticketing.shared.exception.SeatAlreadyBookedException;
import com.ticketing.shared.exception.SeatAlreadyReservedException;
import com.ticketing.shared.exception.SeatNotHeldByUserException;
import com.ticketing.shared.exception.SeatReservationLostException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Holds the individually-committed transactional units of the booking flow.
 *
 * <p>These live in a separate bean (not on {@code BookingService}) on purpose:
 * {@code Propagation.REQUIRES_NEW} only takes effect when the call goes through
 * the Spring proxy, and a self-invocation from within the same class would
 * bypass it. {@code BookingService} orchestrates these via injection so each
 * step really does run in its own transaction.
 */
@Service
public class BookingTransactionService {

    /** How long a RESERVED seat is held before the sweeper may reclaim it. */
    static final Duration RESERVATION_WINDOW = Duration.ofMinutes(2);

    private final UserService userService;
    private final SeatService seatService;
    private final BookingRepository bookingRepository;
    private final SeatHoldService seatHoldService;

    public BookingTransactionService(
            UserService userService,
            SeatService seatService,
            BookingRepository bookingRepository,
            SeatHoldService seatHoldService) {

        this.userService = userService;
        this.seatService = seatService;
        this.bookingRepository = bookingRepository;
        this.seatHoldService = seatHoldService;
    }

    /**
     * T1: lock the seat, validate it, mark it RESERVED with a deadline, and
     * commit — releasing the row lock before payment runs. Returns the eventId
     * (needed later to release the Redis hold).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long reserveSeat(Long userId, Long seatId) {

        Seat seat = seatService.getSeatWithLock(seatId);

        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new SeatAlreadyBookedException();
        }
        if (seat.getStatus() == SeatStatus.RESERVED) {
            throw new SeatAlreadyReservedException("Seat is reserved by another request");
        }

        Long eventId = seat.getEvent().getId();

        String holder = seatHoldService.getSeatHolder(eventId, seatId);
        if (holder != null && !holder.equals(userId.toString())) {
            throw new SeatNotHeldByUserException("Seat is held by another user");
        }

        seat.setStatus(SeatStatus.RESERVED);
        seat.setReservedUntil(LocalDateTime.now().plus(RESERVATION_WINDOW));

        return eventId;
    }

    /**
     * T2 success path: re-lock the seat, assert it is still RESERVED (guard A),
     * mark it BOOKED, insert the booking, and release the Redis hold.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking confirmBooking(Long userId, Long seatId, Long eventId) {

        Seat seat = seatService.getSeatWithLock(seatId);

        // Guard (A): payment ran with no lock held. The sweeper (or anything
        // else) may have moved the seat off RESERVED in the meantime. Only a
        // still-RESERVED seat may be confirmed; otherwise abort without inserting.
        if (seat.getStatus() != SeatStatus.RESERVED) {
            throw new SeatReservationLostException(
                    "Seat reservation is no longer valid (status=" + seat.getStatus() + ")");
        }

        User user = userService.getUserEntityById(userId);

        seat.setStatus(SeatStatus.BOOKED);
        seat.setReservedUntil(null);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setSeat(seat);

        Booking saved = bookingRepository.save(booking);

        seatHoldService.releaseSeat(eventId, seatId);

        return saved;
    }

    /**
     * T2 failure path: re-lock the seat and free it, but only if it is still the
     * RESERVED seat we set. If it has already moved on (e.g. swept, or somehow
     * BOOKED), leave it untouched.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revertSeat(Long seatId) {

        Seat seat = seatService.getSeatWithLock(seatId);

        if (seat.getStatus() == SeatStatus.RESERVED) {
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setReservedUntil(null);
        }
    }
}
