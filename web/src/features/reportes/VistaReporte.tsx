import type { ReactNode } from 'react'
import type { Reporte, Tabla } from './modelo'
import { textoDeCelda } from './modelo'

/** El reporte en pantalla: lo mismo que va al Excel y al PDF. */
export function VistaReporte({ reporte, extra }: { reporte: Reporte; extra?: ReactNode }) {
  return (
    <article className="rounded-xl border border-stone-200 bg-white p-5 dark:border-stone-800 dark:bg-stone-900">
      <h2 className="text-lg font-semibold">{reporte.titulo}</h2>
      <ul className="mt-1 text-sm text-stone-500">
        {reporte.encabezado.map((linea) => (
          <li key={linea}>{linea}</li>
        ))}
      </ul>
      {extra}
      {reporte.tablas.map((tabla) => (
        <TablaVista key={tabla.titulo} tabla={tabla} />
      ))}
      {reporte.nota && <p className="mt-5 text-xs text-stone-500 italic">{reporte.nota}</p>}
    </article>
  )
}

function TablaVista({ tabla }: { tabla: Tabla }) {
  const alinear = (i: number) => (tabla.columnas[i].tipo === 'numero' ? 'text-right' : 'text-left')
  return (
    <section className="mt-6">
      <h3 className="mb-2 text-sm font-semibold">{tabla.titulo}</h3>
      {/* Las tablas anchas se desplazan dentro de su caja, no la página entera */}
      <div className="overflow-x-auto rounded-lg border border-stone-200 dark:border-stone-800">
        <table className="w-full text-sm">
          <thead className="bg-stone-50 text-xs text-stone-600 dark:bg-stone-800/60 dark:text-stone-300">
            <tr>
              {tabla.columnas.map((c, i) => (
                <th
                  key={c.titulo}
                  scope="col"
                  className={`px-3 py-2 font-semibold whitespace-nowrap ${alinear(i)}`}
                >
                  {c.titulo}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-stone-100 dark:divide-stone-800">
            {tabla.filas.length === 0 && (
              <tr>
                <td
                  colSpan={tabla.columnas.length}
                  className="px-3 py-6 text-center text-stone-500"
                >
                  {tabla.vacia ?? 'Sin datos'}
                </td>
              </tr>
            )}
            {tabla.filas.map((fila, f) => (
              <tr key={f}>
                {fila.map((v, i) => (
                  <td
                    key={i}
                    className={`px-3 py-1.5 whitespace-nowrap tabular-nums ${alinear(i)}`}
                  >
                    {textoDeCelda(v, tabla.columnas[i])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
          {tabla.total && tabla.filas.length > 0 && (
            <tfoot className="border-t border-stone-200 bg-stone-50 font-semibold dark:border-stone-800 dark:bg-stone-800/60">
              <tr>
                {tabla.total.map((v, i) => (
                  <td key={i} className={`px-3 py-2 whitespace-nowrap tabular-nums ${alinear(i)}`}>
                    {textoDeCelda(v, tabla.columnas[i])}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </section>
  )
}
