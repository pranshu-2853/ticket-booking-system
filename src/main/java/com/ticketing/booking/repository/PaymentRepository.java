package com.ticketing.booking.repository;

import com.ticketing.booking.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentIdempotencyKey(String paymentIdempotencyKey);
}
