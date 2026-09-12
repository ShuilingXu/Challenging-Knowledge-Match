import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const apiProxyTarget = process.env.API_PROXY_TARGET || 'http://127.0.0.1:8080'
const proxy = {
  '/api': {
    target: apiProxyTarget,
    changeOrigin: true,
  },
  '/ws': {
    target: apiProxyTarget.replace(/^http/, 'ws'),
    ws: true,
  },
}

export default defineConfig({
  plugins: [react()],
  server: {
    // Browser profiles and temporary HTML workspaces can contain locked files.
    // They are not application source and must stay outside Vite's watch graph.
    watch: {
      ignored: [
        '**/test-assets/edge-qa-profile/**',
        '**/*.tmpdir',
        '**/*.tmpdir/**',
        '**/.*.tmpdir',
        '**/.*.tmpdir/**',
        '**/*.tmp',
        '**/*.tmp/**',
        '**/.*.tmp',
        '**/.*.tmp/**',
      ],
    },
    proxy,
  },
  preview: {
    proxy,
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.js'],
  },
})
