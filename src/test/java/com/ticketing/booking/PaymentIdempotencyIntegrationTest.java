package com.ticketing.booking;

import com.ticketing.auth.entity.User;
import com.ticketing.booking.entity.PaymentStatus;
import com.ticketing.booking.repository.PaymentRepository;
import com.ticketing.booking.service.IdempotentPaymentService;
import com.ticketing.booking.service.PaymentService;
import com.ticketing.booking.support.AbstractBookingIntegrationTest;
import com.ticketing.event.entity.Event;
import com.ticketing.seat.entity.Seat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * DB-backed proof that PostgreSQL (not Redis) is the durable authority for payment
 * idempotency:
 *
 * <ul>
 *   <li>two concurrent requests with the same key produce ONE row and ONE charge;</li>
 *   <li>a repeat call whose Redis cache entry has been evicted still dedups via
 *       the PostgreSQL row.</li>
 * </ul>
 *
 * The mock {@link PaymentService} is replaced with a counting stub so the number of
 * real charge executions can be asserted.
 */
class PaymentIdempotencyIntegrationTest extends AbstractBookingIntegrationTest {

    @Autowired private IdempotentPaymentService idempotentPaymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RedisTemplate<String, String> redisTemplate;

    @MockBean private PaymentService paymentService;

    private final AtomicInteger charges = new AtomicInteger();

    private Long userId;
    private Long seatId;

    @BeforeEach
    void setUp() {
        charges.set(0);
        when(paymentService.process()).thenAnswer(invocation -> {
            charges.incrementAndGet();
            Thread.sleep(50); // widen the concurrency window
            return true;
        });

        Event event = createEvent();
        Seat seat = createSeat(event);
        seatId = seat.getId();
        User user = createUser();
        userId = user.getId();
    }

    @Test
    void concurrentDuplicatePayments_produceOneRowAndOneExecution() throws Exception {
        String key = "concurrent-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        Future<?> f1 = executor.submit(() -> attemptPay(latch, key));
        Future<?> f2 = executor.submit(() -> attemptPay(latch, key));

        latch.countDown();
        f1.get();
        f2.get();
        executor.shutdown();

        assertThat(charges.get())
                .as("payment executed exactly once for the shared key")
                .isEqualTo(1);
        assertThat(paymentRepository.findByPaymentIdempotencyKey(key)).isPresent();
        assertThat(paymentRepository.findByPaymentIdempotencyKey(key).get().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void secondCallAfterRedisEvicted_stillDedupsViaPostgres() {
        String key = "durable-" + UUID.randomUUID();

        assertThat(idempotentPaymentService.pay(key, userId, seatId)).isTrue();
        assertThat(charges.get()).isEqualTo(1);

        // Evict the Redis cache so the second call cannot use the fast path;
        // PostgreSQL alone must prevent a second charge.
        redisTemplate.delete("pay:" + key);

        assertThat(idempotentPaymentService.pay(key, userId, seatId)).isTrue();
        assertThat(charges.get())
                .as("no second charge — PostgreSQL is the durable authority")
                .isEqualTo(1);
    }

    private void attemptPay(CountDownLatch latch, String key) {
        try {
            latch.await();
            idempotentPaymentService.pay(key, userId, seatId);
        } catch (Exception ignored) {
            // The losing concurrent request may throw "already in progress".
        }
    }
}
