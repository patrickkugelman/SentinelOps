package io.sentinelops.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/** A stock-keeping unit with available and reserved quantities. */
@Entity
@Table(name = "inventory_item")
public class InventoryItem {

    @Id
    private String sku;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    /** Optimistic lock so concurrent reservations can't oversell. */
    @Version
    private long version;

    protected InventoryItem() { /* JPA */ }

    public InventoryItem(String sku, BigDecimal unitPrice, int available) {
        this.sku = sku;
        this.unitPrice = unitPrice;
        this.available = available;
        this.reserved = 0;
    }

    public String getSku() { return sku; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getAvailable() { return available; }
    public int getReserved() { return reserved; }
    public long getVersion() { return version; }

    public void reserve(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (available < qty) throw new InsufficientStockException(sku, qty, available);
        available -= qty;
        reserved += qty;
    }

    public void confirm(int qty) {
        reserved = Math.max(0, reserved - qty);
    }

    public void release(int qty) {
        reserved = Math.max(0, reserved - qty);
        available += qty;
    }
}
