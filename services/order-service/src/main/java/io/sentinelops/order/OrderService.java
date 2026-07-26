package io.sentinelops.order;

import io.micrometer.core.instrument.MeterRegistry;
import io.sentinelops.order.client.InventoryClient;
import io.sentinelops.order.client.PaymentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Orchestrates a checkout across inventory + payment. This is the SAGA:
 *   reserve -> authorize -> confirm -> capture
 * with best-effort compensation (release the reservation) on failure.
 *
 * Deliberately NOT wrapped in a single DB @Transactional: the remote calls are
 * the interesting failure surface, and we want partial progress + compensation
 * to be observable, which is what makes the cascading-failure demos realistic.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final InventoryClient inventory;
    private final PaymentClient payment;
    private final MeterRegistry meters;

    public OrderService(OrderRepository orders, InventoryClient inventory,
                        PaymentClient payment, MeterRegistry meters) {
        this.orders = orders;
        this.inventory = inventory;
        this.payment = payment;
        this.meters = meters;
    }

    public Order checkout(String sku, int quantity) {
        String orderId = UUID.randomUUID().toString();
        Order order = orders.save(new Order(orderId, sku, quantity));
        String reservationId = null;
        try {
            InventoryClient.Reservation res = inventory.reserve(sku, quantity);
            reservationId = res.reservationId();
            order.setReservationId(reservationId);

            BigDecimal amount = res.unitPrice().multiply(BigDecimal.valueOf(quantity));
            order.setAmount(amount);

            PaymentClient.Payment auth = payment.authorize(orderId, amount);
            order.setPaymentId(auth.paymentId());

            inventory.confirm(reservationId);
            payment.capture(auth.paymentId());

            order.markCompleted();
            orders.save(order);
            meters.counter("orders_checkout_total", "result", "completed").increment();
            log.info("checkout COMPLETED order={} sku={} qty={} amount={}", orderId, sku, quantity, amount);
            return order;
        } catch (Exception e) {
            compensate(reservationId);
            order.markFailed(rootMessage(e));
            orders.save(order);
            meters.counter("orders_checkout_total", "result", "failed").increment();
            log.warn("checkout FAILED order={} sku={} qty={} reason={}", orderId, sku, quantity, rootMessage(e));
            throw new CheckoutException("checkout failed for order " + orderId, e);
        }
    }

    public Order get(String id) {
        return orders.findById(id)
                .orElseThrow(() -> new NoSuchElementException("unknown order: " + id));
    }

    public java.util.List<Order> recent() {
        return orders.findAll();
    }

    private void compensate(String reservationId) {
        if (reservationId == null) return;
        try {
            inventory.release(reservationId);
        } catch (Exception ex) {
            // Compensation is best-effort; a stuck reservation is preferable to a stuck request.
            log.warn("compensation (release {}) failed: {}", reservationId, ex.toString());
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        return r.getClass().getSimpleName() + ": " + r.getMessage();
    }
}
