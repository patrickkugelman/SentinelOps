<script setup lang="ts">
import { computed } from 'vue'
import type { AgentTrace } from '../api'

const props = defineProps<{ trace: AgentTrace | null }>()

const PHASE_ICON: Record<string, string> = {
  detect: '🔍', anomaly: '🚨', signature: '🧬', retrieve: '📚',
  reason: '🧠', remediate: '🛠️', verify: '📈', record: '💾', done: '✅',
}
const ACTION_CLASS: Record<string, string> = {
  RESTART: 'warn', SCALE: 'ok', ROLLBACK: 'err', NONE: 'mut',
}

const t = computed(() => props.trace)
function fmt(v: number | undefined) { return v === undefined ? '—' : v.toFixed(3) }
</script>

<template>
  <div class="card trace">
    <div class="row">
      <h3 style="margin:0">Reasoning trace</h3>
      <span v-if="t?.dryRun" class="pill warn">dry-run</span>
      <div class="spacer"></div>
      <span v-if="t" class="mono small muted">{{ t.id.slice(0, 8) }}</span>
    </div>

    <div v-if="!t" class="empty muted">
      Trigger a chaos experiment, then hit <b>Agent: respond</b>. The agent's
      steps stream here live — anomaly, retrieved precedent, decision, and outcome.
    </div>

    <template v-else>
      <!-- step timeline -->
      <ol class="steps">
        <li v-for="(s, i) in t.steps" :key="i" :class="{ last: i === t.steps.length - 1 }">
          <span class="ph">{{ PHASE_ICON[s.phase] ?? '•' }}</span>
          <span class="txt"><b class="mono">{{ s.phase }}</b> {{ s.message }}</span>
        </li>
      </ol>

      <!-- anomaly + signature -->
      <div v-if="t.anomaly" class="block">
        <div class="row">
          <span class="pill err">{{ t.anomaly.type }}</span>
          <span class="mono">{{ t.anomaly.service }}</span>
          <span class="muted small">{{ t.anomaly.summary }}</span>
        </div>
        <div v-if="t.signature" class="row small muted" style="margin-top:6px">
          signature: symptom=<b>{{ t.signature.symptomType }}</b>,
          category=<b>{{ t.signature.errorPatternCategory }}</b>,
          types=[{{ t.signature.serviceTypes.join(', ') }}]
        </div>
      </div>

      <!-- retrieved precedents -->
      <div v-if="t.precedents.length" class="block">
        <div class="muted small" style="margin-bottom:6px">Retrieved precedents (institutional memory)</div>
        <div v-for="p in t.precedents" :key="p.id" class="prec"
             :class="{ chosen: p.id === t.decision?.precedentId }">
          <div class="row">
            <a :href="p.sourceUrl" target="_blank" rel="noopener" class="ptitle">{{ p.title }}</a>
            <div class="spacer"></div>
            <span class="pill mut">{{ p.symptomType }}</span>
            <span class="score" :style="{ '--w': Math.round(Math.min(p.score,1)*100) + '%' }">
              <b>{{ p.score.toFixed(2) }}</b>
            </span>
          </div>
          <div class="mono small muted">{{ p.id }} · {{ p.errorPatternCategory }}
            <span v-if="p.id === t.decision?.precedentId" class="pill ok" style="margin-left:6px">used</span>
          </div>
        </div>
      </div>

      <!-- decision -->
      <div v-if="t.decision" class="block decision">
        <div class="row">
          <span class="pill" :class="ACTION_CLASS[t.decision.action] ?? 'mut'">{{ t.decision.action }}</span>
          <span class="mono" v-if="t.decision.targetService">{{ t.decision.targetService }}</span>
          <span v-if="t.decision.action === 'SCALE'" class="muted small">→ {{ t.decision.replicas }} replicas</span>
          <div class="spacer"></div>
          <span class="muted small">via {{ t.decision.planner }} planner</span>
        </div>
        <p class="just">{{ t.decision.justification }}</p>
      </div>

      <!-- result + recovery -->
      <div v-if="t.result" class="block row">
        <span class="pill" :class="t.result.executed ? 'ok' : 'warn'">
          {{ t.result.executed ? 'executed' : (t.result.dryRun ? 'dry-run' : 'no-op') }}
        </span>
        <span class="mono small">{{ t.result.message }}</span>
        <div class="spacer"></div>
        <span v-if="t.recovered !== null" class="pill" :class="t.recovered ? 'ok' : 'warn'">
          {{ t.recovered ? 'recovering' : 'watching' }}
        </span>
      </div>

      <!-- before/after -->
      <div v-if="t.beforeMetrics" class="block row metrics">
        <div class="mtile">
          <div class="mlabel">before · err ratio</div>
          <div class="mval">{{ fmt(t.beforeMetrics.errorRatio) }}</div>
        </div>
        <div class="arrow">→</div>
        <div class="mtile">
          <div class="mlabel">after · err ratio</div>
          <div class="mval">{{ fmt(t.afterMetrics?.errorRatio) }}</div>
        </div>
        <div v-if="t.recordedIncidentId" class="spacer"></div>
        <span v-if="t.recordedIncidentId" class="pill ok" title="written back to incident memory">
          💾 recorded
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.trace { min-height: 320px; }
.empty { padding: 40px 10px; text-align: center; max-width: 460px; margin: 0 auto; }
.steps { list-style: none; margin: 12px 0; padding: 0; }
.steps li { display: flex; gap: 10px; padding: 4px 0; opacity: .8; }
.steps li.last { opacity: 1; }
.steps .ph { width: 20px; text-align: center; }
.steps .txt { font-size: 13px; }
.block { border-top: 1px solid var(--border); padding: 12px 0; }
.prec { padding: 8px; border: 1px solid var(--border); border-radius: 9px; margin-bottom: 8px; background: #0e1420; }
.prec.chosen { border-color: var(--ok); box-shadow: 0 0 0 1px rgba(46,204,113,.25) inset; }
.ptitle { font-weight: 600; font-size: 13px; }
.score { position: relative; font-size: 11px; padding: 2px 8px; border-radius: 6px; background: linear-gradient(90deg, rgba(79,140,255,.35) var(--w), #0e1420 var(--w)); border: 1px solid var(--border); }
.decision .just { margin: 8px 0 0; font-size: 13px; line-height: 1.6; color: var(--text); background: #0e1420; border-left: 3px solid var(--accent-2); padding: 8px 10px; border-radius: 0 8px 8px 0; }
.metrics { gap: 12px; align-items: center; }
.mtile { background: #0e1420; border: 1px solid var(--border); border-radius: 9px; padding: 8px 12px; min-width: 120px; }
.mlabel { font-size: 11px; color: var(--muted); }
.mval { font-family: var(--mono); font-size: 18px; }
.arrow { color: var(--muted); font-size: 18px; }
</style>
