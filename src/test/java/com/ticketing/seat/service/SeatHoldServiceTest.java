package com.ticketing.seat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeatHoldService seatHoldService;

    @Test
    void tryHoldSeat_shouldReturnTrue_whenSeatCanBeHeld() {
        // Arrange
        Long eventId = 10L;
        Long seatId = 20L;
        Long userId = 30L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "seat_lock:10:20",
                "30",
                Duration.ofMinutes(5)
        )).thenReturn(true);

        // Act
        boolean result = seatHoldService.tryHoldSeat(eventId, seatId, userId);

        // Assert
        assertTrue(result);
        verify(valueOperations).setIfAbsent(
                "seat_lock:10:20",
                "30",
                Duration.ofMinutes(5)
        );
    }

    @Test
    void tryHoldSeat_shouldReturnFalse_whenSeatIsAlreadyHeld() {
        // Arrange
        Long eventId = 10L;
        Long seatId = 20L;
        Long userId = 30L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "seat_lock:10:20",
                "30",
                Duration.ofMinutes(5)
        )).thenReturn(false);

        // Act
        boolean result = seatHoldService.tryHoldSeat(eventId, seatId, userId);

        // Assert
        assertFalse(result);
        verify(valueOperations).setIfAbsent(
                "seat_lock:10:20",
                "30",
                Duration.ofMinutes(5)
        );
    }

    @Test
    void tryHoldSeat_shouldReturnTrue_whenRedisThrowsException() {
        // Arrange
        Long eventId = 10L;
        Long seatId = 20L;
        Long userId = 30L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenThrow(new RuntimeException("redis down"));

        // Act
        boolean result = seatHoldService.tryHoldSeat(eventId, seatId, userId);

        // Assert
        assertTrue(result);
    }

    @Test
    void releaseSeat_shouldDeleteKey_whenRedisIsAvailable() {
        // Arrange
        Long eventId = 10L;
        Long seatId = 20L;

        // Act
        seatHoldService.releaseSeat(eventId, seatId);

        // Assert
        verify(redisTemplate).delete("seat_lock:10:20");
    }

    @Test
    void releaseSeat_shouldSwallowException_whenRedisThrowsException() {
        // Arrange
        Long eventId = 10L;
        Long seatId = 20L;
        doThrow(new RuntimeException("redis down")).when(redisTemplate).delete("seat_lock:10:20");

        // Act
        seatHoldService.releaseSeat(eventId, seatId);

        // Assert
        verify(redisTemplate).delete("seat_lock:10:20");
    }

    @Test
    void getSeatHolder_shouldReturnStoredHolder() {
        // Arrange
        Long eventId = 10L;
        Long seatId = 20L;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("seat_lock:10:20")).thenReturn("30");

        // Act
        String result = seatHoldService.getSeatHolder(eventId, seatId);

        // Assert
        assertEquals("30", result);
        verify(valueOperations).get("seat_lock:10:20");
    }
}
