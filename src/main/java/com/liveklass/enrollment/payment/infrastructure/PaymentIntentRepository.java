package com.liveklass.enrollment.payment.infrastructure;

import com.liveklass.enrollment.payment.domain.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {

    Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);
}
