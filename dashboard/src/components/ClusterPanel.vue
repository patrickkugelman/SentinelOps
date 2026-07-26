<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { api, type ClusterState } from '../api'

const state = ref<ClusterState | null>(null)
const error = ref<string | null>(null)
let timer: number | undefined

async function refresh() {
  try {
    state.value = await api.clusterState()
    error.value = null
  } catch (e) {
    error.value = (e as Error).message
  }
}

onMounted(() => { refresh(); timer = window.setInterval(refresh, 5000) })
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div class="card">
    <div class="row">
      <h3>Cluster · <span class="mono">{{ state?.namespace ?? '…' }}</span></h3>
      <div class="spacer"></div>
      <button class="btn sm" @click="refresh">↻</button>
    </div>

    <p v-if="error" class="small" style="color: var(--err)">cluster unavailable — {{ error }}</p>

    <div v-for="d in state?.deployments ?? []" :key="d.name" class="dep">
      <div class="row">
        <span class="mono name">{{ d.name }}</span>
        <div class="spacer"></div>
        <span class="pill" :class="d.readyReplicas >= d.desiredReplicas ? 'ok' : 'err'">
          {{ d.readyReplicas }}/{{ d.desiredReplicas }} ready
        </span>
      </div>
      <div class="pods">
        <span v-for="p in d.pods" :key="p.name"
              class="pod" :class="p.ready ? 'ok' : 'bad'"
              :title="`${p.name} · ${p.phase} · restarts ${p.restartCount}`">
          <span class="dot"></span>{{ p.phase }}<span v-if="p.restartCount" class="rc">↻{{ p.restartCount }}</span>
        </span>
      </div>
    </div>

    <p v-if="state && state.deployments.length === 0" class="muted small">no deployments in namespace</p>
  </div>
</template>

<style scoped>
.dep { padding: 8px 0; border-top: 1px solid var(--border); }
.dep:first-of-type { border-top: none; }
.name { font-weight: 600; }
.pods { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.pod {
  display: inline-flex; align-items: center; gap: 5px; font-size: 11px;
  padding: 2px 7px; border-radius: 6px; border: 1px solid var(--border); background: #0e1420;
}
.pod .dot { width: 7px; height: 7px; border-radius: 50%; }
.pod.ok .dot { background: var(--ok); box-shadow: 0 0 6px var(--ok); }
.pod.bad { color: var(--err); }
.pod.bad .dot { background: var(--err); box-shadow: 0 0 6px var(--err); }
.rc { color: var(--warn); }
</style>
