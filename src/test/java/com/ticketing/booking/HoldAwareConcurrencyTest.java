package com.ticketing.booking;

import com.ticketing.auth.entity.User;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.service.BookingService;
import com.ticketing.booking.service.PaymentService;
import com.ticketing.booking.support.AbstractBookingIntegrationTest;
import com.ticketing.event.entity.Event;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.service.SeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Exercises the Redis hold ownership check UNDER CONTENTION. User A holds the
 * seat, then A plus 7 non-holders all try to book simultaneously.
 *
 * <p>Payment is stubbed to always succeed so the sole eligible booker (A) does
 * not flake on a random payment failure. Self-seeds its own event/seat/users.
 */
class HoldAwareConcurrencyTest extends AbstractBookingIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService seatHoldService;

    @MockBean
    private PaymentService paymentService;

    private Long seatId;
    private Long eventId;
    private Long userAId;
    private Long[] otherUserIds;

    @BeforeEach
    void setUp() {
        when(paymentService.process()).thenReturn(true);

        Event event = createEvent();
        eventId = event.getId();
        Seat seat = createSeat(event);
        seatId = seat.getId();

        User userA = createUser();
        userAId = userA.getId();

        List<User> others = createUsers(7);
        otherUserIds = others.stream().map(User::getId).toArray(Long[]::new);

        // ensure no stale hold
        seatHoldService.releaseSeat(eventId, seatId);
    }

    @RepeatedTest(3)
    void onlyTheHolderCanBook_underContention() throws Exception {

        boolean held = seatHoldService.tryHoldSeat(eventId, seatId, userAId);
        assertThat(held).as("user A must acquire the hold").isTrue();

        int threadCount = 8; // A + 7 others
        Long[] contenders = new Long[threadCount];
        contenders[0] = userAId;
        System.arraycopy(otherUserIds, 0, contenders, 1, otherUserIds.length);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicLong winner = new AtomicLong(-1);
        Map<String, Integer> failureCounts = new ConcurrentHashMap<>();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Long userId = contenders[i];
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    bookingService.createBooking(userId, seatId);
                    winner.set(userId);
                } catch (Exception e) {
                    failureCounts.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                }
                return null;
            }));
        }

        latch.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        long bookingCount = bookingRepository.countBySeatId(seatId);
        assertThat(bookingCount).as("exactly one booking for the seat").isEqualTo(1);

        List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> b.getSeat().getId().equals(seatId))
                .toList();
        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).getUser().getId())
                .as("the booking belongs to the holder (user A)").isEqualTo(userAId);
        assertThat(winner.get()).as("the successful thread was user A").isEqualTo(userAId);

        // Diagnostic: how the 7 non-holders failed. Under the reserve-before-pay
        // flow the ownership check sits in T1, but it is still preceded by the
        // RESERVED status check, so most non-holders now fail with
        // SeatAlreadyReservedException (A reserved first) rather than reaching the
        // ownership check (SeatNotHeldByUserException).
        System.out.println("[HoldAwareConcurrencyTest] non-holder failure distribution: " + failureCounts);
    }
}
