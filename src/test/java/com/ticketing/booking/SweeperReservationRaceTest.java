package com.ticketing.booking;

import com.ticketing.auth.entity.User;
import com.ticketing.booking.service.BookingTransactionService;
import com.ticketing.booking.service.ReservationSweeper;
import com.ticketing.booking.support.AbstractBookingIntegrationTest;
import com.ticketing.event.entity.Event;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.shared.exception.SeatReservationLostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the sweeper/T2 (confirm) race. A truly concurrent, wall-clock
 * interleaving of the sweeper's conditional UPDATE against T2's locked confirm
 * cannot be forced deterministically here without instrumenting the service
 * with latches — so rather than write a test that passes by timing luck, this
 * drives BOTH possible outcomes of the race deterministically in sequence:
 *
 * <ul>
 *   <li>sweeper wins first  -> T2 must abort (no booking);</li>
 *   <li>T2 wins first        -> sweeper must be a no-op (booking stands).</li>
 * </ul>
 *
 * These two cover every observable result of the race; the row lock + the
 * {@code WHERE status = RESERVED} re-check are what make only these two outcomes
 * possible.
 */
class SweeperReservationRaceTest extends AbstractBookingIntegrationTest {

    @Autowired
    private BookingTransactionService bookingTransactionService;

    @Autowired
    private ReservationSweeper reservationSweeper;

    private Long seatId;
    private Long eventId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Event event = createEvent();
        eventId = event.getId();
        Seat seat = createSeat(event);
        seatId = seat.getId();
        User user = createUser();
        userId = user.getId();
    }

    private void expireReservation() {
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seat.setReservedUntil(LocalDateTime.now().minusMinutes(1));
        seatRepository.saveAndFlush(seat);
    }

    @Test
    void sweeperWinsFirst_confirmAborts_noBooking() {
        bookingTransactionService.reserveSeat(userId, seatId);
        expireReservation();

        // Sweeper reclaims the expired reservation first.
        reservationSweeper.sweepExpiredReservations();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.AVAILABLE);

        // T2 now runs and must refuse to book a seat that is no longer RESERVED.
        assertThatThrownBy(() -> bookingTransactionService.confirmBooking(userId, seatId, eventId))
                .isInstanceOf(SeatReservationLostException.class);

        assertThat(bookingRepository.countBySeatId(seatId)).isZero();
    }

    @Test
    void confirmWinsFirst_sweeperIsNoOp_bookingStands() {
        bookingTransactionService.reserveSeat(userId, seatId);
        expireReservation();

        // T2 confirms first (status is still RESERVED, so it is allowed).
        bookingTransactionService.confirmBooking(userId, seatId, eventId);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.BOOKED);
        assertThat(bookingRepository.countBySeatId(seatId)).isEqualTo(1);

        // Sweeper runs afterwards: its WHERE status = RESERVED no longer matches,
        // so it must NOT revert the booked seat.
        reservationSweeper.sweepExpiredReservations();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.BOOKED);
        assertThat(bookingRepository.countBySeatId(seatId)).isEqualTo(1);
    }

    @Test
    void sweeperLeavesNonExpiredReservationsAlone() {
        bookingTransactionService.reserveSeat(userId, seatId); // reserved_until = now + window (future)

        reservationSweeper.sweepExpiredReservations();

        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.RESERVED);
    }
}
