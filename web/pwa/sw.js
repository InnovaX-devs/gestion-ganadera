/*
 * Service worker de la app (#30). Lo arma el plugin `serviceWorker` de vite.config.ts en cada
 * build: completa VERSION y ARCHIVOS con la versión y los archivos del build. Así cada
 * build nuevo es un service worker nuevo, y la app completa queda guardada para abrir sin conexión.
 *
 * - Páginas (navegación): primero la red (con un tope de 4 s, para la señal mala del campo); si no
 *   hay, el index.html guardado.
 * - Archivos del build (/assets/...): de la caché; tienen hash en el nombre, nunca cambian.
 * - Fuentes de Google: de la caché si ya se bajaron.
 * - La API no se toca: los datos sin conexión salen de IndexedDB (src/features/offline).
 */
const VERSION = '__VERSION__'
const CACHE = `ganadera-${VERSION}`
const FUENTES = 'ganadera-fuentes'
const ARCHIVOS = __ARCHIVOS__
const ESPERA_RED_MS = 4000

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll(['/', '/index.html', ...ARCHIVOS]))
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((nombres) =>
        Promise.all(
          nombres
            .filter((n) => n.startsWith('ganadera-') && n !== CACHE && n !== FUENTES)
            .map((n) => caches.delete(n)),
        ),
      )
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const { request } = event
  if (request.method !== 'GET') return
  const url = new URL(request.url)

  if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    event.respondWith(primeroCache(request, FUENTES))
    return
  }
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return

  if (request.mode === 'navigate') {
    event.respondWith(paginaConRespaldo(request))
    return
  }
  event.respondWith(primeroCache(request, CACHE))
})

async function paginaConRespaldo(request) {
  const cache = await caches.open(CACHE)
  try {
    const respuesta = await conTope(fetch(request), ESPERA_RED_MS)
    if (respuesta.ok) cache.put('/index.html', respuesta.clone())
    return respuesta
  } catch {
    // Es una SPA: cualquier ruta se resuelve con el index.html
    return (await cache.match('/index.html')) || (await cache.match('/')) || Response.error()
  }
}

async function primeroCache(request, nombre) {
  const cache = await caches.open(nombre)
  const guardada = await cache.match(request)
  if (guardada) return guardada
  const respuesta = await fetch(request)
  // Las fuentes vienen "opacas" (otro origen, status 0): igual se pueden guardar
  if (respuesta.ok || respuesta.type === 'opaque') cache.put(request, respuesta.clone())
  return respuesta
}

function conTope(promesa, ms) {
  return new Promise((resolver, rechazar) => {
    const reloj = setTimeout(() => rechazar(new Error('La red tardó demasiado')), ms)
    promesa.then(
      (r) => {
        clearTimeout(reloj)
        resolver(r)
      },
      (e) => {
        clearTimeout(reloj)
        rechazar(e)
      },
    )
  })
}
