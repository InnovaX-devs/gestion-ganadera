import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

/**
 * Arma el service worker (pwa/sw.js) con la lista de archivos de este build, para que la app
 * completa quede guardada y abra sin conexión (#30). Cada build cambia el contenido, así el
 * navegador detecta que hay una versión nueva. En desarrollo no se usa.
 */
function serviceWorker(): Plugin {
  return {
    name: 'service-worker',
    apply: 'build',
    generateBundle(_opciones, bundle) {
      const publicos = [
        '/manifest.webmanifest',
        '/favicon.svg',
        '/icons/icon-192.png',
        '/icons/icon-512.png',
      ]
      const archivos = [
        ...Object.keys(bundle)
          .map((f) => `/${f}`)
          .filter((f) => f.startsWith('/assets/')),
        ...publicos,
      ]
      const version = createHash('sha256').update(archivos.join('|')).digest('hex').slice(0, 12)
      const plantilla = readFileSync(fileURLToPath(new URL('./pwa/sw.js', import.meta.url)), 'utf8')
      this.emitFile({
        type: 'asset',
        fileName: 'sw.js',
        source: plantilla
          .replace('__VERSION__', version)
          .replace('__ARCHIVOS__', JSON.stringify(archivos)),
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), serviceWorker()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
  },
})
