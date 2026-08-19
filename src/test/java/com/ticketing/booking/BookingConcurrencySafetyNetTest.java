package com.ticketing.booking;

import com.ticketing.auth.entity.User;
import com.ticketing.booking.service.BookingService;
import com.ticketing.booking.service.PaymentService;
import com.ticketing.booking.support.AbstractBookingIntegrationTest;
import com.ticketing.event.entity.Event;
import com.ticketing.seat.entity.Seat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Safety-net duplicate of {@link ConcurrencyTest}: 8 users, same seat, exactly
 * one booking. Kept as an independent guard on the concurrency invariant.
 */
class BookingConcurrencySafetyNetTest extends AbstractBookingIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @MockBean
    private PaymentService paymentService;

    private Long seatId;
    private Long[] userIds;

    @BeforeEach
    void setUp() {
        when(paymentService.process()).thenReturn(true);

        Event event = createEvent();
        Seat seat = createSeat(event);
        seatId = seat.getId();

        List<User> users = createUsers(8);
        userIds = users.stream().map(User::getId).toArray(Long[]::new);
    }

    @RepeatedTest(3)
    void onlyOneBookingShouldBeCreatedUnderConcurrentRequests() throws Exception {

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Long userId = userIds[i];
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    bookingService.createBooking(userId, seatId, java.util.UUID.randomUUID().toString());
                } catch (Exception ignored) {
                }
                return null;
            }));
        }

        latch.countDown();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception ignored) {
            }
        }
        executor.shutdown();

        long bookingCount = bookingRepository.countBySeatId(seatId);
        assertThat(bookingCount).isEqualTo(1);
    }
}
