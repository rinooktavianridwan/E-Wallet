/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Proxy /api ke backend supaya browser melihat satu origin yang sama.
// Dengan begitu cookie refresh token (SameSite=Strict) ikut terkirim saat development.
// Port dan alamat backend bisa diubah lewat frontend/.env (lihat .env.example).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: Number(env.DEV_PORT) || 5173,
      proxy: {
        '/api': { target: env.DEV_API_TARGET || 'http://localhost:8080', changeOrigin: false },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      globals: true,
    },
  }
})
