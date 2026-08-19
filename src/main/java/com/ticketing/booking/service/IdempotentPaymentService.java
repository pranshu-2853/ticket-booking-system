package com.ticketing.booking.service;

import com.ticketing.booking.entity.Payment;
import com.ticketing.shared.exception.PaymentServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

/**
 * Durable, idempotent wrapper around the (mock) {@link PaymentService}.
 *
 * <p>Two distinct idempotency mechanisms exist in this project and must not be
 * confused:
 * <ul>
 *   <li><b>Booking idempotency</b> — a Redis cache of the HTTP BookingResponse,
 *       keyed by the client's {@code Idempotency-Key}. Replays duplicate booking
 *       requests when Redis is available. Handled by {@code IdempotencyService}.</li>
 *   <li><b>Payment idempotency (this class)</b> — a PostgreSQL-durable record keyed
 *       by a UNIQUE {@code payment_idempotency_key}. Guarantees a single payment
 *       execution per key even if Redis is down. Redis is only a fast cache here.</li>
 * </ul>
 *
 * <p>This bean is intentionally NOT {@code @Transactional}: it orchestrates the
 * separately-committed persistence steps in {@link PaymentRecordService} with the
 * charge ({@link PaymentService#process()}) in between, exactly like
 * {@code BookingService} orchestrates reserve -> pay -> confirm. Calling
 * {@code process()} on a different bean also keeps its Resilience4j Retry +
 * CircuitBreaker aspects intact.
 */
@Service
@Slf4j
public class IdempotentPaymentService {

    /** Fixed nominal ticket price — the payment service is a mock, not a real gateway. */
    static final BigDecimal TICKET_AMOUNT = new BigDecimal("100.00");

    private static final String CACHE_PREFIX = "pay:";
    private static final String CACHE_SUCCESS = "SUCCESS";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final PaymentService paymentService;
    private final PaymentRecordService paymentRecordService;
    private final RedisTemplate<String, String> redisTemplate;

    public IdempotentPaymentService(
            PaymentService paymentService,
            PaymentRecordService paymentRecordService,
            RedisTemplate<String, String> redisTemplate) {

        this.paymentService = paymentService;
        this.paymentRecordService = paymentRecordService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Idempotently pays for the given key. Returns {@code true} when the payment
     * for this key is (or already was) SUCCESS. Throws when this attempt's charge
     * fails, preserving the caller's existing revert-on-failure behaviour.
     *
     * <p>The same {@code paymentKey} is used for every Resilience4j retry attempt:
     * the key (and its INITIATED row) is established once, before the retryable
     * {@code process()} runs, so retries never generate a new key.
     */
    public boolean pay(String paymentKey, Long userId, Long seatId) {

        // 1. Redis fast path (fail-open: an outage is treated as a miss).
        if (isCachedSuccess(paymentKey)) {
            return true;
        }

        // 2. Durable check in PostgreSQL — the authority, independent of Redis.
        Optional<Payment> existing = paymentRecordService.findByKey(paymentKey);
        if (existing.isPresent()) {
            return handleExisting(paymentKey, existing.get());
        }

        // 3. Establish durable identity BEFORE charging. The UNIQUE constraint on
        //    payment_idempotency_key is the concurrency arbiter: if a competing
        //    request already inserted this key, our insert fails and we defer to
        //    the existing row rather than charging a second time.
        Long paymentId;
        try {
            paymentId = paymentRecordService.createInitiated(
                    paymentKey, userId, seatId, TICKET_AMOUNT);
        } catch (DataIntegrityViolationException duplicateKey) {
            Payment concurrent = paymentRecordService.findByKey(paymentKey)
                    .orElseThrow(() -> duplicateKey);
            return handleExisting(paymentKey, concurrent);
        }

        // 4. We own the row -> charge exactly once.
        return charge(paymentKey, paymentId);
    }

    private boolean handleExisting(String paymentKey, Payment payment) {
        switch (payment.getStatus()) {
            case SUCCESS:
                // Repopulate the cache after a miss; never re-charge.
                cacheSuccess(paymentKey);
                return true;
            case FAILED:
                // Same key, same row: re-attempt the charge. Never a second row.
                return charge(paymentKey, payment.getId());
            case INITIATED:
            default:
                // A concurrent request owns the in-flight charge for this key.
                // Do NOT process a second payment.
                throw new PaymentServiceUnavailableException(
                        "A payment for this request is already in progress.");
        }
    }

    private boolean charge(String paymentKey, Long paymentId) {
        try {
            // Resilience4j Retry + CircuitBreaker live on this call (separate bean).
            paymentService.process();

            // PostgreSQL first...
            paymentRecordService.markSuccess(paymentId);
            // ...Redis second. Redis is never the authoritative result.
            cacheSuccess(paymentKey);
            return true;

        } catch (RuntimeException chargeFailed) {
            paymentRecordService.markFailed(paymentId);
            // Preserve the caller's existing revert-on-payment-failure behaviour.
            throw chargeFailed;
        }
    }

    private boolean isCachedSuccess(String paymentKey) {
        try {
            return CACHE_SUCCESS.equals(
                    redisTemplate.opsForValue().get(CACHE_PREFIX + paymentKey));
        } catch (Exception redisDown) {
            log.warn(
                    "Redis unavailable during payment cache lookup, falling back to PostgreSQL: {}",
                    redisDown.getMessage());
            return false;
        }
    }

    private void cacheSuccess(String paymentKey) {
        try {
            redisTemplate.opsForValue()
                    .set(CACHE_PREFIX + paymentKey, CACHE_SUCCESS, CACHE_TTL);
        } catch (Exception redisDown) {
            log.warn(
                    "Redis unavailable while caching payment success, skipping cache write: {}",
                    redisDown.getMessage());
        }
    }
}
