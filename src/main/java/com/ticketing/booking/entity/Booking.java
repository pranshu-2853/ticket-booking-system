package com.ticketing.booking.entity;

import com.ticketing.auth.entity.User;
import com.ticketing.seat.entity.Seat;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "seat_id",
            nullable = false
    )
    private Seat seat;

    @Column(
            name = "booked_at",
            nullable = false
    )
    private LocalDateTime bookedAt;

    @PrePersist
    public void prePersist() {
        this.bookedAt = LocalDateTime.now();
    }

    public Booking() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
    }

    public LocalDateTime getBookedAt() {
        return bookedAt;
    }
}