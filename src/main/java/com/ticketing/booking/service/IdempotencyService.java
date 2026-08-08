package com.ticketing.booking.service;

import com.ticketing.booking.dto.BookingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@Slf4j
public class IdempotencyService {

    private static final String PREFIX = "idem:";

    private final RedisTemplate<String, BookingResponse> redisTemplate;

    public IdempotencyService(
            RedisTemplate<String, BookingResponse> redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    public Optional<BookingResponse> getCachedResponse(
            Long userId,
            String idempotencyKey) {

        try {

            BookingResponse response =
                    redisTemplate.opsForValue()
                            .get(buildKey(userId, idempotencyKey));

            return Optional.ofNullable(response);

        } catch (Exception e) {

            // Fail open: a Redis outage must not block booking. Treating the
            // lookup as a miss means the request is processed normally.
            log.warn(
                    "Redis unavailable during idempotency lookup, treating as cache miss: {}",
                    e.getMessage());

            return Optional.empty();
        }
    }

    public void saveResponse(
            Long userId,
            String idempotencyKey,
            BookingResponse response) {

        try {

            redisTemplate.opsForValue().set(
                    buildKey(userId, idempotencyKey),
                    response,
                    Duration.ofHours(24)
            );

        } catch (Exception e) {

            // Fail open: the booking already succeeded; failing to cache the
            // response must not turn a successful booking into an error.
            log.warn(
                    "Redis unavailable while caching idempotency response, skipping cache write: {}",
                    e.getMessage());
        }
    }

    private String buildKey(Long userId, String idempotencyKey) {
        return PREFIX + userId + ":" + idempotencyKey;
    }
}