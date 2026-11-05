/**
 * Reglas de los archivos que se pueden adjuntar. Sin dependencias de la app, para poder probarlas
 * con `npm test`. El servidor vuelve a controlar todo (mira el contenido, no el nombre): esto es
 * para avisar antes de gastar datos del celular.
 */

/** Copia de app.adjuntos.maximo-por-elemento (application.yml): archivos por animal o por evento. */
export const MAXIMO_ARCHIVOS = 10

/** Copia de app.adjuntos.tamanio-maximo (application.yml). */
export const TAMANIO_MAXIMO_MB = 10
export const TAMANIO_MAXIMO = TAMANIO_MAXIMO_MB * 1024 * 1024

const TIPOS = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif',
  'application/pdf',
])
/** Algunos navegadores no informan el tipo de una foto HEIC: se mira la extensión. */
const EXTENSIONES = /\.(jpe?g|png|webp|heic|heif|pdf)$/i

/** Para el atributo `accept` del selector de archivos. */
export const ACEPTA = 'image/*,application/pdf'

export interface DatosArchivo {
  name: string
  size: number
  type: string
}

export const esImagen = (tipo: string) => tipo.startsWith('image/')

export const esPdf = (tipo: string) => tipo === 'application/pdf'

/** Mensaje si no se puede adjuntar, o null. El tamaño se mira aparte, después de achicar las fotos. */
export function validarTipo(archivo: DatosArchivo): string | null {
  if (TIPOS.has(archivo.type.toLowerCase()) || EXTENSIONES.test(archivo.name)) return null
  return `${archivo.name}: solo se pueden adjuntar fotos y archivos PDF`
}

export function validarTamanio(archivo: DatosArchivo): string | null {
  if (archivo.size === 0) return `${archivo.name}: el archivo está vacío`
  if (archivo.size > TAMANIO_MAXIMO) {
    return `${archivo.name}: pesa ${formatearTamanio(archivo.size)} y el máximo es ${TAMANIO_MAXIMO_MB} MB`
  }
  return null
}

/** "850 KB", "2,4 MB" */
export function formatearTamanio(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toLocaleString('es-AR', { maximumFractionDigits: 1 })} MB`
}

/** "vaca.png" -> "vaca.jpg" (al achicar una foto siempre sale un JPEG) */
export function comoJpg(nombre: string) {
  return nombre.replace(/\.[^./\\]+$/, '') + '.jpg'
}
