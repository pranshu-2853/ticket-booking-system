package com.ticketing.shared.exception;

public class PaymentFailedException
        extends RuntimeException {

    public PaymentFailedException() {
        super("Payment failed");
    }
}