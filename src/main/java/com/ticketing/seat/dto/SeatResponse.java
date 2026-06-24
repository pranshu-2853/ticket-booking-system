package com.ticketing.seat.dto;

import com.ticketing.seat.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeatResponse {

    private Long id;

    private Long eventId;

    private String seatNumber;

    private SeatStatus status;
}