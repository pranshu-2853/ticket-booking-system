package com.ticketing.booking.service;

import com.ticketing.booking.dto.BookingResponse;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final String PREFIX = "idem:";

    private final RedisTemplate<String, BookingResponse> redisTemplate;

    public IdempotencyService(
            RedisTemplate<String, BookingResponse> redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    public Optional<BookingResponse> getCachedResponse(
            String idempotencyKey) {

        BookingResponse response =
                redisTemplate.opsForValue()
                        .get(buildKey(idempotencyKey));

        return Optional.ofNullable(response);
    }

    public void saveResponse(
            String idempotencyKey,
            BookingResponse response) {

        redisTemplate.opsForValue().set(
                buildKey(idempotencyKey),
                response,
                Duration.ofHours(24)
        );
    }

    private String buildKey(String idempotencyKey) {
        return PREFIX + idempotencyKey;
    }
}