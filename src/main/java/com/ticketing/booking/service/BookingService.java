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

@Service
public class BookingService {

    private final UserService userService;
    private final SeatService seatService;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;

    public BookingService(
            UserService userService,
            SeatService seatService,
            BookingRepository bookingRepository,
            PaymentService paymentService) {

        this.userService = userService;
        this.seatService = seatService;
        this.bookingRepository = bookingRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    public Booking createBooking(
            Long userId,
            Long seatId) {

        Seat seat = seatService.getSeatWithLock(seatId);

        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new SeatAlreadyBookedException();
        }

        boolean paid = paymentService.process();

        if (!paid) {
            throw new PaymentFailedException();
        }

        User user = userService.getUserEntityById(userId);

        seat.setStatus(SeatStatus.BOOKED);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setSeat(seat);

        return bookingRepository.save(booking);
    }

    public Booking getBookingById(Long bookingId) {

        return bookingRepository.findById(bookingId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Booking not found with id: " + bookingId
                        ));
    }
}