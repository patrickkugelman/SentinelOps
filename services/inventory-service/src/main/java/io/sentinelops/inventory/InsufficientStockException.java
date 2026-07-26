package io.sentinelops.inventory;

/** Thrown when a reservation asks for more units than are available. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku, int requested, int available) {
        super("insufficient stock for %s: requested %d, available %d".formatted(sku, requested, available));
    }
}
