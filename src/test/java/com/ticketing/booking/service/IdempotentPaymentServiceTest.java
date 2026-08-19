package com.ticketing.booking.service;

import com.ticketing.booking.entity.Payment;
import com.ticketing.booking.entity.PaymentStatus;
import com.ticketing.shared.exception.PaymentServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for durable payment idempotency. Exercises the Redis fast path, the
 * PostgreSQL authority (including with Redis down), the INITIATED -> SUCCESS/FAILED
 * lifecycle, the FAILED-retry-same-row rule, and the UNIQUE-constraint concurrency
 * arbiter (DataIntegrityViolationException handling).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotentPaymentServiceTest {

    @Mock private PaymentService paymentService;
    @Mock private PaymentRecordService paymentRecordService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private IdempotentPaymentService service;

    private static final String KEY = "42:idem-1";
    private static final String CACHE_KEY = "pay:" + KEY;
    private static final Long USER = 42L;
    private static final Long SEAT = 11L;

    private void redisMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
    }

    @Test
    void firstPayment_createsInitiatedThenSuccess_processCalledOnce() {
        redisMiss();
        when(paymentRecordService.findByKey(KEY)).thenReturn(Optional.empty());
        when(paymentRecordService.createInitiated(
                KEY, USER, SEAT, IdempotentPaymentService.TICKET_AMOUNT)).thenReturn(1L);
        when(paymentService.process()).thenReturn(true);

        assertTrue(service.pay(KEY, USER, SEAT));

        verify(paymentService, times(1)).process();
        verify(paymentRecordService).markSuccess(1L);
        verify(paymentRecordService, never()).markFailed(anyLong());
        verify(valueOps).set(eq(CACHE_KEY), eq("SUCCESS"), any());
    }

    @Test
    void existingSuccessInDb_returnsWithoutProcessing() {
        redisMiss();
        Payment success = mock(Payment.class);
        when(success.getStatus()).thenReturn(PaymentStatus.SUCCESS);
        when(paymentRecordService.findByKey(KEY)).thenReturn(Optional.of(success));

        assertTrue(service.pay(KEY, USER, SEAT));

        verify(paymentService, never()).process();
        verify(paymentRecordService, never())
                .createInitiated(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void redisCacheHit_returnsWithoutDbOrProcess() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn("SUCCESS");

        assertTrue(service.pay(KEY, USER, SEAT));

        verify(paymentService, never()).process();
        verifyNoInteractions(paymentRecordService);
    }

    @Test
    void redisDown_fallsBackToPostgres_noReprocess() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenThrow(new RuntimeException("redis down"));
        Payment success = mock(Payment.class);
        when(success.getStatus()).thenReturn(PaymentStatus.SUCCESS);
        when(paymentRecordService.findByKey(KEY)).thenReturn(Optional.of(success));

        assertTrue(service.pay(KEY, USER, SEAT));

        verify(paymentService, never()).process();
    }

    @Test
    void existingFailed_reprocessesSameRow_noNewRow() {
        redisMiss();
        Payment failed = mock(Payment.class);
        when(failed.getStatus()).thenReturn(PaymentStatus.FAILED);
        when(failed.getId()).thenReturn(7L);
        when(paymentRecordService.findByKey(KEY)).thenReturn(Optional.of(failed));
        when(paymentService.process()).thenReturn(true);

        assertTrue(service.pay(KEY, USER, SEAT));

        verify(paymentRecordService, never())
                .createInitiated(anyString(), anyLong(), anyLong(), any());
        verify(paymentService, times(1)).process();
        verify(paymentRecordService).markSuccess(7L);
    }

    @Test
    void concurrentInsertLosesRace_defersToExistingSuccess_noSecondProcess() {
        redisMiss();
        Payment success = mock(Payment.class);
        when(success.getStatus()).thenReturn(PaymentStatus.SUCCESS);
        // first check empty; after the UNIQUE violation the winner's row is visible
        when(paymentRecordService.findByKey(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(success));
        when(paymentRecordService.createInitiated(
                KEY, USER, SEAT, IdempotentPaymentService.TICKET_AMOUNT))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertTrue(service.pay(KEY, USER, SEAT));

        verify(paymentService, never()).process();
    }

    @Test
    void concurrentInsertLosesRace_winnerStillInFlight_throwsInProgress() {
        redisMiss();
        Payment inFlight = mock(Payment.class);
        when(inFlight.getStatus()).thenReturn(PaymentStatus.INITIATED);
        when(paymentRecordService.findByKey(KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(inFlight));
        when(paymentRecordService.createInitiated(
                KEY, USER, SEAT, IdempotentPaymentService.TICKET_AMOUNT))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(PaymentServiceUnavailableException.class,
                () -> service.pay(KEY, USER, SEAT));
        verify(paymentService, never()).process();
    }

    @Test
    void processFails_marksFailedAndPropagates() {
        redisMiss();
        when(paymentRecordService.findByKey(KEY)).thenReturn(Optional.empty());
        when(paymentRecordService.createInitiated(
                KEY, USER, SEAT, IdempotentPaymentService.TICKET_AMOUNT)).thenReturn(3L);
        when(paymentService.process())
                .thenThrow(new PaymentServiceUnavailableException("down"));

        assertThrows(PaymentServiceUnavailableException.class,
                () -> service.pay(KEY, USER, SEAT));

        verify(paymentRecordService).markFailed(3L);
        verify(paymentRecordService, never()).markSuccess(anyLong());
    }
}
