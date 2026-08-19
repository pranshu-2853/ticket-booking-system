package com.ticketing.booking.controller;

import com.ticketing.auth.security.JwtUtil;
import com.ticketing.booking.dto.BookingRequest;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.service.BookingService;
import com.ticketing.booking.service.IdempotencyService;
import com.ticketing.shared.exception.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final JwtUtil jwtUtil;
    private final IdempotencyService idempotencyService;

    public BookingController(
            BookingService bookingService,
            JwtUtil jwtUtil,
            IdempotencyService idempotencyService) {

        this.bookingService = bookingService;
        this.jwtUtil = jwtUtil;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey) {

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new BadRequestException("Invalid Authorization header");
                }

        Long userId = jwtUtil.extractUserId(authHeader.substring(7));

        if (idempotencyKey != null) {

            Optional<BookingResponse> cached =
                    idempotencyService.getCachedResponse(
                            userId,
                            idempotencyKey
                    );

            if (cached.isPresent()) {
                log.info("Returning cached booking response for idempotency key: {}", idempotencyKey);
                // Replay must be identical to the original response, which is 201 CREATED.
                return new ResponseEntity<>(cached.get(), HttpStatus.CREATED);
            }
        }

        // Payment idempotency key, derived from the booking Idempotency-Key so it
        // is stable across retries of the same booking request. When the client
        // sends no Idempotency-Key we fall back to a per-attempt server UUID —
        // cross-HTTP-request payment dedup then requires a client-supplied key.
        String paymentKey = (idempotencyKey != null)
                ? userId + ":" + idempotencyKey
                : UUID.randomUUID().toString();

        Booking booking = bookingService.createBooking(userId, request.getSeatId(), paymentKey);

        BookingResponse response = new BookingResponse(
                booking.getId(),
                booking.getUser().getId(),
                booking.getSeat().getId(),
                booking.getBookedAt()
        );

        if (idempotencyKey != null) {

            idempotencyService.saveResponse(
                    userId,
                    idempotencyKey,
                    response
            );
        }

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