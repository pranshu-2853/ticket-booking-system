package com.ticketing.seat.controller;

import com.ticketing.seat.dto.SeatRequest;

import com.ticketing.seat.dto.SeatResponse;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.service.SeatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
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

    private SeatResponse mapToResponse(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getEvent().getId(),
                seat.getSeatNumber(),
                seat.getStatus()
        );
    }
}