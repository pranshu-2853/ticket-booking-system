package com.ticketing.seat.repository;

import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByEventId(Long eventId);

    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);

    boolean existsByEventIdAndSeatNumber(Long eventId, String seatNumber);
}