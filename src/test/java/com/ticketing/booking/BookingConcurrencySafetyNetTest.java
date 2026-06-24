package com.ticketing.booking;

import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.service.BookingService;
import com.ticketing.seat.entity.Seat;
import com.ticketing.seat.entity.SeatStatus;
import com.ticketing.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencySafetyNetTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private SeatRepository seatRepository;

    private static final Long SEAT_ID = 11L;

    private static final Long[] USER_IDS = {
            1L, 2L, 3L, 6L, 7L, 8L, 9L, 10L
    };

    @BeforeEach
    void cleanUp() {

        bookingRepository.deleteAll();

        Seat seat = seatRepository.findById(SEAT_ID)
                .orElseThrow();

        seat.setStatus(SeatStatus.AVAILABLE);

        seatRepository.saveAndFlush(seat);
    }

    @RepeatedTest(3)
    void onlyOneBookingShouldBeCreatedUnderConcurrentRequests()
            throws Exception {

        int threadCount = 8;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch latch =
                new CountDownLatch(1);

        List<Future<?>> futures =
                new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            Long userId = USER_IDS[i];

            futures.add(
                    executor.submit(() -> {

                        try {

                            latch.await();

                            bookingService.createBooking(
                                    userId,
                                    SEAT_ID
                            );

                        } catch (Exception ignored) {
                        }

                        return null;
                    })
            );
        }

        latch.countDown();

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
            }
        }

        executor.shutdown();

        long bookingCount =
                bookingRepository.countBySeatId(SEAT_ID);

        assertThat(bookingCount)
                .isEqualTo(1);
    }
}