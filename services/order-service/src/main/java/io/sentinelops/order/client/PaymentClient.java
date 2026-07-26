package io.sentinelops.order.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/** Thin wrapper over the payment-service REST API. */
@Component
public class PaymentClient {

    private final RestClient client;

    public PaymentClient(RestClient paymentRestClient) {
        this.client = paymentRestClient;
    }

    public record Payment(String paymentId, String orderRef, BigDecimal amount, String status) { }

    public Payment authorize(String orderRef, BigDecimal amount) {
        return client.post()
                .uri("/payments/authorize")
                .body(Map.of("orderRef", orderRef, "amount", amount))
                .retrieve()
                .body(Payment.class);
    }

    public Payment capture(String paymentId) {
        return client.post()
                .uri("/payments/capture")
                .body(Map.of("paymentId", paymentId))
                .retrieve()
                .body(Payment.class);
    }
}
