package io.sentinelops.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment {

    public enum Status { AUTHORIZED, CAPTURED, DECLINED, VOIDED }

    @Id
    private String id;

    @Column(nullable = false)
    private String orderRef;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    protected Payment() { /* JPA */ }

    public Payment(String id, String orderRef, BigDecimal amount, Status status) {
        this.id = id;
        this.orderRef = orderRef;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getOrderRef() { return orderRef; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void capture() { this.status = Status.CAPTURED; }
    public void voidPayment() { this.status = Status.VOIDED; }
}
