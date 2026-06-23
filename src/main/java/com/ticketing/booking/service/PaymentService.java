package com.ticketing.booking.service;

import com.ticketing.shared.exception.PaymentFailedException;
import com.ticketing.shared.exception.PaymentServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService")
    public boolean process() {
        log.info("Payment attempt started");
        if (Math.random() < 0.3) {
            throw new PaymentFailedException();
        }

        return true;
    }

    /**
     * Industry Standard Fallback Handler.
     * Catches all exceptions to protect the client, but differentiates
     * the root cause for precise logging and monitoring.
     */
    public boolean paymentFallback(Exception ex) {

        // Case 1: The Circuit Breaker is OPEN (Short-circuiting)
        if (ex instanceof CallNotPermittedException) {
            log.error("Circuit Breaker is OPEN. Request blocked.");
            throw new PaymentServiceUnavailableException(
                    "Our payment gateway is temporarily down. Please try again in a few minutes."
            );
        }

        // Case 2: The Circuit Breaker is CLOSED, but all retries failed (Exhausted attempts)
        log.warn("Payment retries exhausted. Root cause: {}", ex.getClass().getSimpleName());
        throw new PaymentServiceUnavailableException(
                "Payment service is temporarily unavailable."
        );
    }
}