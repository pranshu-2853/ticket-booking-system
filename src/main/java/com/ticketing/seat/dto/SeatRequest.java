package com.ticketing.seat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeatRequest {

    @NotBlank(message = "Seat number is required")
    private String seatNumber;
}