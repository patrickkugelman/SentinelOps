package io.sentinelops.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    @Value("${app.delay-ms:0}")
    private long delayMs;

    /** Fraction of authorizations that are randomly declined (0.0–1.0). Default 0. */
    @Value("${app.decline-rate:0.0}")
    private double declineRate;

    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional
    public Payment authorize(String orderRef, BigDecimal amount) {
        applyDelay();
        boolean declined = declineRate > 0 && ThreadLocalRandom.current().nextDouble() < declineRate;
        Payment.Status status = declined ? Payment.Status.DECLINED : Payment.Status.AUTHORIZED;
        Payment p = new Payment(UUID.randomUUID().toString(), orderRef, amount, status);
        payments.save(p);
        if (declined) throw new PaymentDeclinedException(orderRef);
        return p;
    }

    @Transactional
    public Payment capture(String paymentId) {
        Payment p = payments.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("unknown payment: " + paymentId));
        if (p.getStatus() == Payment.Status.AUTHORIZED) {
            p.capture();
            payments.save(p);
        }
        return p;
    }

    @Transactional(readOnly = true)
    public Payment get(String paymentId) {
        return payments.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException("unknown payment: " + paymentId));
    }

    private void applyDelay() {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
