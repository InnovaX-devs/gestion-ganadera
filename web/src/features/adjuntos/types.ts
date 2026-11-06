/** Un archivo ya subido (fotos y PDF). El contenido se pide aparte: ver queries.ts. */
export interface Adjunto {
  id: string
  nombre: string
  tipoContenido: string
  tamanio: number
  createdAt: string
}

/** A qué se adjunta: a la operación de un evento (se ve en todos sus eventos) o a un animal. */
export type DestinoArchivo =
  { tipo: 'EVENTO'; operacionId: string } | { tipo: 'ANIMAL'; animalId: string }

/**
 * Un archivo que se eligió sin conexión (o se cortó la subida) y espera la señal para subirse. Vive
 * en IndexedDB junto a la cola de operaciones (#30). El `id` es la `cargaId` que se manda al
 * servidor: reintentar la misma subida no la duplica.
 */
export interface ArchivoPendiente {
  id: string
  establecimientoId: string
  destino: DestinoArchivo
  nombre: string
  contenido: Blob
  /** Para mostrarlo en el indicador: "Foto de la observación", "Foto de V-214". */
  descripcion: string
  creadoEn: string
}
