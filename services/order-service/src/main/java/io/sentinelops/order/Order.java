package io.sentinelops.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    public enum Status { PENDING, COMPLETED, FAILED }

    @Id
    private String id;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String reservationId;
    private String paymentId;

    @Column(length = 512)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    protected Order() { /* JPA */ }

    public Order(String id, String sku, int quantity) {
        this.id = id;
        this.sku = sku;
        this.quantity = quantity;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getReservationId() { return reservationId; }
    public String getPaymentId() { return paymentId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }

    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public void markCompleted() { this.status = Status.COMPLETED; }
    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.failureReason = reason != null && reason.length() > 512 ? reason.substring(0, 512) : reason;
    }
}
