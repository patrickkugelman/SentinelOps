package io.sentinelops.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A hold placed on stock for a SKU, later confirmed or released. */
@Entity
@Table(name = "reservation")
public class Reservation {

    public enum Status { PENDING, CONFIRMED, RELEASED }

    @Id
    private String id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Reservation() { /* JPA */ }

    public Reservation(String id, String sku, int quantity) {
        this.id = id;
        this.sku = sku;
        this.quantity = quantity;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void markConfirmed() { this.status = Status.CONFIRMED; }
    public void markReleased() { this.status = Status.RELEASED; }
}
