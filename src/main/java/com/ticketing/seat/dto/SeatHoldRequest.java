package com.ticketing.seat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeatHoldRequest {

    @NotNull(message = "Event id is required")
    private Long eventId;

    @NotNull(message = "Seat id is required")
    private Long seatId;
}