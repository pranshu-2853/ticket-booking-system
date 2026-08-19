-- Durable payment-idempotency ledger. PostgreSQL is the source of truth for
-- payment deduplication; the UNIQUE constraint on payment_idempotency_key is the
-- concurrency arbiter that guarantees a single payment execution per key. Redis
-- only caches the SUCCESS result for fast lookup.
--
-- No booking_id column: in the reserve -> pay -> confirm flow the booking row is
-- created in T2, AFTER payment, so a booking id is not available when the
-- INITIATED payment row is written.
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_idempotency_key VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT uq_payment_idem_key UNIQUE (payment_idempotency_key),

    CONSTRAINT fk_payment_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),

    CONSTRAINT fk_payment_seat
        FOREIGN KEY (seat_id)
            REFERENCES seats(id)
);

CREATE INDEX idx_payment_user_id
    ON payments(user_id);
