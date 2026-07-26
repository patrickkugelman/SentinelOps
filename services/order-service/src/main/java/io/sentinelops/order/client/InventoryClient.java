package io.sentinelops.order.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/** Thin wrapper over the inventory-service REST API. */
@Component
public class InventoryClient {

    private final RestClient client;

    public InventoryClient(RestClient inventoryRestClient) {
        this.client = inventoryRestClient;
    }

    public record Reservation(String reservationId, String sku, int quantity, BigDecimal unitPrice) { }

    public Reservation reserve(String sku, int quantity) {
        return client.post()
                .uri("/inventory/reserve")
                .body(Map.of("sku", sku, "quantity", quantity))
                .retrieve()
                .body(Reservation.class);
    }

    public void confirm(String reservationId) {
        client.post()
                .uri("/inventory/confirm")
                .body(Map.of("reservationId", reservationId))
                .retrieve()
                .toBodilessEntity();
    }

    public void release(String reservationId) {
        client.post()
                .uri("/inventory/release")
                .body(Map.of("reservationId", reservationId))
                .retrieve()
                .toBodilessEntity();
    }
}
