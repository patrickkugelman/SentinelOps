<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { api, type ChaosHandle, type ExperimentDef } from '../api'

const emit = defineEmits<{ (e: 'injected', experiment: ExperimentDef): void }>()

const experiments = ref<ExperimentDef[]>([])
const active = ref<ChaosHandle[]>([])
const busy = ref<string | null>(null)
const error = ref<string | null>(null)
let timer: number | undefined

const ICONS: Record<string, string> = {
  'pod-kill': '💀', 'network-delay': '🐌', 'network-partition': '✂️', 'cpu-stress': '🔥',
}

async function loadExperiments() {
  try { experiments.value = await api.experiments() } catch (e) { error.value = (e as Error).message }
}
async function refreshActive() {
  try { active.value = await api.activeChaos(); error.value = null } catch (e) { error.value = (e as Error).message }
}
async function trigger(exp: ExperimentDef) {
  busy.value = exp.id
  try {
    await api.triggerChaos(exp.id, exp.supportsDuration ? { durationSeconds: 60 } : {})
    await refreshActive()
    emit('injected', exp)
  } catch (e) { error.value = (e as Error).message } finally { busy.value = null }
}
async function stop(h: ChaosHandle) {
  try { await api.stopChaos(h.name); await refreshActive() } catch (e) { error.value = (e as Error).message }
}

onMounted(() => { loadExperiments(); refreshActive(); timer = window.setInterval(refreshActive, 5000) })
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div class="card">
    <h3>Chaos experiments</h3>
    <p v-if="error" class="small" style="color: var(--err)">{{ error }}</p>

    <div class="exps">
      <button v-for="exp in experiments" :key="exp.id" class="exp" :disabled="busy === exp.id"
              :title="exp.description" @click="trigger(exp)">
        <span class="ico">{{ ICONS[exp.id] ?? '⚡' }}</span>
        <span class="meta">
          <span class="t">{{ exp.title }}</span>
          <span class="d muted">→ {{ exp.defaultTarget }} · {{ exp.symptomType }}</span>
        </span>
        <span v-if="busy === exp.id" class="mono small">…</span>
      </button>
    </div>

    <div v-if="active.length" class="active">
      <div class="muted small" style="margin: 8px 0 4px">Active</div>
      <div v-for="h in active" :key="h.name" class="row arow">
        <span class="pill warn">{{ h.experimentId }}</span>
        <span class="mono small">{{ h.target }}</span>
        <div class="spacer"></div>
        <button class="btn sm danger" @click="stop(h)">stop</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.exps { display: flex; flex-direction: column; gap: 8px; }
.exp {
  display: flex; align-items: center; gap: 10px; text-align: left; cursor: pointer;
  background: #141d30; border: 1px solid var(--border); border-radius: 10px; padding: 9px 11px; color: var(--text);
  transition: all .15s;
}
.exp:hover:not(:disabled) { border-color: var(--err); background: #1b2438; }
.exp:disabled { opacity: .6; }
.exp .ico { font-size: 18px; }
.exp .meta { display: flex; flex-direction: column; }
.exp .t { font-weight: 600; font-size: 13px; }
.exp .d { font-size: 11px; }
.arow { padding: 4px 0; }
</style>
