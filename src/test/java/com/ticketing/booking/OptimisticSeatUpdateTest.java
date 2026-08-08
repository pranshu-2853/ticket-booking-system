package com.ticketing.booking;

import com.ticketing.booking.support.AbstractBookingIntegrationTest;
import com.ticketing.event.entity.Event;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optimistic locking produces the RIGHT result under contention — exactly one
 * winner — but it does so by forcing the other N-1 concurrent callers to FAIL
 * with ObjectOptimisticLockingFailureException, leaving them to retry. That
 * retry-storm cost under high contention is precisely why the real booking path
 * ({@code BookingService}/{@code BookingTransactionService}) uses a pessimistic
 * row lock instead: contenders wait their turn rather than failing and retrying.
 *
 * <p>This test demonstrates the optimistic strategy in isolation (it bypasses
 * the booking path and updates the seat row directly). It asserts correctness
 * (1 success) AND the contention cost (>0 conflicts).
 */
class OptimisticSeatUpdateTest extends AbstractBookingIntegrationTest {

    private Long seatId;

    @BeforeEach
    void setUp() {
        Event event = createEvent();
        Seat seat = createSeat(event);
        seatId = seat.getId();
    }

    @Test
    void optimisticLockingIsCorrectButFailsUnderContention() throws Exception {

        int threadCount = 8;

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();

                    Seat seat = seatRepository.findById(seatId).orElseThrow();

                    Thread.sleep(500);

                    seat.setStatus(SeatStatus.BOOKED);

                    seatRepository.saveAndFlush(seat);

                    successCount.incrementAndGet();

                } catch (ObjectOptimisticLockingFailureException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }));
        }

        latch.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isGreaterThan(0);
    }
}
