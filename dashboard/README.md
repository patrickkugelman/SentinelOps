# Dashboard — `dashboard/`

Vue 3 + Vite + TypeScript single-page app. **Implemented in Phase 7.**

## Panels
- **Cluster** — deployments + pod health (ready/desired, phase, restarts), polled.
- **Chaos experiments** — a button per experiment; triggers via the agent API and
  lists/stops active ones. Injecting also kicks off an agent response after ~5s.
- **Reasoning trace** — streams the agent's steps **live over SSE**
  (`/api/agent/stream`): anomaly → signature → retrieved precedents (with links to
  the source postmortem, the used one highlighted) → decision + justification →
  result → recovery + write-back.
- **Metrics · before/after** — live 5xx error-rate and p95 latency charts
  (SVG, no chart lib) from Prometheus, so you can watch the fix take hold.
- **Agent controls** — planner, memory size, live **dry-run toggle**, and the
  "respond to incident" trigger.

## Run

Dev (proxies `/api` → agent at `:8090`, so no CORS; SSE streams through):
```bash
cd dashboard
npm install
npm run dev            # http://localhost:5173  (AGENT_URL=… to point elsewhere)
```

Build / typecheck:
```bash
npm run build          # vue-tsc --noEmit && vite build
```

Container (nginx serving the build, proxying `/api` to `agent:8090` on the same
Docker network):
```bash
docker build -t sentinelops/dashboard dashboard
```

## Notes
- Types in `src/api.ts` mirror the agent's JSON contracts.
- Everything degrades gracefully: panels show "unavailable" if the cluster or
  Prometheus isn't up, while chaos/agent-state/memory still work.
