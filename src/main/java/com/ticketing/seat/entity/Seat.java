package com.ticketing.seat.entity;

import com.ticketing.event.entity.Event;
import jakarta.persistence.*;

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

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    public Long getId() { return id; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }
    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
}