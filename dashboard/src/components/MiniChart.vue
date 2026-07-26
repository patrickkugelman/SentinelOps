<script setup lang="ts">
import { computed } from 'vue'
import type { Series } from '../api'

const props = defineProps<{ title: string; series: Series[]; unit?: string }>()

const COLORS: Record<string, string> = {
  'order-service': '#4f8cff',
  'inventory-service': '#7c5cff',
  'payment-service': '#2ecc71',
}
function colorFor(s: Series, i: number) {
  return COLORS[s.labels.app] ?? ['#f4b740', '#ff5c7a', '#41d0c0'][i % 3]
}

const W = 300, H = 90, PAD = 6

const model = computed(() => {
  const all = props.series.flatMap((s) => s.points.map((p) => p.value)).filter((v) => Number.isFinite(v))
  const max = Math.max(0.0001, ...all)
  const lines = props.series.map((s, i) => {
    const n = Math.max(1, s.points.length - 1)
    const pts = s.points
      .filter((p) => Number.isFinite(p.value))
      .map((p, idx) => {
        const x = PAD + (idx / n) * (W - 2 * PAD)
        const y = H - PAD - (p.value / max) * (H - 2 * PAD)
        return `${x.toFixed(1)},${y.toFixed(1)}`
      })
      .join(' ')
    return { app: s.labels.app ?? `series-${i}`, color: colorFor(s, i), pts, last: s.points.at(-1)?.value ?? 0 }
  })
  return { max, lines }
})
</script>

<template>
  <div class="chart">
    <div class="row">
      <span class="ctitle">{{ title }}</span>
      <div class="spacer"></div>
      <span class="muted small mono">max {{ model.max.toFixed(2) }}{{ unit ?? '' }}</span>
    </div>
    <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" class="svg">
      <line :x1="PAD" :y1="H - PAD" :x2="W - PAD" :y2="H - PAD" class="axis" />
      <polyline v-for="l in model.lines" :key="l.app" :points="l.pts" fill="none" :stroke="l.color" stroke-width="1.6" />
    </svg>
    <div class="legend">
      <span v-for="l in model.lines" :key="l.app" class="lg">
        <span class="sw" :style="{ background: l.color }"></span>{{ l.app }}
      </span>
      <span v-if="!model.lines.length" class="muted small">no data</span>
    </div>
  </div>
</template>

<style scoped>
.chart { flex: 1; min-width: 240px; }
.ctitle { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: .5px; }
.svg { width: 100%; height: 90px; display: block; margin: 6px 0; }
.axis { stroke: var(--border); stroke-width: 1; }
.legend { display: flex; gap: 12px; flex-wrap: wrap; }
.lg { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; color: var(--muted); }
.sw { width: 10px; height: 3px; border-radius: 2px; }
</style>
