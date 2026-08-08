package com.ticketing.booking.service;

import com.ticketing.seat.repository.SeatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Reclaims seats left stuck in RESERVED — for example if the app crashed
 * between T1 (reserve) and T2 (confirm/revert), the seat would otherwise sit
 * RESERVED forever. Without this sweeper the reserve-before-pay design would
 * leak inventory.
 */
@Component
@Slf4j
public class ReservationSweeper {

    private final SeatRepository seatRepository;

    public ReservationSweeper(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    /**
     * Reverts every RESERVED seat whose reserved_until is in the past.
     *
     * <p>The reclaim is a single conditional UPDATE
     * ({@code SeatRepository.revertExpiredReservations}) that takes the row lock
     * and re-checks {@code status = RESERVED} atomically. That is the sweeper
     * side of the sweeper/T2 race guard: if a confirm (T2) is holding the row,
     * this UPDATE blocks on it and then matches zero rows because T2 will have
     * set the seat BOOKED; if the sweeper commits first, T2's re-check finds the
     * seat AVAILABLE and aborts with SeatReservationLostException.
     */
    @Scheduled(
            fixedDelayString = "${reservation.sweeper.fixed-delay-ms:60000}",
            initialDelayString = "${reservation.sweeper.initial-delay-ms:60000}")
    @Transactional
    public void sweepExpiredReservations() {

        int reverted =
                seatRepository.revertExpiredReservations(LocalDateTime.now());

        if (reverted > 0) {
            log.warn(
                    "Reservation sweeper reclaimed {} expired RESERVED seat(s) to AVAILABLE",
                    reverted);
        }
    }
}
