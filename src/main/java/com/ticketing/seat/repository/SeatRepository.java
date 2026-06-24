package com.ticketing.seat.repository;

import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}