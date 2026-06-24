package com.ticketing.shared.exception;

public class SeatAlreadyBookedException
        extends RuntimeException {

    public SeatAlreadyBookedException() {
        super("Seat is already booked");
    }
}