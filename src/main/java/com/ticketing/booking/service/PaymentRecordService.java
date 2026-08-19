package com.ticketing.booking.service;

import com.ticketing.booking.entity.Payment;
import com.ticketing.booking.entity.PaymentStatus;
import com.ticketing.booking.repository.PaymentRepository;
import com.ticketing.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Durable PostgreSQL state for payment idempotency.
 *
 * <p>Kept as a separate bean from the {@link IdempotentPaymentService} orchestrator
 * on purpose: {@code REQUIRES_NEW} only takes effect through the Spring proxy, so
 * each write must be an external (cross-bean) call to commit in its own
 * transaction. This mirrors the {@code BookingService} / {@code BookingTransactionService}
 * split. PostgreSQL — via the UNIQUE {@code payment_idempotency_key} — is the source
 * of truth; Redis is only a cache.
 */
@Service
public class PaymentRecordService {

    private final PaymentRepository paymentRepository;

    public PaymentRecordService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findByKey(String paymentKey) {
        return paymentRepository.findByPaymentIdempotencyKey(paymentKey);
    }

    /**
     * Inserts the INITIATED row that establishes this payment's durable identity
     * BEFORE the charge runs. Throws
     * {@link org.springframework.dao.DataIntegrityViolationException} if the key
     * already exists — that is the concurrency arbiter for duplicate requests.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long createInitiated(
            String paymentKey,
            Long userId,
            Long seatId,
            BigDecimal amount) {

        Payment payment = new Payment(
                paymentKey, userId, seatId, amount, PaymentStatus.INITIATED);

        return paymentRepository.saveAndFlush(payment).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long paymentId) {
        updateStatus(paymentId, PaymentStatus.SUCCESS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long paymentId) {
        updateStatus(paymentId, PaymentStatus.FAILED);
    }

    private void updateStatus(Long paymentId, PaymentStatus status) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with id: " + paymentId));

        // Dirty checking flushes the status change at commit.
        payment.setStatus(status);
    }
}
