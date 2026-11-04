import type { ReactNode } from 'react'
import { formatearFecha, hoyISO } from '@/features/eventos/fechas'
import { ESTADOS_REPRODUCTIVOS, RESULTADOS_TACTO, TIPOS_SERVICIO, textoMeses } from './config'
import { diasHasta } from './gestacion'
import type { EstadoReproductivo } from './types'

/** Tarjeta de la ficha (#19): en qué está la hembra y sus últimos servicio, tacto y parto. */
export function Reproduccion({ estado }: { estado: EstadoReproductivo }) {
  const { etiqueta, clases } = ESTADOS_REPRODUCTIVOS[estado.estado]
  const servicio = estado.ultimoServicio
  const tacto = estado.ultimoTacto

  return (
    <section className="rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
      <div className="flex items-center justify-between gap-3 px-5 pt-4 pb-3">
        <h2 className="text-sm font-semibold">Reproducción</h2>
        <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${clases}`}>
          {etiqueta}
        </span>
      </div>

      <div className="px-5 pb-5">
        {estado.estado === 'SIN_DATOS' ? (
          <p className="text-sm text-stone-500">
            Todavía no tiene servicios, tactos ni partos. Cuando se registren, acá vas a ver si está
            preñada y cuándo pare.
          </p>
        ) : (
          <>
            {estado.estado === 'PRENADA' && estado.fechaEstimadaParto && (
              <PartoEstimado fecha={estado.fechaEstimadaParto} />
            )}
            {servicio && (
              <Dato etiqueta="Último servicio">
                {TIPOS_SERVICIO[servicio.tipo]} · {formatearFecha(servicio.fecha)}
                {servicio.fechaFin && ` al ${formatearFecha(servicio.fechaFin)}`}
                {(servicio.pajuela || (servicio.toros && servicio.toros.length > 0)) && (
                  <span className="block text-xs text-stone-500">
                    {[servicio.pajuela, servicio.toros?.join(', ')].filter(Boolean).join(' · ')}
                  </span>
                )}
              </Dato>
            )}
            {tacto && (
              <Dato etiqueta="Último tacto">
                {tacto.resultado === 'PRENADA' && tacto.mesesGestacion
                  ? `Preñada de ${textoMeses(tacto.mesesGestacion)}`
                  : RESULTADOS_TACTO[tacto.resultado]}{' '}
                · {formatearFecha(tacto.fecha)}
                {tacto.veterinario && (
                  <span className="block text-xs text-stone-500">Vet. {tacto.veterinario}</span>
                )}
              </Dato>
            )}
            {estado.ultimoParto && (
              <Dato etiqueta="Último parto">{formatearFecha(estado.ultimoParto)}</Dato>
            )}
          </>
        )}
      </div>
    </section>
  )
}

/** La fecha grande, con cuánto falta (o cuánto se pasó sin registrar el parto). */
function PartoEstimado({ fecha }: { fecha: string }) {
  const dias = diasHasta(fecha, hoyISO())
  const detalle =
    dias > 0
      ? `faltan ${dias} ${dias === 1 ? 'día' : 'días'}`
      : dias === 0
        ? 'es hoy'
        : `pasaron ${-dias} ${dias === -1 ? 'día' : 'días'} y no hay parto registrado`
  return (
    <div className="mb-3 border-b border-stone-100 pb-3 dark:border-stone-800">
      <p className="text-sm text-stone-500">Parto estimado</p>
      <p className="text-2xl font-semibold tabular-nums">{formatearFecha(fecha)}</p>
      <p
        className={`text-sm ${dias < 0 ? 'text-amber-700 dark:text-amber-400' : 'text-stone-500'}`}
      >
        {detalle}
      </p>
    </div>
  )
}

function Dato({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-1 text-sm">
      <span className="shrink-0 text-stone-500">{etiqueta}</span>
      <span className="text-right">{children}</span>
    </div>
  )
}
