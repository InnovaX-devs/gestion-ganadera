import type { Celda, Columna, Reporte } from './modelo'
import { textoDeCelda } from './modelo'

/**
 * Exportación a Excel y PDF (#32). Las librerías se cargan recién al exportar: no agrandan la
 * carga inicial de la app.
 */

// ---------- Excel ----------

function celdaExcel(valor: Celda, columna: Columna, negrita = false) {
  const estilo = negrita ? { fontWeight: 'bold' as const } : {}
  if (valor === null || valor === '') return null
  if (columna.tipo === 'fecha' && typeof valor === 'string') {
    const [a, m, d] = valor.slice(0, 10).split('-').map(Number)
    return { value: new Date(Date.UTC(a, m - 1, d)), type: Date, format: 'dd/mm/yyyy', ...estilo }
  }
  if (typeof valor === 'number') {
    const d = columna.decimales ?? 0
    const formato = d > 0 ? `#,##0.${'0'.repeat(d)}` : '#,##0'
    return { value: valor, type: Number, format: formato, ...estilo }
  }
  return { value: String(valor), type: String, ...estilo }
}

/** Una hoja por tabla, con el título y el encabezado del reporte arriba. */
export async function exportarExcel(reporte: Reporte) {
  const { default: writeXlsxFile } = await import('write-excel-file/browser')
  const usados = new Set<string>()
  const hojas = reporte.tablas.map((tabla) => {
    const titulo = { value: reporte.titulo, fontWeight: 'bold' as const, fontSize: 14 }
    const encabezado = reporte.encabezado.map((linea) => [{ value: linea, type: String }])
    const columnas = tabla.columnas.map((c) => ({
      value: c.titulo,
      fontWeight: 'bold' as const,
      backgroundColor: '#F5F5F4',
    }))
    const filas = tabla.filas.map((fila) => fila.map((v, i) => celdaExcel(v, tabla.columnas[i])))
    const total = tabla.total
      ? [tabla.total.map((v, i) => celdaExcel(v, tabla.columnas[i], true))]
      : []
    const vacia =
      tabla.filas.length === 0 ? [[{ value: tabla.vacia ?? 'Sin datos', type: String }]] : []
    const nota = reporte.nota ? [[null], [{ value: reporte.nota, type: String }]] : []
    return {
      sheet: nombreDeHoja(tabla.titulo, usados),
      data: [
        [titulo],
        ...encabezado,
        [null],
        [{ value: tabla.titulo, fontWeight: 'bold' as const }],
        columnas,
        ...filas,
        ...total,
        ...vacia,
        ...nota,
      ],
      columns: tabla.columnas.map((c) => ({ width: c.ancho ?? Math.max(12, c.titulo.length + 2) })),
    }
  })
  // write-excel-file tipa cada celda por separado: el arreglo mixto se arma bien, pero hay que avisarle
  await writeXlsxFile(hojas as never, { fontFamily: 'Calibri', fontSize: 11 }).toFile(
    `${reporte.archivo}.xlsx`,
  )
}

/** Excel admite hasta 31 caracteres, sin : \ / ? * [ ], y sin repetir. */
function nombreDeHoja(titulo: string, usados: Set<string>) {
  const base =
    titulo
      .replace(/[:\\/?*[\]]/g, ' ')
      .slice(0, 28)
      .trim() || 'Hoja'
  let nombre = base
  for (let i = 2; usados.has(nombre); i++) nombre = `${base} ${i}`
  usados.add(nombre)
  return nombre
}

// ---------- PDF ----------

const MARRON: [number, number, number] = [180, 83, 9]
const GRIS: [number, number, number] = [120, 113, 108]

/**
 * A4: vertical, salvo que alguna tabla tenga muchas columnas. Fuente Helvetica, que tiene tildes y
 * eñes; los guiones largos (—) no están en esa fuente y se reemplazan.
 */
export async function exportarPdf(reporte: Reporte) {
  const [{ jsPDF }, { default: autoTable }] = await Promise.all([
    import('jspdf'),
    import('jspdf-autotable'),
  ])
  const ancha = reporte.tablas.some((t) => t.columnas.length > 7)
  const doc = new jsPDF({ orientation: ancha ? 'landscape' : 'portrait', unit: 'mm', format: 'a4' })
  const margen = 14
  const limpiar = (t: string) => t.replace(/[—–]/g, '-')

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(16)
  doc.text(limpiar(reporte.titulo), margen, 18)
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(9)
  doc.setTextColor(...GRIS)
  reporte.encabezado.forEach((linea, i) => doc.text(limpiar(linea), margen, 25 + i * 4.5))
  doc.setTextColor(0)
  let y = 25 + reporte.encabezado.length * 4.5 + 4

  for (const tabla of reporte.tablas) {
    doc.setFont('helvetica', 'bold')
    doc.setFontSize(11)
    // Si el título queda al pie de la página, la tabla empieza en la siguiente
    if (y > doc.internal.pageSize.getHeight() - 30) {
      doc.addPage()
      y = 18
    }
    doc.text(limpiar(tabla.titulo), margen, y)
    const texto = (fila: Celda[]) => fila.map((v, i) => limpiar(textoDeCelda(v, tabla.columnas[i])))
    autoTable(doc, {
      startY: y + 2,
      margin: { left: margen, right: margen },
      head: [tabla.columnas.map((c) => limpiar(c.titulo))],
      body: tabla.filas.length > 0 ? tabla.filas.map(texto) : [[tabla.vacia ?? 'Sin datos']],
      foot: tabla.total ? [texto(tabla.total)] : undefined,
      showFoot: 'lastPage',
      styles: { font: 'helvetica', fontSize: 8, cellPadding: 1.6 },
      headStyles: { fillColor: MARRON, textColor: 255 },
      footStyles: { fillColor: [245, 245, 244], textColor: 20, fontStyle: 'bold' },
      alternateRowStyles: { fillColor: [250, 250, 249] },
      columnStyles: Object.fromEntries(
        tabla.columnas.map((c, i) => [i, { halign: c.tipo === 'numero' ? 'right' : 'left' }]),
      ),
    })
    y = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 10
  }

  if (reporte.nota) {
    doc.setFont('helvetica', 'italic')
    doc.setFontSize(8)
    doc.setTextColor(...GRIS)
    const lineas = doc.splitTextToSize(
      limpiar(reporte.nota),
      doc.internal.pageSize.getWidth() - margen * 2,
    )
    if (y > doc.internal.pageSize.getHeight() - 20) {
      doc.addPage()
      y = 18
    }
    doc.text(lineas, margen, y)
  }

  // Pie en cada página
  const paginas = doc.getNumberOfPages()
  for (let i = 1; i <= paginas; i++) {
    doc.setPage(i)
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(8)
    doc.setTextColor(...GRIS)
    const alto = doc.internal.pageSize.getHeight()
    doc.text('Gestión Ganadera', margen, alto - 8)
    doc.text(`Página ${i} de ${paginas}`, doc.internal.pageSize.getWidth() - margen, alto - 8, {
      align: 'right',
    })
  }
  doc.save(`${reporte.archivo}.pdf`)
}
