package com.ticketing.shared.exception;

/**
 * Thrown from the confirm step (T2) when the seat is no longer in the RESERVED
 * state the reservation left it in — e.g. the sweeper reclaimed it to AVAILABLE
 * while payment was running, or it was otherwise changed. The booking is aborted
 * rather than inserted.
 */
public class SeatReservationLostException extends RuntimeException {

    public SeatReservationLostException(String message) {
        super(message);
    }
}
