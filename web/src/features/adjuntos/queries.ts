import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import type { Adjunto } from './types'

const base = (establecimientoId: string) => `/establecimientos/${establecimientoId}`

/** Una subida en red lenta puede tardar: más que los 15 segundos de las consultas comunes. */
const TIMEOUT_SUBIDA_MS = 90_000

/**
 * Sube un archivo. `cargaId` lo genera quien llama: si la respuesta se pierde y se reintenta con el
 * mismo, el servidor devuelve el que ya tiene en vez de duplicarlo.
 */
export function subirArchivo(
  establecimientoId: string,
  destino: { operacionId: string } | { animalId: string },
  contenido: Blob,
  nombre: string,
  cargaId: string,
) {
  const url =
    'operacionId' in destino
      ? `${base(establecimientoId)}/eventos/operaciones/${destino.operacionId}/adjuntos`
      : `${base(establecimientoId)}/animales/${destino.animalId}/adjuntos`
  const formulario = new FormData()
  formulario.append('archivo', contenido, nombre)
  formulario.append('cargaId', cargaId)
  return api
    .post<Adjunto>(url, formulario, {
      // Con el Content-Type por defecto (JSON), axios convertiría el formulario a JSON
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: TIMEOUT_SUBIDA_MS,
    })
    .then((r) => r.data)
}

/** Los archivos del animal (los de sus eventos vienen dentro de cada evento). */
export function useAdjuntosAnimal(establecimientoId: string, animalId: string) {
  return useQuery({
    queryKey: ['adjuntos-animal', establecimientoId, animalId],
    queryFn: () =>
      api
        .get<Adjunto[]>(`${base(establecimientoId)}/animales/${animalId}/adjuntos`)
        .then((r) => r.data),
  })
}

/**
 * El contenido de un archivo. Las imágenes no se pueden pedir con `<img src>` porque la API
 * necesita el token en un encabezado: se baja como blob y se muestra desde la memoria. Un adjunto
 * nunca cambia, así que se guarda mientras la pestaña viva.
 */
export function useContenido(establecimientoId: string, adjuntoId: string, habilitado = true) {
  return useQuery({
    queryKey: ['adjunto-contenido', establecimientoId, adjuntoId],
    queryFn: () =>
      api
        .get<Blob>(`${base(establecimientoId)}/adjuntos/${adjuntoId}`, {
          responseType: 'blob',
          timeout: TIMEOUT_SUBIDA_MS,
        })
        .then((r) => r.data),
    enabled: habilitado,
    staleTime: Infinity,
    gcTime: 10 * 60 * 1000,
    retry: 1,
  })
}

export function useEliminarAdjunto(establecimientoId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (adjuntoId: string) =>
      api.delete(`${base(establecimientoId)}/adjuntos/${adjuntoId}`).then(() => undefined),
    onSuccess: (_, adjuntoId) => {
      queryClient.removeQueries({ queryKey: ['adjunto-contenido', establecimientoId, adjuntoId] })
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: ['eventos'] }),
        queryClient.invalidateQueries({ queryKey: ['adjuntos-animal'] }),
      ])
    },
  })
}

/** Baja el archivo al dispositivo (para los PDF). */
export async function descargarArchivo(establecimientoId: string, adjunto: Adjunto) {
  const { data } = await api.get<Blob>(`${base(establecimientoId)}/adjuntos/${adjunto.id}`, {
    responseType: 'blob',
    timeout: TIMEOUT_SUBIDA_MS,
  })
  const url = URL.createObjectURL(data)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = adjunto.nombre
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  // El navegador necesita un momento para empezar la descarga antes de soltar el blob
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}
