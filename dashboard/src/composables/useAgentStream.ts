import { onUnmounted, ref } from 'vue'
import type { AgentTrace } from '../api'

/**
 * Subscribes to the agent's SSE reasoning-trace stream. The agent republishes
 * the whole (growing) trace on every step, so `trace` always holds the latest
 * state and the UI reveals precedents/decision/result progressively.
 */
export function useAgentStream() {
  const trace = ref<AgentTrace | null>(null)
  const connected = ref(false)

  const source = new EventSource('/api/agent/stream')
  source.addEventListener('connected', () => { connected.value = true })
  source.addEventListener('trace', (e) => {
    try {
      trace.value = JSON.parse((e as MessageEvent).data) as AgentTrace
    } catch {
      /* ignore malformed frame */
    }
  })
  source.onerror = () => { connected.value = false }

  onUnmounted(() => source.close())

  return { trace, connected }
}
