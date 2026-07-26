<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, type AgentTrace, type ExperimentDef } from './api'
import { useAgentStream } from './composables/useAgentStream'
import ClusterPanel from './components/ClusterPanel.vue'
import ChaosPanel from './components/ChaosPanel.vue'
import AgentControls from './components/AgentControls.vue'
import ReasoningTrace from './components/ReasoningTrace.vue'
import MetricsPanel from './components/MetricsPanel.vue'

const { trace: streamTrace, connected } = useAgentStream()
const lastResult = ref<AgentTrace | null>(null)
const busy = ref(false)
const hint = ref<string | null>(null)

// Prefer the live-streamed trace; fall back to the POST result if SSE is down.
const displayTrace = computed(() => streamTrace.value ?? lastResult.value)

async function respond(service?: string, type?: string) {
  busy.value = true
  hint.value = null
  try {
    lastResult.value = await api.respond(service, type)
  } catch (e) {
    hint.value = 'respond failed: ' + (e as Error).message
  } finally {
    busy.value = false
  }
}

function onInjected(exp: ExperimentDef) {
  hint.value = `Injected ${exp.title}. Agent will respond in ~5s…`
  window.setTimeout(() => respond(exp.defaultTarget, exp.symptomType), 5000)
}
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">
        <div class="logo">S</div>
        <div>
          <h1>SentinelOps</h1>
          <div class="sub">Self-healing Kubernetes with retrospective incident memory</div>
        </div>
      </div>
      <div class="row">
        <a class="pill mut" href="https://github.com" target="_blank" rel="noopener">docs</a>
      </div>
    </header>

    <div class="grid">
      <div class="col">
        <ClusterPanel />
        <ChaosPanel @injected="onInjected" />
      </div>

      <div class="col">
        <AgentControls :connected="connected" :busy="busy" @respond="respond()" />
        <p v-if="hint" class="hint small">{{ hint }}</p>
        <ReasoningTrace :trace="displayTrace" />
        <MetricsPanel />
      </div>
    </div>
  </div>
</template>

<style scoped>
.hint {
  margin: 0; padding: 8px 12px; border-radius: 8px;
  background: #1b2438; border: 1px solid var(--border); color: var(--muted);
}
</style>
