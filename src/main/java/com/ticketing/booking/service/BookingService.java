package com.ticketing.booking.service;

import com.ticketing.auth.entity.User;
import com.ticketing.auth.service.UserService;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.service.SeatService;
import com.ticketing.shared.exception.PaymentFailedException;
import com.ticketing.shared.exception.ResourceNotFoundException;
import com.ticketing.shared.exception.SeatAlreadyBookedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ticketing.seat.service.SeatHoldService;
import com.ticketing.shared.exception.SeatNotHeldByUserException;


@Service

public class BookingService {

    private final UserService userService;
    private final SeatService seatService;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final SeatHoldService seatHoldService;

    public BookingService(
            UserService userService,
            SeatService seatService,
            BookingRepository bookingRepository,
            PaymentService paymentService,
            SeatHoldService seatHoldService) {


        this.userService = userService;
        this.seatService = seatService;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
        this.seatHoldService = seatHoldService;
    }

    @Transactional
    public Booking createBooking(Long userId, Long seatId) {

        // 1. DB lock — must be first, gives you the seat entity
        Seat seat = seatService.getSeatWithLock(seatId);

        // 2. Immediate BOOKED check — return fast, no further work needed
        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new SeatAlreadyBookedException();
        }

        // 3. Redis ownership check — needs eventId from seat
        Long eventId = seat.getEvent().getId();
        String holder = seatHoldService.getSeatHolder(eventId, seatId);
        if (holder != null && !holder.equals(userId.toString())) {
            throw new SeatNotHeldByUserException("Seat is held by another user");
        }

        // 4. Payment
        boolean paid = paymentService.process();
        if (!paid) {
            throw new PaymentFailedException();
        }

        // 5. All checks passed — do the actual work
        User user = userService.getUserEntityById(userId);
        seat.setStatus(SeatStatus.BOOKED);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setSeat(seat);

        Booking saved = bookingRepository.save(booking);  // if this throws, nothing below runs

        // 6. Release Redis ONLY after save succeeds
        seatHoldService.releaseSeat(
                eventId,
                seatId
        );

        return saved;
    }

    public Booking getBookingById(Long bookingId) {

        return bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Booking not found with id: " + bookingId
                        ));
    }
}