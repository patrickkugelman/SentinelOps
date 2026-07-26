package io.sentinelops.agent.prometheus;

import io.sentinelops.agent.prometheus.model.Sample;
import io.sentinelops.agent.prometheus.model.Series;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

class PrometheusClientTest {

    private MockRestServiceServer server;
    private PrometheusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prom.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PrometheusClient(builder.build());
    }

    @Test
    void parsesInstantVectorIntoSamples() {
        String body = """
            {"status":"success","data":{"resultType":"vector","result":[
              {"metric":{"app":"order-service","namespace":"sentinelops-demo"},"value":[1700000000.5,"3.5"]},
              {"metric":{"app":"inventory-service"},"value":[1700000000.5,"1"]}
            ]}}""";
        server.expect(requestTo(containsString("/api/v1/query")))
              .andExpect(queryParam("query", "up"))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<Sample> samples = client.query("up");

        assertThat(samples).hasSize(2);
        Sample first = samples.get(0);
        assertThat(first.labels()).containsEntry("app", "order-service");
        assertThat(first.value()).isEqualTo(3.5);
        assertThat(first.timestamp()).isEqualTo(Instant.ofEpochMilli(1700000000500L));
        server.verify();
    }

    @Test
    void parsesRangeMatrixIntoSeriesWithPoints() {
        String body = """
            {"status":"success","data":{"resultType":"matrix","result":[
              {"metric":{"app":"order-service"},"values":[[1700000000,"0.1"],[1700000015,"0.2"],[1700000030,"0.25"]]}
            ]}}""";
        server.expect(requestTo(containsString("/api/v1/query_range")))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Instant end = Instant.parse("2023-11-14T22:13:50Z");
        List<Series> series = client.queryRange("rate(x[1m])", end.minus(Duration.ofMinutes(1)), end, Duration.ofSeconds(15));

        assertThat(series).hasSize(1);
        assertThat(series.get(0).labels()).containsEntry("app", "order-service");
        assertThat(series.get(0).points()).hasSize(3);
        assertThat(series.get(0).points().get(2).value()).isEqualTo(0.25);
        server.verify();
    }

    /** PromQL label selectors contain braces, which must NOT be treated as URI templates. */
    @Test
    void handlesPromqlContainingBracesAndQuotes() {
        String body = """
            {"status":"success","data":{"resultType":"vector","result":[
              {"metric":{"app":"order-service"},"value":[1700000000,"0.5"]}
            ]}}""";
        String promql = "sum by (app) (rate(http_server_requests_seconds_count"
                + "{namespace=\"sentinelops-demo\",status=~\"5..\"}[1m]))";
        // The braces must reach the wire percent-encoded (%7B/%7D), not expanded.
        server.expect(requestTo(containsString("%7Bnamespace%3D%22sentinelops-demo%22")))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<Sample> samples = client.query(promql);

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).value()).isEqualTo(0.5);
        server.verify();
    }

    @Test
    void scalarReturnsFirstValueOrEmpty() {
        String body = """
            {"status":"success","data":{"resultType":"vector","result":[
              {"metric":{},"value":[1700000000,"42"]}
            ]}}""";
        server.expect(requestTo(containsString("/api/v1/query")))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.queryScalar("vector(42)")).contains(42.0);
        server.verify();
    }

    @Test
    void surfacesPrometheusErrorStatusAsException() {
        String body = """
            {"status":"error","errorType":"bad_data","error":"parse error at char 1"}""";
        server.expect(requestTo(containsString("/api/v1/query")))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.query("this is not promql"))
                .isInstanceOf(PrometheusException.class)
                .hasMessageContaining("parse error");
    }

    @Test
    void surfacesHttpErrorAsException() {
        server.expect(requestTo(containsString("/api/v1/query")))
              .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.query("bad"))
                .isInstanceOf(PrometheusException.class);
    }
}
