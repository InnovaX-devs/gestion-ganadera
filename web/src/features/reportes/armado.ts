import { CATEGORIAS, LISTA_CATEGORIAS, SEXOS } from '@/features/animales/config'
import type { Categoria } from '@/features/animales/types'
import type { Stock, StockGrupo } from '@/features/stock/types'
import type { Celda, Reporte, Tabla } from './modelo'
import { formatearFecha, hoyISO, slug } from './modelo'
import type { Existencias, ReportePesos } from './types'

/** Cómo se arma cada reporte a partir de los datos de la API (#32). */

/** "Generado el 02/11/2026 a las 13:07" */
function generado() {
  const ahora = new Date()
  const hora = ahora.toLocaleTimeString('es-AR', {
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
  return `Generado el ${formatearFecha(hoyISO())} a las ${hora}`
}

const porcentaje = (parte: number, total: number) => (total === 0 ? 0 : (parte / total) * 100)

// ---------- Stock por categoría y lote ----------

/** Lote o rodeo por fila, una columna por categoría: el cuadro que se arma en una planilla. */
function matriz(titulo: string, grupos: StockGrupo[], sinAsignar: string, total: Stock): Tabla {
  return {
    titulo,
    columnas: [
      { titulo: titulo.replace('Por ', '').replace(/^./, (c) => c.toUpperCase()), ancho: 22 },
      ...LISTA_CATEGORIAS.map((c) => ({ titulo: CATEGORIAS[c].plural, tipo: 'numero' as const })),
      { titulo: 'Total', tipo: 'numero' as const },
    ],
    filas: grupos.map((g) => [
      g.nombre ?? sinAsignar,
      ...LISTA_CATEGORIAS.map((c) => g.porCategoria[c]),
      g.total,
    ]),
    total: ['Total', ...LISTA_CATEGORIAS.map((c) => total.porCategoria[c]), total.total],
    vacia: 'Sin datos',
  }
}

export function reporteStock(stock: Stock, establecimiento: string): Reporte {
  return {
    titulo: 'Stock por categoría y lote',
    archivo: `stock-${slug(establecimiento)}-${hoyISO()}`,
    encabezado: [
      `Establecimiento: ${establecimiento}`,
      `Animales activos al ${formatearFecha(hoyISO())}`,
      generado(),
    ],
    tablas: [
      {
        titulo: 'Por categoría',
        columnas: [
          { titulo: 'Categoría', ancho: 16 },
          { titulo: 'Sexo', ancho: 10 },
          { titulo: 'Cabezas', tipo: 'numero' },
          { titulo: '% del total', tipo: 'numero', decimales: 1 },
        ],
        filas: LISTA_CATEGORIAS.map((c) => [
          CATEGORIAS[c].plural,
          SEXOS[CATEGORIAS[c].sexo],
          stock.porCategoria[c],
          porcentaje(stock.porCategoria[c], stock.total),
        ]),
        total: ['Total', null, stock.total, stock.total > 0 ? 100 : 0],
      },
      matriz('Por lote', stock.porLote, 'Sin lote', stock),
      matriz('Por rodeo', stock.porRodeo, 'Sin rodeo', stock),
    ],
  }
}

// ---------- Existencias para vacunación / SENASA ----------

export function reporteExistencias(e: Existencias): Reporte {
  const est = e.establecimiento
  const ubicacion = [est.localidad, est.provincia].filter(Boolean).join(', ')
  const totalPorCategoria = (c: Categoria) => e.stock.porCategoria[c]
  return {
    titulo: 'Existencias para vacunación',
    archivo: `existencias-${slug(est.nombre)}-${hoyISO()}`,
    encabezado: [
      `Establecimiento: ${est.nombre}`,
      `RENSPA: ${est.renspa ?? 'sin cargar'}`,
      ...(ubicacion ? [`Ubicación: ${ubicacion}`] : []),
      `Existencias al ${formatearFecha(e.generadoEn)}`,
    ],
    tablas: [
      {
        titulo: 'Existencias por categoría',
        columnas: [
          { titulo: 'Categoría', ancho: 16 },
          { titulo: 'Cabezas', tipo: 'numero' },
        ],
        filas: LISTA_CATEGORIAS.map((c) => [CATEGORIAS[c].plural, totalPorCategoria(c)]),
        total: ['Total', e.stock.total],
      },
      {
        titulo: 'Planilla de animales',
        columnas: [
          { titulo: 'N.º', tipo: 'numero', ancho: 6 },
          { titulo: 'Caravana', ancho: 14 },
          { titulo: 'Caravana electrónica', ancho: 22 },
          { titulo: 'Categoría', ancho: 14 },
          { titulo: 'Lote', ancho: 18 },
          { titulo: 'Rodeo', ancho: 18 },
          // Para marcar a mano en la manga
          { titulo: 'Vacunado', ancho: 10 },
        ],
        filas: e.animales.map((a, i) => [
          i + 1,
          a.caravana,
          a.caravanaElectronica ?? null,
          CATEGORIAS[a.categoria].etiqueta,
          a.lote ?? 'Sin lote',
          a.rodeo ?? null,
          null,
        ]),
        vacia: 'No hay animales activos',
      },
    ],
    nota: est.renspa
      ? undefined
      : 'El establecimiento no tiene RENSPA cargado: se puede agregar en Establecimientos.',
  }
}

// ---------- Evolución de pesos y ganancia diaria ----------

export function reportePesos(p: ReportePesos, establecimiento: string, filtros: string[]): Reporte {
  const r = p.resumen
  const kg = (v: number | undefined) => v ?? null
  return {
    titulo: 'Evolución de pesos y ganancia diaria',
    archivo: `pesos-${slug(establecimiento)}-${p.desde}-al-${p.hasta}`,
    encabezado: [
      `Establecimiento: ${establecimiento}`,
      `Período: ${formatearFecha(p.desde)} al ${formatearFecha(p.hasta)}`,
      ...filtros,
      generado(),
    ],
    tablas: [
      {
        // Una fila, una columna por indicador: cada uno con su formato (cantidades sin decimales)
        titulo: 'Resumen',
        columnas: [
          { titulo: 'Animales pesados', tipo: 'numero', ancho: 16 },
          { titulo: 'Pesajes', tipo: 'numero' },
          { titulo: 'Con ganancia (2+ pesadas)', tipo: 'numero', ancho: 24 },
          { titulo: 'Peso inicial prom. (kg)', tipo: 'numero', decimales: 1, ancho: 22 },
          { titulo: 'Peso final prom. (kg)', tipo: 'numero', decimales: 1, ancho: 22 },
          { titulo: 'Ganancia diaria prom. (kg/día)', tipo: 'numero', decimales: 3, ancho: 28 },
        ],
        filas: [
          [
            r.animales,
            r.pesajes,
            r.animalesConGanancia,
            kg(r.pesoInicialPromedio),
            kg(r.pesoFinalPromedio),
            kg(r.gananciaDiariaPromedio),
          ],
        ],
      },
      {
        titulo: 'Por categoría',
        columnas: [
          { titulo: 'Categoría', ancho: 16 },
          { titulo: 'Animales', tipo: 'numero' },
          { titulo: 'Peso final promedio (kg)', tipo: 'numero', decimales: 1, ancho: 24 },
          { titulo: 'Ganancia diaria (kg/día)', tipo: 'numero', decimales: 3, ancho: 24 },
        ],
        filas: p.porCategoria.map((c) => [
          CATEGORIAS[c.categoria].plural,
          c.animales,
          kg(c.pesoFinalPromedio),
          kg(c.gananciaDiariaPromedio),
        ]),
        vacia: 'No hay pesajes en el período',
      },
      {
        titulo: 'Evolución del peso promedio',
        columnas: [
          { titulo: 'Fecha', tipo: 'fecha', ancho: 12 },
          { titulo: 'Pesajes', tipo: 'numero' },
          { titulo: 'Peso promedio (kg)', tipo: 'numero', decimales: 1, ancho: 20 },
        ],
        filas: p.porFecha.map((f) => [f.fecha, f.pesajes, f.kgPromedio]),
        vacia: 'No hay pesajes en el período',
      },
      {
        titulo: 'Por animal',
        columnas: [
          { titulo: 'Caravana', ancho: 12 },
          { titulo: 'Categoría', ancho: 12 },
          { titulo: 'Lote', ancho: 16 },
          { titulo: 'Pesajes', tipo: 'numero' },
          { titulo: 'Fecha inicial', tipo: 'fecha', ancho: 12 },
          { titulo: 'Peso inicial', tipo: 'numero', decimales: 1 },
          { titulo: 'Fecha final', tipo: 'fecha', ancho: 12 },
          { titulo: 'Peso final', tipo: 'numero', decimales: 1 },
          { titulo: 'Días', tipo: 'numero' },
          { titulo: 'Variación (kg)', tipo: 'numero', decimales: 1, ancho: 14 },
          { titulo: 'Ganancia (kg/día)', tipo: 'numero', decimales: 3, ancho: 16 },
        ],
        filas: p.porAnimal.map((a): Celda[] => [
          a.caravana,
          CATEGORIAS[a.categoria].etiqueta,
          a.lote ?? null,
          a.pesajes,
          a.fechaInicial,
          a.pesoInicial,
          a.fechaFinal,
          a.pesoFinal,
          a.dias ?? null,
          a.variacionKg ?? null,
          a.gananciaDiaria ?? null,
        ]),
        vacia: 'No hay pesajes en el período',
      },
    ],
    nota:
      'La ganancia diaria de cada animal es (último peso − primer peso) / días entre esas pesadas, ' +
      'dentro del período. Lote y categoría son los actuales del animal.',
  }
}
