package io.sentinelops.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    public record CheckoutRequest(@NotBlank String sku, @Min(1) int quantity) { }
    public record OrderView(String id, String sku, int quantity, BigDecimal amount,
                            String status, String reservationId, String paymentId, String failureReason) { }

    @PostMapping("/checkout")
    public OrderView checkout(@Valid @RequestBody CheckoutRequest req) {
        return view(service.checkout(req.sku(), req.quantity()));
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable String id) {
        return view(service.get(id));
    }

    @GetMapping
    public List<OrderView> recent() {
        return service.recent().stream().map(OrderController::view).toList();
    }

    private static OrderView view(Order o) {
        return new OrderView(o.getId(), o.getSku(), o.getQuantity(), o.getAmount(),
                o.getStatus().name(), o.getReservationId(), o.getPaymentId(), o.getFailureReason());
    }

    // A failed checkout is surfaced as 502: the order-service itself is healthy,
    // but an upstream dependency prevented completion.
    @ExceptionHandler(CheckoutException.class)
    public ResponseEntity<Map<String, String>> onCheckoutFailure(CheckoutException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", e.getMessage(), "cause", String.valueOf(e.getCause())));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> onMissing(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
