package com.ticketing.booking.entity;

/**
 * Lifecycle of a payment idempotency record.
 *
 * <p>{@code INITIATED} is written BEFORE the (mock) charge runs, establishing the
 * durable identity of the payment operation. Only {@code SUCCESS} short-circuits a
 * repeat call for the same key; {@code INITIATED}/{@code FAILED} are non-terminal
 * for dedup purposes (the UNIQUE key still guarantees a single row).
 */
public enum PaymentStatus {
    INITIATED,
    SUCCESS,
    FAILED
}
