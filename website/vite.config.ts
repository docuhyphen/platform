import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  ssr: {
    noExternal: true,
  },
  server: {
    proxy: {
      // Local development: forward public API calls to the Quarkus backend so the
      // "Speak to Sales" form works without a VITE_API_BASE_URL override.
      "/no-auth": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
})
