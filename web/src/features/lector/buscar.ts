import type { Animal, Pagina } from '@/features/animales/types'
import { conRespaldo } from '@/features/offline/conRespaldo'
import { animalesLocales } from '@/features/offline/local'
import { api } from '@/lib/api'
import { soloDigitos } from './lectura'

/**
 * El animal activo con esa caravana electrónica (o con esa caravana visual, si cargaron el número
 * del chip como caravana). Sin conexión, busca en lo guardado en el dispositivo.
 */
export async function buscarPorEid(establecimientoId: string, eid: string): Promise<Animal | null> {
  // Se busca por los últimos 12 dígitos: así también encuentra las cargadas como "982 000123456789"
  const filtro = { q: eid.slice(-12), tamanio: 20 }
  const pagina = await conRespaldo(
    () =>
      api
        .get<Pagina<Animal>>(`/establecimientos/${establecimientoId}/animales`, { params: filtro })
        .then((r) => r.data),
    () => animalesLocales(establecimientoId, filtro),
  )
  return (
    pagina.contenido.find(
      (a) => soloDigitos(a.caravanaElectronica) === eid || soloDigitos(a.caravana) === eid,
    ) ?? null
  )
}
