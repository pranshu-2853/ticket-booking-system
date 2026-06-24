package com.ticketing.booking.service;

import com.ticketing.shared.exception.PaymentFailedException;
import com.ticketing.shared.exception.PaymentServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void process_shouldReturnTrue_whenRandomAboveFailureThreshold() {
        // Arrange
        boolean successObserved = false;

        // Act
        for (int attempt = 0; attempt < 200; attempt++) {
            try {
                if (paymentService.process()) {
                    successObserved = true;
                    break;
                }
            } catch (PaymentFailedException ignored) {
                // retry until the success branch is observed
            }
        }

        // Assert
        assertTrue(successObserved);
    }

    @Test
    void process_shouldThrowPaymentFailed_whenRandomBelowFailureThreshold() {
        // Arrange
        boolean failureObserved = false;

        // Act
        for (int attempt = 0; attempt < 200; attempt++) {
            try {
                paymentService.process();
            } catch (PaymentFailedException ex) {
                failureObserved = true;
                break;
            }
        }

        // Assert
        assertTrue(failureObserved);
    }

    @Test
    void paymentFallback_shouldThrowServiceUnavailable_whenCircuitBreakerIsOpen() {
        // Arrange
        CallNotPermittedException exception = mock(CallNotPermittedException.class);

        // Act + Assert
        PaymentServiceUnavailableException ex = assertThrows(
                PaymentServiceUnavailableException.class,
                () -> paymentService.paymentFallback(exception)
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                "Our payment gateway is temporarily down. Please try again in a few minutes.",
                ex.getMessage()
        );
    }

    @Test
    void paymentFallback_shouldThrowServiceUnavailable_whenRetriesAreExhausted() {
        // Arrange
        RuntimeException exception = new RuntimeException("retry exhausted");

        // Act + Assert
        PaymentServiceUnavailableException ex = assertThrows(
                PaymentServiceUnavailableException.class,
                () -> paymentService.paymentFallback(exception)
        );

        org.junit.jupiter.api.Assertions.assertEquals("Payment service is temporarily unavailable.", ex.getMessage());
    }
}
