import { formatearFecha } from '@/features/eventos/fechas'
import { ListadoReproductivo } from '@/features/reproduccion/ListadoReproductivo'
import { TactoDialog } from '@/features/reproduccion/TactoDialog'
import { ESTADOS_REPRODUCTIVOS, textoMeses } from '@/features/reproduccion/config'
import type { TactoDatos } from '@/features/reproduccion/types'

export function TactosPage() {
  return (
    <ListadoReproductivo<TactoDatos>
      tipo="TACTO"
      titulo="Tactos"
      descripcion={(est) => `Diagnósticos de preñez en ${est}.`}
      vacio={{
        titulo: 'Todavía no hay tactos',
        texto: 'Cargá el resultado de cada vaca: si está preñada, se calcula la fecha de parto.',
      }}
      botonNuevo="Nuevo tacto"
      alAnular="y su estado reproductivo vuelve a lo que marcaba antes."
      detalle={(e) => <Detalle datos={e.datos} />}
      dialogo={(p) => <TactoDialog {...p} />}
    />
  )
}

function Detalle({ datos }: { datos: TactoDatos }) {
  const prenada = datos.resultado === 'PRENADA'
  const { etiqueta, clases } = ESTADOS_REPRODUCTIVOS[prenada ? 'PRENADA' : 'VACIA']
  return (
    <>
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${clases}`}>{etiqueta}</span>
        {prenada && datos.mesesGestacion && <span>{textoMeses(datos.mesesGestacion)}</span>}
        {datos.fechaEstimadaParto && (
          <span className="text-stone-500">
            Parto estimado {formatearFecha(datos.fechaEstimadaParto)}
          </span>
        )}
        {datos.veterinario && <span className="text-stone-500">Vet. {datos.veterinario}</span>}
      </p>
      {datos.observaciones && (
        <p className="mt-1 whitespace-pre-line text-stone-600 dark:text-stone-400">
          {datos.observaciones}
        </p>
      )}
    </>
  )
}
