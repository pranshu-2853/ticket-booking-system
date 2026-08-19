package com.ticketing.booking.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Durable, PostgreSQL-backed record of a payment operation, keyed by a UNIQUE
 * {@code payment_idempotency_key}. This is the source of truth for payment
 * idempotency: even if Redis is unavailable, the UNIQUE constraint on the key is
 * what guarantees a single payment execution per key.
 *
 * <p>Intentionally has no {@code booking_id}: in the reserve -> pay -> confirm
 * flow the booking row is not created until T2, AFTER payment, so a booking id is
 * not available at INITIATED time. Adding it would create a circular lifecycle
 * dependency, so it is deliberately out of scope.
 */
@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_payment_idem_key",
                columnNames = "payment_idempotency_key"
        ),
        indexes = @Index(
                name = "idx_payment_user_id",
                columnList = "user_id"
        )
)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_idempotency_key", nullable = false, unique = true)
    private String paymentIdempotencyKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Payment() {
    }

    public Payment(
            String paymentIdempotencyKey,
            Long userId,
            Long seatId,
            BigDecimal amount,
            PaymentStatus status) {

        this.paymentIdempotencyKey = paymentIdempotencyKey;
        this.userId = userId;
        this.seatId = seatId;
        this.amount = amount;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getPaymentIdempotencyKey() { return paymentIdempotencyKey; }
    public Long getUserId() { return userId; }
    public Long getSeatId() { return seatId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
