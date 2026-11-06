import { useEffect } from 'react'
import { useAuth } from '@/features/auth/useAuth'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { establecimientosConEnvios } from './archivos'
import { alCambiarLaCola, alPedirSincronizacion } from './cola'
import { borrarTodo, db, metaClave } from './db'
import { sincronizar } from './sincronizar'

const CADA_CINCO_MINUTOS = 5 * 60 * 1000
const REINTENTO_PENDIENTES_MS = 30 * 1000
const ESPERA_TRAS_CARGAR_MS = 1500

/**
 * Mantiene sincronizado el dispositivo (#30, ADR 0004). Sincroniza el establecimiento activo y los
 * que tengan cargas pendientes:
 * - al abrir la app y al cambiar de establecimiento;
 * - al recuperar la conexión y al volver a la app (si estaba en segundo plano);
 * - un momento después de guardar algo sin conexión;
 * - cada 30 segundos mientras haya cargas o archivos pendientes (no todos los celulares avisan bien cuando
 *   vuelve la señal);
 * - cada 5 minutos mientras está abierta;
 * - cuando se toca "Sincronizar ahora".
 * No dibuja nada.
 */
export function SincronizacionAutomatica() {
  const { usuario } = useAuth()
  const { activo } = useEstablecimientoActivo()
  const usuarioId = usuario?.id
  const activoId = activo?.id

  useEffect(() => {
    if (!usuarioId) return
    let vigente = true
    let espera: ReturnType<typeof setTimeout> | undefined

    const correr = async () => {
      if (!vigente) return
      const conPendientes = await establecimientosConEnvios()
      await sincronizar([...(activoId ? [activoId] : []), ...conPendientes])
    }

    // Los datos guardados son de un usuario: si entró otro en este navegador, se empieza de cero
    const preparar = (async () => {
      const base = await db()
      const anterior = await base.get('meta', metaClave.usuarioId)
      if (anterior && anterior !== usuarioId) await borrarTodo()
      await base.put('meta', usuarioId, metaClave.usuarioId)
      // Que el navegador no borre los datos guardados si se queda sin espacio
      await navigator.storage?.persist?.().catch(() => false)
    })()

    preparar.then(correr).catch(() => undefined)

    const alVolver = () => document.visibilityState === 'visible' && correr()
    const dejarCola = alCambiarLaCola(() => {
      clearTimeout(espera)
      espera = setTimeout(correr, ESPERA_TRAS_CARGAR_MS)
    })
    const intervalo = setInterval(
      () => document.visibilityState === 'visible' && correr(),
      CADA_CINCO_MINUTOS,
    )
    const reintento = setInterval(async () => {
      if ((await establecimientosConEnvios()).length > 0) correr()
    }, REINTENTO_PENDIENTES_MS)
    const dejarPedidos = alPedirSincronizacion(correr)
    window.addEventListener('online', correr)
    document.addEventListener('visibilitychange', alVolver)

    return () => {
      vigente = false
      clearTimeout(espera)
      clearInterval(intervalo)
      clearInterval(reintento)
      dejarCola()
      dejarPedidos()
      window.removeEventListener('online', correr)
      document.removeEventListener('visibilitychange', alVolver)
    }
  }, [usuarioId, activoId])

  return null
}
