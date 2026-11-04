import { COLUMNAS, type Campo, campoDeTitulo } from './columnas'
import type { FilaImportacion } from './types'

export const MAXIMO_FILAS = 2000
const HOJA = 'Animales'

/** Problema con el archivo entero (no con una fila): se muestra tal cual. */
export class PlanillaInvalida extends Error {}

/**
 * Lee la hoja "Animales" (o la primera) de un .xlsx. La primera fila tiene que tener los títulos;
 * se reconocen aunque cambien el orden, las mayúsculas o los acentos. Las filas vacías se saltean.
 */
export async function leerPlanilla(archivo: File): Promise<FilaImportacion[]> {
  if (!/\.xlsx$/i.test(archivo.name)) {
    throw new PlanillaInvalida(
      /\.(xls|csv|ods)$/i.test(archivo.name)
        ? 'Ese formato no se puede leer. Abrilo en Excel y guardalo como "Libro de Excel (.xlsx)".'
        : 'Elegí un archivo de Excel (.xlsx).',
    )
  }

  // Se carga recién cuando hace falta: no pesa en el resto de la aplicación
  const { default: readXlsxFile } = await import('read-excel-file/browser')
  let hojas
  try {
    hojas = await readXlsxFile(archivo)
  } catch {
    throw new PlanillaInvalida(
      'No se pudo leer el archivo. ¿Está dañado o protegido con contraseña?',
    )
  }
  const hoja = hojas.find((h) => h.sheet.trim().toLowerCase() === HOJA.toLowerCase()) ?? hojas[0]
  const [titulos, ...datos] = hoja?.data ?? []
  if (!titulos) throw new PlanillaInvalida('La planilla está vacía.')

  const campos: (Campo | undefined)[] = titulos.map((t) =>
    typeof t === 'string' ? campoDeTitulo(t) : undefined,
  )
  const faltan = COLUMNAS.filter((c) => c.obligatoria && !campos.includes(c.campo))
  if (faltan.length > 0) {
    throw new PlanillaInvalida(
      `No encontramos la columna ${faltan.map((c) => `"${c.titulo}"`).join(' ni ')} en la primera fila. Usá la plantilla o revisá los títulos.`,
    )
  }

  const filas: FilaImportacion[] = []
  datos.forEach((celdas, i) => {
    const fila: FilaImportacion = { fila: i + 2 }
    let vacia = true
    campos.forEach((campo, columna) => {
      if (!campo) return
      const valor = aTexto(celdas[columna], campo)
      if (valor) {
        fila[campo] = valor
        vacia = false
      }
    })
    if (!vacia) filas.push(fila)
  })

  if (filas.length === 0) throw new PlanillaInvalida('La planilla no tiene animales cargados.')
  if (filas.length > MAXIMO_FILAS) {
    throw new PlanillaInvalida(
      `La planilla tiene ${filas.length} animales. Se pueden importar hasta ${MAXIMO_FILAS} por vez: dividila en partes.`,
    )
  }
  return filas
}

/** Convierte una celda a texto. Las fechas van como AAAA-MM-DD. */
function aTexto(valor: unknown, campo: Campo): string | undefined {
  if (valor === null || valor === undefined) return undefined
  if (valor instanceof Date) {
    // La librería devuelve las fechas de Excel en UTC
    const d = valor
    return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-${String(d.getUTCDate()).padStart(2, '0')}`
  }
  if (typeof valor === 'boolean') return valor ? 'Sí' : 'No'
  if (typeof valor === 'number') {
    const texto = Number.isInteger(valor) ? valor.toFixed(0) : String(valor)
    // Excel guarda 032000123456789 como número y se come el 0 inicial: el chip tiene 15 dígitos
    if (campo === 'caravanaElectronica' && /^\d{12,14}$/.test(texto)) return texto.padStart(15, '0')
    return texto
  }
  const texto = String(valor).trim()
  return texto || undefined
}

/** Descarga la plantilla vacía, con una hoja de instrucciones. */
export async function descargarPlantilla() {
  const { default: writeXlsxFile } = await import('write-excel-file/browser')
  const titulos = COLUMNAS.map((c) => ({
    value: c.obligatoria ? `${c.titulo} *` : c.titulo,
    fontWeight: 'bold' as const,
    backgroundColor: c.obligatoria ? '#FDE68A' : '#F5F5F4',
  }))
  // Columnas de texto: así Excel no le saca el 0 a la caravana electrónica ni convierte "1-2" en fecha
  const vacias = Array.from({ length: 200 }, () =>
    COLUMNAS.map((c) =>
      c.campo === 'fechaNacimiento' ? null : { value: '', type: String as StringConstructor },
    ),
  )
  const instrucciones = [
    [
      { value: 'Columna', fontWeight: 'bold' as const },
      { value: 'Qué poner', fontWeight: 'bold' as const },
    ],
    ...COLUMNAS.map((c) => [
      { value: c.obligatoria ? `${c.titulo} *` : c.titulo },
      { value: c.ayuda },
    ]),
    [],
    [
      {
        value:
          'Una fila por animal, en la hoja "Animales". No cambies los títulos de la primera fila. Hasta 2000 animales por archivo.',
      },
    ],
  ]

  await writeXlsxFile(
    [
      {
        sheet: 'Animales',
        data: [titulos, ...vacias],
        columns: COLUMNAS.map((c) => ({ width: c.ancho })),
        stickyRowsCount: 1,
        dateFormat: 'dd/mm/yyyy',
      },
      { sheet: 'Instrucciones', data: instrucciones, columns: [{ width: 24 }, { width: 80 }] },
    ],
    { fontFamily: 'Calibri', fontSize: 11 },
  ).toFile('plantilla-animales.xlsx')
}
