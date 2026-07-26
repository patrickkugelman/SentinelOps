package io.sentinelops.agent.chaos;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.sentinelops.agent.chaos.ChaosDtos.ChaosHandle;
import io.sentinelops.agent.chaos.ChaosDtos.TriggerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates, lists, and deletes Chaos Mesh experiments. Every experiment lives in
 * the allowed namespace only; anything else is rejected as a guardrail.
 *
 * The four experiment CRs are classpath templates (chaos/{id}.yaml) — the single
 * source of truth, also directly kubectl-applyable. {@link #render} loads a
 * template and applies overrides; it is pure (no cluster) so it is unit-testable.
 */
@Service
public class ChaosService {

    private static final Logger log = LoggerFactory.getLogger(ChaosService.class);
    private static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
    private static final String MANAGED_BY_VALUE = "sentinelops-agent";
    private static final String EXPERIMENT_LABEL = "sentinelops.io/experiment";
    private static final String NAME_SELECTOR = "app.kubernetes.io/name";

    private final KubernetesClient client;
    private final ChaosProperties props;

    public ChaosService(KubernetesClient client, ChaosProperties props) {
        this.client = client;
        this.props = props;
    }

    public List<ExperimentDef> catalog() {
        return ExperimentDef.ALL;
    }

    /** Load + parametrize a template into a ready-to-apply resource. Pure. */
    GenericKubernetesResource render(ExperimentDef def, TriggerRequest req) {
        String ns = props.namespace();
        guard(ns);

        GenericKubernetesResource cr = load(def.id());
        String target = (req != null && req.target() != null && !req.target().isBlank())
                ? req.target() : def.defaultTarget();

        // ---- metadata ----
        ObjectMeta md = cr.getMetadata() != null ? cr.getMetadata() : new ObjectMeta();
        md.setName(def.id() + "-" + shortId());
        md.setNamespace(ns);
        Map<String, String> labels = md.getLabels() != null ? new HashMap<>(md.getLabels()) : new HashMap<>();
        labels.put(MANAGED_BY_LABEL, MANAGED_BY_VALUE);
        labels.put(EXPERIMENT_LABEL, def.id());
        labels.put("sentinelops.io/target", target);
        md.setLabels(labels);
        cr.setMetadata(md);

        // ---- spec overrides ----
        Map<String, Object> spec = asMap(cr.getAdditionalProperties().get("spec"));
        // Point the primary selector at the (possibly overridden) target service.
        Map<String, Object> selector = asMap(spec.get("selector"));
        selector.put("namespaces", List.of(ns));
        Map<String, Object> labelSelectors = asMap(selector.get("labelSelectors"));
        labelSelectors.put(NAME_SELECTOR, target);

        if (def.supportsDuration() && req != null && req.durationSeconds() != null) {
            spec.put("duration", req.durationSeconds() + "s");
        }
        return cr;
    }

    /** Render + create the experiment in the cluster. */
    public ChaosHandle trigger(String experimentId, TriggerRequest req) {
        ExperimentDef def = ExperimentDef.byId(experimentId);
        GenericKubernetesResource cr = render(def, req);
        String target = cr.getMetadata().getLabels().get("sentinelops.io/target");

        log.info("Injecting chaos: experiment={} kind={} target={} namespace={} name={}",
                def.id(), def.kind(), target, props.namespace(), cr.getMetadata().getName());

        GenericKubernetesResource created = client.genericKubernetesResources(context(def))
                .inNamespace(props.namespace())
                .resource(cr)
                .create();
        return toHandle(created, def.id(), def.kind(), target);
    }

    /** All agent-managed experiments currently in the namespace. */
    public List<ChaosHandle> active() {
        guard(props.namespace());
        List<ChaosHandle> out = new ArrayList<>();
        for (ExperimentDef def : distinctKinds()) {
            var items = client.genericKubernetesResources(context(def))
                    .inNamespace(props.namespace())
                    .withLabel(MANAGED_BY_LABEL, MANAGED_BY_VALUE)
                    .list().getItems();
            for (GenericKubernetesResource item : items) {
                Map<String, String> l = item.getMetadata().getLabels();
                out.add(toHandle(item, l.getOrDefault(EXPERIMENT_LABEL, "unknown"),
                        item.getKind(), l.getOrDefault("sentinelops.io/target", "unknown")));
            }
        }
        return out;
    }

    /** Delete an experiment by name (searches all chaos kinds). Idempotent. */
    public boolean stop(String name) {
        guard(props.namespace());
        boolean deleted = false;
        for (ExperimentDef def : distinctKinds()) {
            var details = client.genericKubernetesResources(context(def))
                    .inNamespace(props.namespace())
                    .withName(name)
                    .delete();
            if (details != null && !details.isEmpty()) {
                deleted = true;
                log.info("Stopped chaos experiment {} ({})", name, def.kind());
            }
        }
        return deleted;
    }

    // ---- helpers ----

    private void guard(String ns) {
        if (!props.allowedNamespace().equals(ns)) {
            throw new SecurityException("chaos is restricted to namespace '"
                    + props.allowedNamespace() + "', refusing '" + ns + "'");
        }
    }

    private GenericKubernetesResource load(String id) {
        try (InputStream is = new ClassPathResource("chaos/" + id + ".yaml").getInputStream()) {
            return client.getKubernetesSerialization().unmarshal(is, GenericKubernetesResource.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load chaos template for " + id, e);
        }
    }

    private static ResourceDefinitionContext context(ExperimentDef def) {
        return new ResourceDefinitionContext.Builder()
                .withGroup("chaos-mesh.org")
                .withVersion("v1alpha1")
                .withKind(def.kind())
                .withPlural(def.plural())
                .withNamespaced(true)
                .build();
    }

    /** One ExperimentDef per distinct (kind, plural) so we don't list twice. */
    private static List<ExperimentDef> distinctKinds() {
        Map<String, ExperimentDef> byKind = new LinkedHashMap<>();
        for (ExperimentDef d : ExperimentDef.ALL) byKind.putIfAbsent(d.kind(), d);
        return new ArrayList<>(byKind.values());
    }

    private static ChaosHandle toHandle(GenericKubernetesResource cr, String id, String kind, String target) {
        String ts = cr.getMetadata().getCreationTimestamp();
        Instant started = ts != null ? Instant.parse(ts) : Instant.now();
        return new ChaosHandle(cr.getMetadata().getName(), id, kind, target,
                cr.getMetadata().getNamespace(), started);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalStateException("expected a YAML mapping but got: " + o);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
