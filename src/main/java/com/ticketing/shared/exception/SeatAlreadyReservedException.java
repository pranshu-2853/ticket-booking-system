package com.ticketing.shared.exception;

/**
 * Thrown from the reserve step (T1) when the seat is already RESERVED by
 * another in-flight booking request.
 */
public class SeatAlreadyReservedException extends RuntimeException {

    public SeatAlreadyReservedException(String message) {
        super(message);
    }
}
