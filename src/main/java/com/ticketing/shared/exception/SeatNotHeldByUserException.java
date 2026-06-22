package com.ticketing.shared.exception;

public class SeatNotHeldByUserException extends RuntimeException {

    public SeatNotHeldByUserException(String message) {
        super(message);
    }
}