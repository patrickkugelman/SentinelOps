package io.sentinelops.agent.chaos;

import java.util.List;

/**
 * A pre-built chaos experiment the dashboard can trigger.
 *
 * @param id             stable id + classpath template basename (chaos/{id}.yaml)
 * @param kind           Chaos Mesh CR kind (PodChaos / NetworkChaos / StressChaos)
 * @param plural         CR plural for the Kubernetes API (irregular for Chaos Mesh)
 * @param title          human label for the UI
 * @param description    what it does / expected symptom
 * @param defaultTarget  demo service the template targets by default
 * @param supportsDuration whether a durationSeconds override applies
 * @param symptomType    the incident-signature symptom category it induces
 */
public record ExperimentDef(
        String id,
        String kind,
        String plural,
        String title,
        String description,
        String defaultTarget,
        boolean supportsDuration,
        String symptomType) {

    public static final List<ExperimentDef> ALL = List.of(
            new ExperimentDef("pod-kill", "PodChaos", "podchaos",
                    "Pod Kill",
                    "Kills one inventory-service pod; Kubernetes reschedules it.",
                    "inventory-service", false, "crash"),
            new ExperimentDef("network-delay", "NetworkChaos", "networkchaos",
                    "Network Delay",
                    "Adds 4s latency to inventory-service; cascades to checkout timeouts.",
                    "inventory-service", true, "latency"),
            new ExperimentDef("network-partition", "NetworkChaos", "networkchaos",
                    "Network Partition",
                    "Cuts order-service off from inventory-service; checkout fails fast.",
                    "order-service", true, "error-rate"),
            new ExperimentDef("cpu-stress", "StressChaos", "stresschaos",
                    "CPU Stress",
                    "Pins CPU on payment-service; payment latency and errors climb.",
                    "payment-service", true, "resource-exhaustion"));

    public static ExperimentDef byId(String id) {
        return ALL.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown experiment: " + id));
    }
}
