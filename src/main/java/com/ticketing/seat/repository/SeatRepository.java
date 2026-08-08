package com.ticketing.seat.repository;

import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventId(Long eventId);

    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);

    boolean existsByEventIdAndSeatNumber(Long eventId, String seatNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT s
       FROM Seat s
       WHERE s.id = :seatId
       """)
    Optional<Seat> findByIdWithLock(
            @Param("seatId") Long seatId
    );

    /**
     * Reclaims expired reservations in a single conditional UPDATE. The statement
     * takes the row lock and re-checks status = RESERVED atomically, so if a
     * confirm (T2) holds the row it blocks here and then matches zero rows once
     * T2 has set BOOKED. This is the sweeper side of the sweeper/T2 race guard.
     */
    @Modifying
    @Query("""
       UPDATE Seat s
       SET s.status = com.ticketing.seat.entity.SeatStatus.AVAILABLE,
           s.reservedUntil = null
       WHERE s.status = com.ticketing.seat.entity.SeatStatus.RESERVED
         AND s.reservedUntil < :now
       """)
    int revertExpiredReservations(@Param("now") LocalDateTime now);
}