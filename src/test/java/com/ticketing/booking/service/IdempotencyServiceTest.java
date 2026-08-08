package com.ticketing.booking.service;

import com.ticketing.booking.dto.BookingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, BookingResponse> redisTemplate;

    @Mock
    private ValueOperations<String, BookingResponse> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void getCachedResponse_shouldReturnEmpty_whenRedisThrows() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(eq("idem:42:key-1")))
                .thenThrow(new RuntimeException("redis down"));

        // Act
        Optional<BookingResponse> result =
                idempotencyService.getCachedResponse(42L, "key-1");

        // Assert — fail open: outage looks like a cache miss, booking proceeds
        assertTrue(result.isEmpty());
    }

    @Test
    void saveResponse_shouldSwallowException_whenRedisThrows() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations)
                .set(any(), any(), any(Duration.class));

        BookingResponse response =
                new BookingResponse(1L, 42L, 11L, LocalDateTime.of(2026, 6, 24, 12, 0));

        // Act + Assert — a failed cache write must not surface to the caller
        assertDoesNotThrow(() -> idempotencyService.saveResponse(42L, "key-1", response));
    }
}
