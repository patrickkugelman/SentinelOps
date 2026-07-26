package io.sentinelops.agent.prometheus.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A range-query result: one time series' labels + its points over time. */
public record Series(Map<String, String> labels, List<Point> points) {

    public record Point(Instant timestamp, double value) { }
}
