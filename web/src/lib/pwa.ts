/**
 * Registra el service worker que guarda la app para abrirla sin conexión (#30). Solo en el build de
 * producción: en desarrollo guardaría versiones viejas mientras se edita el código.
 */
export function registrarServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Sin service worker la app funciona igual, solo que no abre sin conexión
    })
  })
}
