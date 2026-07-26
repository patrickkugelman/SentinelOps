import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The dashboard talks to the agent (default :8090). In dev we proxy /api to it
// so there's no CORS and SSE streams straight through. Override the target with
// AGENT_URL if the agent runs elsewhere.
const agent = process.env.AGENT_URL ?? 'http://localhost:8090'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: agent,
        changeOrigin: true,
      },
    },
  },
})
