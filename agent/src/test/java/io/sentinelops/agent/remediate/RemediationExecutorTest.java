package io.sentinelops.agent.remediate;

import io.sentinelops.agent.cluster.ClusterOperations;
import io.sentinelops.agent.orchestrator.AgentProperties;
import io.sentinelops.agent.orchestrator.AgentRuntime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RemediationExecutorTest {

    private final AgentProperties props =
            new AgentProperties("sentinelops-demo", "sentinelops-demo", false, false, 20, 0.10, 1.0, 0, 4, "rule");

    private static RemediationDecision restart(String ns) {
        return new RemediationDecision(RemediationAction.RESTART, "order-service", ns, 0,
                "because precedent said so", "p1", "T", "https://x", "rule");
    }

    @Test
    void executesRestartWhenNotDryRun() {
        ClusterOperations cluster = mock(ClusterOperations.class);
        AgentRuntime runtime = new AgentRuntime(props); // dryRun=false
        RemediationExecutor ex = new RemediationExecutor(cluster, props, runtime);

        RemediationExecutor.Result r = ex.execute(restart("sentinelops-demo"));

        assertThat(r.executed()).isTrue();
        verify(cluster).restart("order-service");
    }

    @Test
    void dryRunDoesNotTouchTheCluster() {
        ClusterOperations cluster = mock(ClusterOperations.class);
        AgentRuntime runtime = new AgentRuntime(props);
        runtime.setDryRun(true);
        RemediationExecutor ex = new RemediationExecutor(cluster, props, runtime);

        RemediationExecutor.Result r = ex.execute(restart("sentinelops-demo"));

        assertThat(r.executed()).isFalse();
        assertThat(r.dryRun()).isTrue();
        verify(cluster, never()).restart(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void refusesRemediationOutsideAllowedNamespace() {
        ClusterOperations cluster = mock(ClusterOperations.class);
        RemediationExecutor ex = new RemediationExecutor(cluster, props, new AgentRuntime(props));

        assertThatThrownBy(() -> ex.execute(restart("kube-system")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("allowed namespace");
        verifyNoInteractions(cluster);
    }

    @Test
    void rollbackFallsBackToRestartWhenThereIsNoPreviousRevision() {
        ClusterOperations cluster = mock(ClusterOperations.class);
        when(cluster.hasPreviousRevision("order-service")).thenReturn(false);
        RemediationExecutor ex = new RemediationExecutor(cluster, props, new AgentRuntime(props));
        RemediationDecision rollback = new RemediationDecision(RemediationAction.ROLLBACK, "order-service",
                "sentinelops-demo", 0, "precedent says bad deploy", "p1", "T", "https://x", "rule");

        RemediationExecutor.Result r = ex.execute(rollback);

        verify(cluster).restart("order-service");
        verify(cluster, never()).rollback(org.mockito.ArgumentMatchers.anyString());
        assertThat(r.executed()).isTrue();
        assertThat(r.message()).contains("no previous revision").contains("rolling restart");
    }

    @Test
    void rollbackIsPerformedWhenAPreviousRevisionExists() {
        ClusterOperations cluster = mock(ClusterOperations.class);
        when(cluster.hasPreviousRevision("order-service")).thenReturn(true);
        RemediationExecutor ex = new RemediationExecutor(cluster, props, new AgentRuntime(props));
        RemediationDecision rollback = new RemediationDecision(RemediationAction.ROLLBACK, "order-service",
                "sentinelops-demo", 0, "precedent says bad deploy", "p1", "T", "https://x", "rule");

        ex.execute(rollback);

        verify(cluster).rollback("order-service");
        verify(cluster, never()).restart(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void scaleUsesDecisionReplicas() {
        ClusterOperations cluster = mock(ClusterOperations.class);
        RemediationExecutor ex = new RemediationExecutor(cluster, props, new AgentRuntime(props));
        RemediationDecision scale = new RemediationDecision(RemediationAction.SCALE, "payment-service",
                "sentinelops-demo", 3, "add capacity", "p2", "T", "https://x", "rule");

        ex.execute(scale);

        verify(cluster).scale("payment-service", 3);
    }
}
