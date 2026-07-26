package io.sentinelops.agent.prometheus.model;

import java.time.Instant;
import java.util.Map;

/** A single instant-query result: one time series' labels + its current value. */
public record Sample(Map<String, String> labels, double value, Instant timestamp) {
}
