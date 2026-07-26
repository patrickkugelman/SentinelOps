<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { api, DEMO_NS, type Series } from '../api'
import MiniChart from './MiniChart.vue'

const errorRate = ref<Series[]>([])
const latency = ref<Series[]>([])
const error = ref<string | null>(null)
let timer: number | undefined

const ERR_Q = `sum by (app) (rate(http_server_requests_seconds_count{namespace="${DEMO_NS}",status=~"5.."}[1m]))`
const LAT_Q = `histogram_quantile(0.95, sum by (app, le) (rate(http_server_requests_seconds_bucket{namespace="${DEMO_NS}"}[1m])))`

async function refresh() {
  try {
    const [e, l] = await Promise.all([
      api.queryRange(ERR_Q, 10, 15),
      api.queryRange(LAT_Q, 10, 15),
    ])
    errorRate.value = e
    latency.value = l
    error.value = null
  } catch (err) {
    error.value = (err as Error).message
  }
}

onMounted(() => { refresh(); timer = window.setInterval(refresh, 5000) })
onUnmounted(() => window.clearInterval(timer))
</script>

<template>
  <div class="card">
    <div class="row">
      <h3 style="margin:0">Metrics · before / after</h3>
      <div class="spacer"></div>
      <span class="muted small">last 10m · live</span>
    </div>
    <p v-if="error" class="small" style="color: var(--err)">Prometheus unavailable — {{ error }}</p>
    <div class="charts">
      <MiniChart title="5xx error rate (req/s)" :series="errorRate" />
      <MiniChart title="p95 latency (s)" :series="latency" unit="s" />
    </div>
  </div>
</template>

<style scoped>
.charts { display: flex; gap: 20px; flex-wrap: wrap; margin-top: 10px; }
</style>
