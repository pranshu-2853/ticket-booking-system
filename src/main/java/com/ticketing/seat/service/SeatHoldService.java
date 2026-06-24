package com.ticketing.seat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatHoldService {

    private final RedisTemplate<String, String> redisTemplate;

    public boolean tryHoldSeat(
            Long eventId,
            Long seatId,
            Long userId) {

        try {

            String key =
                    "seat_lock:" + eventId + ":" + seatId;

            Boolean acquired =
                    redisTemplate.opsForValue()
                            .setIfAbsent(
                                    key,
                                    userId.toString(),
                                    Duration.ofMinutes(5));

            return Boolean.TRUE.equals(acquired);

        } catch (Exception e) {

            log.warn(
                    "Redis unavailable, allowing request through: {}",
                    e.getMessage());

            return true;
        }
    }

    public void releaseSeat(
            Long eventId,
            Long seatId) {

        try {

            String key =
                    "seat_lock:" + eventId + ":" + seatId;

            redisTemplate.delete(key);

        } catch (Exception e) {

            log.warn(
                    "Failed to release Redis hold: {}",
                    e.getMessage());
        }
    }
    public String getSeatHolder(Long eventId, Long seatId) {

        String key = "seat_lock:" + eventId + ":" + seatId;

        return redisTemplate.opsForValue().get(key);
    }

}