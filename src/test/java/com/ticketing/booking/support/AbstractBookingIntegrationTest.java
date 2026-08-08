package com.ticketing.booking.support;

import com.ticketing.auth.entity.Role;
import com.ticketing.auth.entity.User;
import com.ticketing.auth.repository.RoleRepository;
import com.ticketing.auth.repository.UserRepository;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.event.entity.Event;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base for DB-backed booking/seat integration tests. Each test creates its own
 * event, seats and users in-line rather than depending on rows that only exist
 * in a developer's local Postgres volume. That makes the suite pass from an
 * empty database (V6 demo seed is irrelevant to these tests) and stops tests
 * from coupling to seed rows that might change.
 *
 * <p>Rows are keyed by their generated IDs, so tests never collide and no
 * global {@code deleteAll} is needed.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractBookingIntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected SeatRepository seatRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    protected Event createEvent() {
        Event event = new Event();
        event.setName("Test Event " + UUID.randomUUID());
        event.setLocation("Test Venue");
        event.setEventTime(LocalDateTime.now().plusDays(1));
        return eventRepository.save(event);
    }

    protected Seat createSeat(Event event) {
        Seat seat = new Seat();
        seat.setSeatNumber("S-" + UUID.randomUUID());
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setEvent(event);
        return seatRepository.saveAndFlush(seat);
    }

    protected User createUser() {
        Role role = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_USER must exist (seeded by V2__seed_roles.sql)"));
        User user = new User(
                "test-" + UUID.randomUUID() + "@example.com",
                "$2a$10$notarealhashjustforfktests............................",
                role);
        return userRepository.save(user);
    }

    protected List<User> createUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(createUser());
        }
        return users;
    }
}
