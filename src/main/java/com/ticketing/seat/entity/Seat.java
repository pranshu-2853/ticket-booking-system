package com.ticketing.seat.entity;

import com.ticketing.event.entity.Event;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_event_seat",
                columnNames = {"event_id", "seat_number"}
        ),
        indexes = @Index(
                name = "idx_seat_event_id",
                columnList = "event_id"
        )
)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-locking version column.
     *
     * <p>This exists to <b>demonstrate</b> the optimistic-locking strategy as a
     * comparison point (see {@code OptimisticSeatUpdateTest}, which is the only
     * place its conflict detection is actually exercised and relied upon).
     *
     * <p>It is <b>not relied upon by the booking path</b>. In
     * {@code BookingService.createBooking} the seat is loaded with
     * {@code PESSIMISTIC_WRITE} ({@code SELECT ... FOR UPDATE}), so all
     * contending bookers are serialized on the row lock. Hibernate still emits
     * {@code WHERE version = ?} and increments this column when the BOOKED
     * status is flushed, but because the lock already guarantees exclusive
     * access the version check can never fail there — the pessimistic lock, not
     * this field, is the correctness guard for booking.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    /**
     * Deadline for a RESERVED seat. Set when the seat is moved to RESERVED (T1)
     * and cleared when it is confirmed BOOKED or reverted to AVAILABLE. The
     * reservation sweeper reclaims seats whose reserved_until is in the past.
     * Null whenever the seat is not currently RESERVED.
     */
    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Seat() {
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


    public Long getId() { return id; }
    public Long getVersion() {
        return version;
    }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public LocalDateTime getReservedUntil() { return reservedUntil; }
    public void setReservedUntil(LocalDateTime reservedUntil) { this.reservedUntil = reservedUntil; }
    public LocalDateTime getCreatedAt() {  return createdAt;    }
    public LocalDateTime getUpdatedAt() {  return updatedAt;    }
}