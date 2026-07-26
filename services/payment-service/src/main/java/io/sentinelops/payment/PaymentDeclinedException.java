package io.sentinelops.payment;

public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String orderRef) {
        super("payment declined for order " + orderRef);
    }
}
