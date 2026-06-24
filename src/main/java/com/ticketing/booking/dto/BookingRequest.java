package com.ticketing.booking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BookingRequest {

    @NotNull(message = "Seat id is required")
    private Long seatId;
}