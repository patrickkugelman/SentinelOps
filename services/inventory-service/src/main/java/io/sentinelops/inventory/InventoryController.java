package io.sentinelops.inventory;

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
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    // ---- DTOs ----
    public record ReserveRequest(@NotBlank String sku, @Min(1) int quantity) { }
    public record ReserveResponse(String reservationId, String sku, int quantity, BigDecimal unitPrice) { }
    public record ReservationRef(@NotBlank String reservationId) { }
    public record ItemView(String sku, BigDecimal unitPrice, int available, int reserved) { }

    @GetMapping("/{sku}")
    public ItemView get(@PathVariable String sku) {
        InventoryItem i = service.get(sku);
        return new ItemView(i.getSku(), i.getUnitPrice(), i.getAvailable(), i.getReserved());
    }

    @PostMapping("/reserve")
    public ReserveResponse reserve(@Valid @RequestBody ReserveRequest req) {
        Reservation r = service.reserve(req.sku(), req.quantity());
        BigDecimal price = service.get(req.sku()).getUnitPrice();
        return new ReserveResponse(r.getId(), r.getSku(), r.getQuantity(), price);
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ReservationRef ref) {
        service.confirm(ref.reservationId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@Valid @RequestBody ReservationRef ref) {
        service.release(ref.reservationId());
        return ResponseEntity.ok().build();
    }

    // ---- Error mapping ----
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, String>> onInsufficient(InsufficientStockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> onMissing(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
