package com.ticketing.booking.service;

import com.ticketing.shared.exception.PaymentFailedException;
import com.ticketing.shared.exception.PaymentServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that @Retry actually re-executes process() under the configured
 * aspect order. The body is forced to always throw PaymentFailedException
 * (which IS in retry-exceptions) and the number of real body executions is
 * counted via a static counter (a getter/field on a CGLIB proxy would not
 * reflect the target instance, so a static counter is used deliberately).
 *
 * With retry-aspect INNER of circuit-breaker-aspect, all 3 attempts run and
 * then the CB fallback converts the final failure into
 * PaymentServiceUnavailableException. With the aspect order reversed, the CB
 * fallback fires first and throws a non-retryable exception, so the body runs
 * only once.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceRetryTest {

    static final AtomicInteger ATTEMPTS = new AtomicInteger();

    static class CountingPaymentService extends PaymentService {

        @Override
        @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
        @Retry(name = "paymentService")
        public boolean process() {
            ATTEMPTS.incrementAndGet();
            throw new PaymentFailedException();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        CountingPaymentService countingPaymentService() {
            return new CountingPaymentService();
        }
    }

    @Autowired
    private CountingPaymentService paymentService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetState() {
        ATTEMPTS.set(0);
        circuitBreakerRegistry.circuitBreaker("paymentService").reset();
    }

    @Test
    void retryReExecutesProcessThreeTimesBeforeFallback() {

        assertThatThrownBy(() -> paymentService.process())
                .isInstanceOf(PaymentServiceUnavailableException.class);

        assertThat(ATTEMPTS.get())
                .as("number of times the process() body actually executed")
                .isEqualTo(3);
    }
}
