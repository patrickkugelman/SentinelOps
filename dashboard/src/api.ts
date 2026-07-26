// Typed client for the SentinelOps agent API (proxied via /api in dev).

export interface PodState { name: string; phase: string; ready: boolean; restartCount: number }
export interface DeploymentState {
  name: string
  desiredReplicas: number
  readyReplicas: number
  pods: PodState[]
}
export interface ClusterState { namespace: string; deployments: DeploymentState[] }

export interface AgentState {
  namespace: string
  allowedNamespace: string
  dryRun: boolean
  planner: string
  autoRemediate: boolean
  verifySeconds: number
}

export interface ExperimentDef {
  id: string
  kind: string
  title: string
  description: string
  defaultTarget: string
  supportsDuration: boolean
  symptomType: string
}
export interface ChaosHandle {
  name: string
  experimentId: string
  kind: string
  target: string
  namespace: string
  startedAt: string
}

export interface Anomaly {
  service: string
  type: string
  observedValue: number
  threshold: number
  summary: string
}
export interface IncidentSignature {
  serviceTypes: string[]
  symptomType: string
  errorPatternCategory: string
  description: string
}
export interface PrecedentView {
  id: string
  title: string
  sourceUrl: string
  score: number
  symptomType: string
  errorPatternCategory: string
}
export interface RemediationDecision {
  action: string
  targetService: string | null
  namespace: string
  replicas: number
  justification: string
  precedentId: string | null
  precedentTitle: string | null
  precedentUrl: string | null
  planner: string
}
export interface RemediationResult {
  executed: boolean
  dryRun: boolean
  action: string
  target: string | null
  message: string
}
export interface TraceStep { at: string; phase: string; message: string }
export interface AgentTrace {
  id: string
  startedAt: string
  dryRun: boolean
  planner: string
  steps: TraceStep[]
  anomaly: Anomaly | null
  signature: IncidentSignature | null
  precedents: PrecedentView[]
  decision: RemediationDecision | null
  result: RemediationResult | null
  beforeMetrics: Record<string, number> | null
  afterMetrics: Record<string, number> | null
  recovered: boolean | null
  recordedIncidentId: string | null
  finished: boolean
}

export interface SeriesPoint { timestamp: string; value: number }
export interface Series { labels: Record<string, string>; points: SeriesPoint[] }

async function json<T>(res: Response): Promise<T> {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json() as Promise<T>
}

export const api = {
  agentState: () => fetch('/api/agent/state').then(json<AgentState>),
  setDryRun: (enabled: boolean) =>
    fetch(`/api/agent/dry-run?enabled=${enabled}`, { method: 'POST' }).then(json<{ dryRun: boolean }>),
  clusterState: () => fetch('/api/agent/cluster').then(json<ClusterState>),
  respond: (service?: string, type?: string) => {
    const q = new URLSearchParams()
    if (service) q.set('service', service)
    if (type) q.set('type', type)
    return fetch(`/api/agent/respond?${q.toString()}`, { method: 'POST' }).then(json<AgentTrace>)
  },
  traces: () => fetch('/api/agent/traces').then(json<AgentTrace[]>),

  experiments: () => fetch('/api/chaos/experiments').then(json<ExperimentDef[]>),
  triggerChaos: (id: string, body?: { target?: string; durationSeconds?: number }) =>
    fetch(`/api/chaos/experiments/${id}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body ?? {}),
    }).then(json<ChaosHandle>),
  activeChaos: () => fetch('/api/chaos/active').then(json<ChaosHandle[]>),
  stopChaos: (name: string) =>
    fetch(`/api/chaos/active/${name}`, { method: 'DELETE' }).then(json<unknown>),

  incidentCount: () => fetch('/api/incidents/count').then(json<{ count: number }>),
  queryRange: (q: string, minutes = 15, stepSeconds = 15) =>
    fetch(`/api/prometheus/query_range?q=${encodeURIComponent(q)}&minutes=${minutes}&stepSeconds=${stepSeconds}`)
      .then(json<Series[]>),
}

export const DEMO_NS = 'sentinelops-demo'
