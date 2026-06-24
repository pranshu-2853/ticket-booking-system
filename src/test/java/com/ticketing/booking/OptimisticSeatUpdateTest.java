package com.ticketing.booking;

import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OptimisticSeatUpdateTest {

    @Autowired
    private SeatRepository seatRepository;

    private static final Long SEAT_ID = 13L;

    @BeforeEach
    void resetSeat() {

        Seat seat = seatRepository.findById(SEAT_ID)
                .orElseThrow();

        seat.setStatus(SeatStatus.AVAILABLE);

        seatRepository.saveAndFlush(seat);
    }

    @Test
    void shouldThrowOptimisticLockExceptionForConcurrentUpdates()
            throws Exception {

        int threadCount = 8;

        AtomicInteger successCount =
                new AtomicInteger();

        AtomicInteger conflictCount =
                new AtomicInteger();

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch latch =
                new CountDownLatch(1);

        List<Future<?>> futures =
                new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            futures.add(
                    executor.submit(() -> {

                        try {

                            latch.await();

                            Seat seat =
                                    seatRepository.findById(SEAT_ID)
                                            .orElseThrow();

                            Thread.sleep(500);

                            seat.setStatus(
                                    SeatStatus.BOOKED
                            );

                            seatRepository.saveAndFlush(seat);

                            successCount.incrementAndGet();

                        } catch (
                                ObjectOptimisticLockingFailureException e
                        ) {

                            conflictCount.incrementAndGet();



                        } catch (Exception e) {

                            e.printStackTrace();
                        }

                        return null;
                    })
            );
        }

        latch.countDown();

        for (Future<?> future : futures) {

            future.get();
        }

        executor.shutdown();

        assertThat(successCount.get())
                .isEqualTo(1);

        assertThat(conflictCount.get())
                .isGreaterThan(0);
    }
}