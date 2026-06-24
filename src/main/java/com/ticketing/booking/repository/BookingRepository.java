package com.ticketing.booking.repository;

import com.ticketing.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    long countBySeatId(Long seatId);
}