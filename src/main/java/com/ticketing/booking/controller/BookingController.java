package com.ticketing.booking.controller;

import com.ticketing.auth.security.JwtUtil;
import com.ticketing.booking.dto.BookingRequest;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final JwtUtil jwtUtil;

    public BookingController(
            BookingService bookingService,
            JwtUtil jwtUtil) {

        this.bookingService = bookingService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader("Authorization") String authHeader) {

        Long userId = jwtUtil.extractUserId(authHeader.substring(7));

        Booking booking = bookingService.createBooking(userId, request.getSeatId());

        BookingResponse response = new BookingResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getSeat().getId(),
                booking.getBookedAt()
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id) {

        Booking booking =
                bookingService.getBookingById(id);

        BookingResponse response =
                new BookingResponse(
                        booking.getId(),
                        booking.getUser().getId(),
                        booking.getSeat().getId(),
                        booking.getBookedAt()
                );

        return ResponseEntity.ok(response);
    }
}