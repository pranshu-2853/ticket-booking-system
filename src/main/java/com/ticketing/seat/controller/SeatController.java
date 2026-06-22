package com.ticketing.seat.controller;

import com.ticketing.auth.security.JwtUtil;
import com.ticketing.seat.dto.SeatHoldRequest;
import com.ticketing.seat.dto.SeatHoldResponse;
import com.ticketing.seat.dto.SeatRequest;

import com.ticketing.seat.dto.SeatResponse;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.service.SeatHoldService;
import com.ticketing.seat.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
public class SeatController {

    private final SeatService seatService;
    private final SeatHoldService seatHoldService;
    private final JwtUtil jwtUtil;

    public SeatController(SeatService seatService,SeatHoldService seatHoldService,
                          JwtUtil jwtUtil) {
        this.seatService = seatService;
        this.seatHoldService = seatHoldService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/{eventId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public SeatResponse addSeat(
            @PathVariable Long eventId,
            @Valid @RequestBody SeatRequest request) {

        Seat seat = seatService.addSeat(
                eventId,
                request.getSeatNumber()
        );

        return mapToResponse(seat);
    }

    @GetMapping("/{eventId}/seats")
    public List<SeatResponse> getSeatsByEvent(
            @PathVariable Long eventId) {

        return seatService.getSeatsByEvent(eventId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @PostMapping("/hold")
    public ResponseEntity<SeatHoldResponse> holdSeat(
            @Valid @RequestBody SeatHoldRequest request,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);

        Long userId = jwtUtil.extractUserId(token);

        boolean held = seatHoldService.tryHoldSeat(
                request.getEventId(),
                request.getSeatId(),
                userId
        );

        if (held) {
            return ResponseEntity.ok(
                    new SeatHoldResponse(
                            true,
                            "Seat held successfully"
                    )
            );
        }

        return ResponseEntity.badRequest()
                .body(
                        new SeatHoldResponse(
                                false,
                                "Seat is already held"
                        )
                );
    }

    private SeatResponse mapToResponse(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getEvent().getId(),
                seat.getSeatNumber(),
                seat.getStatus()
        );
    }

}