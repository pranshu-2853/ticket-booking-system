package com.ticketing.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeatHoldResponse {

    private boolean success;

    private String message;
}