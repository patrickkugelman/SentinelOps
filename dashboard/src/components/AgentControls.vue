<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type AgentState } from '../api'

const props = defineProps<{ connected: boolean; busy: boolean }>()
const emit = defineEmits<{ (e: 'respond'): void }>()

const state = ref<AgentState | null>(null)
const memory = ref<number | null>(null)

async function load() {
  try { state.value = await api.agentState() } catch { /* agent may be down */ }
  try { memory.value = (await api.incidentCount()).count } catch { /* db may be down */ }
}
async function toggleDryRun() {
  if (!state.value) return
  const r = await api.setDryRun(!state.value.dryRun)
  state.value = { ...state.value, dryRun: r.dryRun }
}
onMounted(load)
defineExpose({ reload: load })
</script>

<template>
  <div class="card controls">
    <div class="row">
      <h3 style="margin: 0">Agent</h3>
      <span class="pill" :class="props.connected ? 'ok' : 'err'">
        {{ props.connected ? 'stream live' : 'stream offline' }}
      </span>
      <div class="spacer"></div>
      <span v-if="state" class="pill mut">planner: {{ state.planner }}</span>
      <span v-if="memory !== null" class="pill mut">memory: {{ memory }}</span>
    </div>

    <div class="row" style="margin-top: 12px">
      <button class="btn primary" :disabled="props.busy" @click="emit('respond')">
        {{ props.busy ? 'Responding…' : '▶ Agent: respond to incident' }}
      </button>

      <div class="spacer"></div>

      <div class="switch" :class="{ on: state?.dryRun }" @click="toggleDryRun" title="Dry-run: reason & log, but don't touch the cluster">
        <span class="track"><span class="thumb"></span></span>
        <span class="small">dry-run</span>
      </div>
    </div>
    <p class="muted small" style="margin: 8px 0 0">
      Namespace <span class="mono">{{ state?.namespace ?? '—' }}</span> ·
      remediation is refused anywhere else.
    </p>
  </div>
</template>

<style scoped>
.controls { position: sticky; top: 0; z-index: 2; }
</style>
