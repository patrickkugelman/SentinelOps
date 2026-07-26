package io.sentinelops.agent.prometheus;

/** Raised when Prometheus is unreachable or returns a non-success payload. */
public class PrometheusException extends RuntimeException {
    public PrometheusException(String message) {
        super(message);
    }

    public PrometheusException(String message, Throwable cause) {
        super(message, cause);
    }
}
