import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'prompt',
      injectRegister: null,
      devOptions: { enabled: true, type: 'module' },
      includeAssets: ['favicon.svg', 'pwa-192.svg', 'pwa-512.svg'],
      manifest: {
        name: 'VyapaarOS',
        short_name: 'VyapaarOS',
        description: 'B2B Wholesale Ordering Platform',
        theme_color: '#173f35',
        background_color: '#f5f5f0',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        icons: [
          { src: '/pwa-192.svg', sizes: '192x192', type: 'image/svg+xml', purpose: 'any maskable' },
          { src: '/pwa-512.svg', sizes: '512x512', type: 'image/svg+xml', purpose: 'any maskable' },
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api/],
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith('/api/'),
            handler: 'NetworkOnly',
          }
        ]
      }
    })
  ],
})
