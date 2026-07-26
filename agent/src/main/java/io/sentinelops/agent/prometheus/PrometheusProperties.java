package io.sentinelops.agent.prometheus;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration for reaching Prometheus (prefix: {@code prometheus}). */
@ConfigurationProperties(prefix = "prometheus")
public record PrometheusProperties(
        String url,
        Duration connectTimeout,
        Duration readTimeout) {

    public PrometheusProperties {
        if (url == null || url.isBlank()) url = "http://localhost:30090";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(5);
    }
}
