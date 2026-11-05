import { comoJpg, esImagen } from './validacion'

/** Una foto de celular pesa 4-8 MB: con 1600 px de lado se ve bien en pantalla y baja a unos 300 KB. */
const LADO_MAXIMO = 1600
const CALIDAD = 0.82
/** Una foto ya chica y liviana se sube tal cual. */
const YA_LIVIANA = 400 * 1024
const COMPRIMIBLES = ['image/jpeg', 'image/png', 'image/webp']

/**
 * Achica la foto antes de subirla: con la señal del campo, subir 6 MB puede no terminar nunca. Los
 * PDF, las fotos que el navegador no sabe abrir (ej: HEIC fuera de Safari) y cualquier falla se
 * dejan como están: el archivo original siempre es un resultado válido.
 */
export async function prepararArchivo(archivo: File): Promise<File> {
  if (!esImagen(archivo.type) || !COMPRIMIBLES.includes(archivo.type)) return archivo
  try {
    // 'from-image' respeta la rotación que guarda el celular (si no, las fotos verticales salen de costado)
    const imagen = await createImageBitmap(archivo, { imageOrientation: 'from-image' })
    try {
      const escala = Math.min(1, LADO_MAXIMO / Math.max(imagen.width, imagen.height))
      if (escala === 1 && archivo.size <= YA_LIVIANA) return archivo

      const ancho = Math.max(1, Math.round(imagen.width * escala))
      const alto = Math.max(1, Math.round(imagen.height * escala))
      const lienzo = document.createElement('canvas')
      lienzo.width = ancho
      lienzo.height = alto
      const contexto = lienzo.getContext('2d')
      if (!contexto) return archivo
      // El JPEG no tiene transparencia: un PNG con fondo transparente saldría negro
      contexto.fillStyle = '#fff'
      contexto.fillRect(0, 0, ancho, alto)
      contexto.drawImage(imagen, 0, 0, ancho, alto)

      const blob = await new Promise<Blob | null>((resolver) =>
        lienzo.toBlob(resolver, 'image/jpeg', CALIDAD),
      )
      // Si no se ganó nada (ej: una captura de pantalla chica), mejor el original
      if (!blob || blob.size >= archivo.size) return archivo
      return new File([blob], comoJpg(archivo.name), {
        type: 'image/jpeg',
        lastModified: archivo.lastModified,
      })
    } finally {
      imagen.close()
    }
  } catch {
    return archivo
  }
}
