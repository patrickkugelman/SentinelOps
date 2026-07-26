package io.sentinelops.agent.remediate;

import io.sentinelops.agent.cluster.ClusterState;
import io.sentinelops.agent.detect.Anomaly;
import io.sentinelops.agent.memory.ScoredIncident;

import java.util.List;

/**
 * Decides how to remediate an anomaly, reasoning over the retrieved precedents.
 * Implementations: rule-based (default, offline) and LLM (OpenAI-compatible).
 */
public interface RemediationPlanner {

    RemediationDecision plan(Anomaly anomaly, List<ScoredIncident> precedents, ClusterState clusterState);

    String name();
}
