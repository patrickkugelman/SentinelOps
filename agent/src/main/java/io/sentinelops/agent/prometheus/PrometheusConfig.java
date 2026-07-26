package io.sentinelops.agent.prometheus;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PrometheusConfig {

    @Bean
    RestClient prometheusRestClient(PrometheusProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(props.connectTimeout())
                .withReadTimeout(props.readTimeout());
        return RestClient.builder()
                .baseUrl(props.url())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Bean
    PrometheusClient prometheusClient(RestClient prometheusRestClient) {
        return new PrometheusClient(prometheusRestClient);
    }
}
