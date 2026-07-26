package io.sentinelops.agent.detect;

import io.sentinelops.agent.memory.IncidentSignature;

import java.util.List;
import java.util.Map;

/**
 * Turns a detected {@link Anomaly} into a structured {@link IncidentSignature}
 * for retrieval — mapping the anomaly type to symptom type, an error-pattern
 * category, and likely service types.
 */
public final class SignatureFactory {

    private SignatureFactory() { }

    private static final Map<Anomaly.Type, String> SYMPTOM = Map.of(
            Anomaly.Type.CRASH, "crash",
            Anomaly.Type.ERROR_RATE, "error-rate",
            Anomaly.Type.RESOURCE_EXHAUSTION, "resource-exhaustion",
            Anomaly.Type.LATENCY, "latency");

    private static final Map<Anomaly.Type, String> ERROR_CATEGORY = Map.of(
            Anomaly.Type.CRASH, "process-crash",
            Anomaly.Type.ERROR_RATE, "http-5xx",
            Anomaly.Type.RESOURCE_EXHAUSTION, "cpu-saturation",
            Anomaly.Type.LATENCY, "network-latency");

    public static IncidentSignature from(Anomaly anomaly) {
        String symptom = SYMPTOM.getOrDefault(anomaly.type(), "error-rate");
        String category = ERROR_CATEGORY.getOrDefault(anomaly.type(), "unknown");
        List<String> serviceTypes = inferServiceTypes(anomaly.service(), anomaly.type());
        String description = "%s on %s: %s".formatted(symptom, anomaly.service(), anomaly.summary());
        return new IncidentSignature(serviceTypes, symptom, category, description);
    }

    /** Best-effort mapping of a demo service + anomaly to knowledge-base service types. */
    private static List<String> inferServiceTypes(String service, Anomaly.Type type) {
        String base = switch (service == null ? "" : service) {
            case "order-service" -> "api";
            case "inventory-service", "payment-service" -> "api";
            default -> "api";
        };
        return switch (type) {
            case LATENCY -> List.of("network", base);
            case ERROR_RATE -> List.of("network", base);
            case RESOURCE_EXHAUSTION -> List.of("compute", base);
            case CRASH -> List.of("compute", base);
        };
    }
}
