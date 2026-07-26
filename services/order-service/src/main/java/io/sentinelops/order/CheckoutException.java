package io.sentinelops.order;

/** Wraps any failure during checkout orchestration. */
public class CheckoutException extends RuntimeException {
    public CheckoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
