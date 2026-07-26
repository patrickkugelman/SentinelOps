package io.sentinelops.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the downstream HTTP clients with BOUNDED timeouts. The read timeout is
 * what turns a slow inventory-service into an order-service failure: once the
 * dependency is slower than this, calls fail fast and Tomcat worker threads
 * (capped via server.tomcat.threads.max) are freed instead of piling up
 * forever — the cascading-failure signal the agent will remediate.
 */
@Configuration
public class RestClientConfig {

    @Bean
    ClientHttpRequestFactory downstreamRequestFactory(
            @Value("${app.http.connect-timeout-ms:1000}") long connectMs,
            @Value("${app.http.read-timeout-ms:2000}") long readMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectMs))
                .withReadTimeout(Duration.ofMillis(readMs));
        return ClientHttpRequestFactories.get(settings);
    }

    @Bean
    RestClient inventoryRestClient(@Value("${app.inventory-url}") String baseUrl,
                                   ClientHttpRequestFactory factory) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    RestClient paymentRestClient(@Value("${app.payment-url}") String baseUrl,
                                 ClientHttpRequestFactory factory) {
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
