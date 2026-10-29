import { type ReactNode, useCallback, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { isAxiosError } from 'axios'
import {
  ArrowLeft,
  ChevronDown,
  LoaderCircle,
  Pencil,
  Plus,
  Scale,
  SearchX,
  Trash2,
} from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { FormError } from '@/components/FormError'
import { Paginacion } from '@/components/Paginacion'
import { AnimalFormDialog } from '@/features/animales/AnimalFormDialog'
import { CategoriaBadge } from '@/features/animales/CategoriaBadge'
import { GraficoPeso } from '@/features/animales/GraficoPeso'
import { SEXOS, formatearEdad } from '@/features/animales/config'
import { useAnimal, useCrias } from '@/features/animales/queries'
import type { Animal, Referencia } from '@/features/animales/types'
import { type Permiso, type Rol, puede } from '@/features/establecimientos/permisos'
import { useEstablecimientoActivo } from '@/features/establecimientos/useEstablecimientoActivo'
import { ObservacionDialog } from '@/features/eventos/ObservacionDialog'
import { TIPOS_EVENTO, formatearKg } from '@/features/eventos/config'
import { formatearFecha } from '@/features/eventos/fechas'
import { useAnularEvento, useEventos } from '@/features/eventos/queries'
import type { Evento, TipoEvento } from '@/features/eventos/types'
import { Ganancia } from '@/features/pesajes/Ganancia'
import { PesajeDialog } from '@/features/pesajes/PesajeDialog'
import { usePesajes } from '@/features/pesajes/queries'
import { CarenciaBadge } from '@/features/sanidad/CarenciaBadge'
import { TratamientoDialog } from '@/features/sanidad/TratamientoDialog'
import { formatearDiasCarencia } from '@/features/sanidad/config'
import { useDismiss } from '@/hooks/useDismiss'
import { getErrorMessage } from '@/lib/api'

/**
 * Tipos que ya se pueden cargar desde la ficha, con los permisos que alcanzan para cargarlos (los
 * mismos que pide el backend en TipoEvento). Sumar acá cada tipo nuevo (#18 a #23).
 */
const REGISTRABLES: Partial<Record<TipoEvento, Permiso[]>> = {
  OBSERVACION: ['GESTIONAR_ANIMALES', 'REGISTRAR_SANIDAD'],
  TRATAMIENTO: ['REGISTRAR_SANIDAD'],
  PESAJE: ['GESTIONAR_ANIMALES'],
}

const registrablesPara = (rol: Rol) =>
  (Object.keys(REGISTRABLES) as TipoEvento[]).filter((tipo) =>
    REGISTRABLES[tipo]!.some((permiso) => puede(rol, permiso)),
  )

const ESTADOS = { VENDIDO: 'Vendido', MUERTO: 'Muerto' } as const

export function AnimalPage() {
  const { animalId } = useParams()
  const { activo, cargando } = useEstablecimientoActivo()
  const { data: animal, error, isPending } = useAnimal(activo?.id, animalId)

  if (cargando || (activo && isPending)) return <Cargando />
  if (!activo || !animal) {
    const noEsta = !activo || (isAxiosError(error) && error.response?.status === 404)
    return (
      <>
        <Volver />
        {noEsta ? (
          <EmptyState
            icono={SearchX}
            titulo={
              activo ? `Este animal no está en ${activo.nombre}` : 'No encontramos este animal'
            }
            texto="Puede ser de otro establecimiento: cambialo arriba o buscalo en el listado."
            accion={
              <Link
                to="/animales"
                className="bg-brand-600 hover:bg-brand-700 inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium text-white"
              >
                Ir a Animales
              </Link>
            }
          />
        ) : (
          <FormError message={getErrorMessage(error)} />
        )}
      </>
    )
  }

  const activoEnCampo = animal.estado === 'ACTIVO'
  return (
    <Ficha
      // Al pasar de un animal a su madre o a una cría, la ficha arranca de cero
      key={animal.id}
      animal={animal}
      establecimientoId={activo.id}
      puedeEditar={activoEnCampo && puede(activo.rol, 'GESTIONAR_ANIMALES')}
      registrables={activoEnCampo ? registrablesPara(activo.rol) : []}
      puedeCrearProductos={puede(activo.rol, 'REGISTRAR_SANIDAD')}
    />
  )
}

function Ficha({
  animal,
  establecimientoId,
  puedeEditar,
  registrables,
  puedeCrearProductos,
}: {
  animal: Animal
  establecimientoId: string
  puedeEditar: boolean
  /** Tipos de evento que el usuario puede cargar a este animal; vacío si no puede ninguno. */
  registrables: TipoEvento[]
  puedeCrearProductos: boolean
}) {
  const puedeRegistrar = registrables.length > 0
  const [editando, setEditando] = useState(false)
  const [registrando, setRegistrando] = useState<TipoEvento | null>(null)
  const edad = formatearEdad(animal.fechaNacimiento, animal.fechaNacimientoEstimada)

  return (
    <>
      <Volver />

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight tabular-nums">
              {animal.caravana}
            </h1>
            <CategoriaBadge animal={animal} />
            <CarenciaBadge carencia={animal.carencia} />
            {animal.estado !== 'ACTIVO' && (
              <span className="rounded-full bg-stone-200 px-2 py-0.5 text-xs font-medium text-stone-700 dark:bg-stone-800 dark:text-stone-300">
                {ESTADOS[animal.estado]}
              </span>
            )}
          </div>
          <p className="mt-1 text-sm text-stone-600 dark:text-stone-400">
            {[SEXOS[animal.sexo], animal.raza, edad].filter(Boolean).join(' · ')}
          </p>
        </div>
        <div className="flex gap-2">
          {puedeEditar && (
            <Button variant="secondary" onClick={() => setEditando(true)}>
              <Pencil className="size-4" />
              Editar
            </Button>
          )}
          {puedeRegistrar && <MenuRegistrar tipos={registrables} onElegir={setRegistrando} />}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4">
          <Tarjeta titulo="Datos">
            <Dato etiqueta="Caravana electrónica">{animal.caravanaElectronica}</Dato>
            <Dato etiqueta="Nacimiento">
              {animal.fechaNacimiento &&
                `${formatearFecha(animal.fechaNacimiento)}${animal.fechaNacimientoEstimada ? ' (estimada)' : ''}`}
            </Dato>
            {animal.sexo === 'MACHO' && (
              <Dato etiqueta="Castrado">{animal.castrado ? 'Sí' : 'No'}</Dato>
            )}
            <Dato etiqueta="Cargado el">{formatearFecha(animal.createdAt.slice(0, 10))}</Dato>
            {animal.observaciones && (
              <p className="mt-3 border-t border-stone-100 pt-3 text-sm whitespace-pre-line text-stone-700 dark:border-stone-800 dark:text-stone-300">
                {animal.observaciones}
              </p>
            )}
          </Tarjeta>

          {animal.carencia && <Carencia carencia={animal.carencia} />}

          <Tarjeta titulo="Ubicación">
            <Dato etiqueta="Lote">{animal.lote?.nombre}</Dato>
            <Dato etiqueta="Rodeo">{animal.rodeo?.nombre}</Dato>
          </Tarjeta>

          <Genealogia animal={animal} establecimientoId={establecimientoId} />
        </div>

        <div className="space-y-4 lg:col-span-2">
          <Peso animalId={animal.id} establecimientoId={establecimientoId} />
          <Historial
            animalId={animal.id}
            establecimientoId={establecimientoId}
            puedeAnular={puedeRegistrar}
          />
        </div>
      </div>

      <AnimalFormDialog
        establecimientoId={establecimientoId}
        open={editando}
        animal={animal}
        onClose={() => setEditando(false)}
      />
      <ObservacionDialog
        establecimientoId={establecimientoId}
        open={registrando === 'OBSERVACION'}
        animalIds={[animal.id]}
        onClose={() => setRegistrando(null)}
      />
      <PesajeDialog
        establecimientoId={establecimientoId}
        open={registrando === 'PESAJE'}
        animalId={animal.id}
        caravana={animal.caravana}
        onClose={() => setRegistrando(null)}
      />
      <TratamientoDialog
        establecimientoId={establecimientoId}
        open={registrando === 'TRATAMIENTO'}
        animalIds={[animal.id]}
        puedeCrearProductos={puedeCrearProductos}
        onClose={() => setRegistrando(null)}
      />
    </>
  )
}

function MenuRegistrar({
  tipos,
  onElegir,
}: {
  tipos: TipoEvento[]
  onElegir: (tipo: TipoEvento) => void
}) {
  const [abierto, setAbierto] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const cerrar = useCallback(() => setAbierto(false), [])
  useDismiss(ref, abierto, cerrar)

  return (
    <div ref={ref} className="relative">
      <Button onClick={() => setAbierto((a) => !a)} aria-expanded={abierto} aria-haspopup="menu">
        <Plus className="size-4" />
        Registrar evento
        <ChevronDown className="size-4" />
      </Button>
      {abierto && (
        <div
          role="menu"
          className="absolute right-0 z-20 mt-1 w-52 rounded-xl border border-stone-200 bg-white p-1 shadow-lg dark:border-stone-700 dark:bg-stone-900"
        >
          {tipos.map((tipo) => {
            const { etiqueta, icono: Icono } = TIPOS_EVENTO[tipo]
            return (
              <button
                key={tipo}
                type="button"
                role="menuitem"
                onClick={() => {
                  setAbierto(false)
                  onElegir(tipo)
                }}
                className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm hover:bg-stone-100 dark:hover:bg-stone-800"
              >
                <Icono className="size-4 text-stone-500" />
                {etiqueta}
              </button>
            )
          })}
          <p className="px-3 pt-1 pb-2 text-xs text-stone-500">
            Pesajes y el resto, a medida que se habiliten.
          </p>
        </div>
      )}
    </div>
  )
}

/** Qué tratamientos mantienen al animal en carencia y desde cuándo queda libre. */
function Carencia({ carencia }: { carencia: NonNullable<Animal['carencia']> }) {
  return (
    <section className="rounded-xl border border-amber-300 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/30">
      <h2 className="px-5 pt-4 pb-1 text-sm font-semibold text-amber-900 dark:text-amber-200">
        En período de carencia
      </h2>
      <p className="px-5 text-sm text-amber-900 dark:text-amber-200">
        No faenar ni vender hasta el {formatearFecha(carencia.libreDesde)}.
      </p>
      <ul className="space-y-1 px-5 pt-3 pb-4 text-sm text-stone-700 dark:text-stone-300">
        {carencia.tratamientos.map((t) => (
          <li key={`${t.producto}-${t.fecha}`} className="flex justify-between gap-3">
            <span>
              {t.producto}
              <span className="text-stone-500"> · {formatearFecha(t.fecha)}</span>
            </span>
            <span className="text-xs text-stone-500">{formatearDiasCarencia(t.diasCarencia)}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function Genealogia({ animal, establecimientoId }: { animal: Animal; establecimientoId: string }) {
  const { data: crias, isPending } = useCrias(establecimientoId, animal.id)
  return (
    <Tarjeta titulo="Genealogía">
      <Dato etiqueta="Madre">{animal.madre && <LinkAnimal animal={animal.madre} />}</Dato>
      <Dato etiqueta="Padre">{animal.padre && <LinkAnimal animal={animal.padre} />}</Dato>
      <div className="mt-3 border-t border-stone-100 pt-3 dark:border-stone-800">
        <p className="text-sm text-stone-500">
          Crías{crias && crias.length > 0 && ` (${crias.length})`}
        </p>
        {isPending ? (
          <LoaderCircle className="mt-2 size-4 animate-spin text-stone-400" />
        ) : crias && crias.length > 0 ? (
          <ul className="mt-2 space-y-1.5">
            {crias.map((c) => (
              <li key={c.id} className="flex flex-wrap items-center gap-2 text-sm">
                {c.establecimientoId === establecimientoId ? (
                  <LinkAnimal animal={{ id: c.id, nombre: c.caravana }} />
                ) : (
                  <span className="font-medium tabular-nums">{c.caravana}</span>
                )}
                <CategoriaBadge animal={c} />
                {c.estado !== 'ACTIVO' && (
                  <span className="text-xs text-stone-500">{ESTADOS[c.estado]}</span>
                )}
                {c.fechaNacimiento && (
                  <span className="ml-auto text-xs text-stone-500 tabular-nums">
                    {formatearFecha(c.fechaNacimiento)}
                  </span>
                )}
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-1 text-sm text-stone-400 dark:text-stone-600">Sin crías registradas</p>
        )}
      </div>
    </Tarjeta>
  )
}

function Peso({ animalId, establecimientoId }: { animalId: string; establecimientoId: string }) {
  const { data, isPending } = usePesajes(establecimientoId, { animalId, tamanio: 200 })
  const pesajes = data?.contenido ?? []
  const puntos = pesajes.map((p) => ({ fecha: p.fecha, kg: p.kg }))
  // El listado viene del más nuevo al más viejo
  const [ultimo, anterior] = puntos

  return (
    <Tarjeta titulo="Peso">
      {isPending ? (
        <LoaderCircle className="size-4 animate-spin text-stone-400" />
      ) : !ultimo ? (
        <div className="flex items-center gap-3 py-2 text-sm text-stone-500">
          <Scale className="size-5 text-stone-400" />
          Sin pesajes todavía. Cuando se registren, acá vas a ver el último peso y su evolución.
        </div>
      ) : (
        <>
          <div className="mb-3 flex flex-wrap items-baseline gap-x-3">
            <span className="text-3xl font-semibold tabular-nums">
              {formatearKg(ultimo.kg)} <span className="text-base font-normal">kg</span>
            </span>
            <span className="text-sm text-stone-500">
              {formatearFecha(ultimo.fecha)}
              {anterior && <> · {variacion(anterior, ultimo)}</>}
            </span>
          </div>
          <p className="mb-3">
            <Ganancia pesaje={pesajes[0]!} />
          </p>
          {puntos.length > 1 && <GraficoPeso puntos={puntos} />}
        </>
      )}
    </Tarjeta>
  )
}

/** "+12 kg desde el 1 sep 2026" */
function variacion(anterior: { fecha: string; kg: number }, ultimo: { kg: number }) {
  const dif = ultimo.kg - anterior.kg
  const signo = dif > 0 ? '+' : dif < 0 ? '−' : ''
  return `${signo}${formatearKg(Math.abs(dif))} kg desde el ${formatearFecha(anterior.fecha)}`
}

const POR_PAGINA = 30

function Historial({
  animalId,
  establecimientoId,
  puedeAnular,
}: {
  animalId: string
  establecimientoId: string
  puedeAnular: boolean
}) {
  const [pagina, setPagina] = useState(0)
  const [aAnular, setAAnular] = useState<Evento | null>(null)
  const { data, isPending, error } = useEventos(establecimientoId, {
    animalId,
    pagina,
    tamanio: POR_PAGINA,
  })
  const anular = useAnularEvento(establecimientoId)
  const cerrar = () => {
    setAAnular(null)
    anular.reset()
  }
  const eventos = data?.contenido ?? []

  return (
    <Tarjeta titulo="Historial" sinPadding>
      {error && (
        <div className="px-5 pb-4">
          <FormError message={getErrorMessage(error)} />
        </div>
      )}
      {isPending && (
        <div className="px-5 pb-5">
          <LoaderCircle className="size-4 animate-spin text-stone-400" />
        </div>
      )}
      {data && eventos.length === 0 && (
        <p className="px-5 pb-5 text-sm text-stone-500">
          Todavía no tiene eventos. Lo que registres (pesajes, tratamientos, observaciones) va a
          aparecer acá, del más nuevo al más viejo.
        </p>
      )}
      {eventos.length > 0 && (
        <ol className="px-5 pb-2">
          {eventos.map((e, i) => {
            const { etiqueta, icono: Icono, resumen } = TIPOS_EVENTO[e.tipo]
            const detalle = resumen?.(e.datos)
            return (
              <li key={e.id} className="relative flex gap-3 pb-5">
                {/* Línea que une los eventos */}
                {i < eventos.length - 1 && (
                  <span
                    aria-hidden
                    className="absolute top-8 bottom-0 left-4 w-px bg-stone-200 dark:bg-stone-800"
                  />
                )}
                <span className="bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200 grid size-8 shrink-0 place-items-center rounded-full">
                  <Icono className="size-4" />
                </span>
                <div className="min-w-0 flex-1 pt-1">
                  <div className="flex flex-wrap items-baseline gap-x-2">
                    <span className="text-sm font-medium">{etiqueta}</span>
                    <span className="text-xs text-stone-500 tabular-nums">
                      {formatearFecha(e.fecha)}
                    </span>
                  </div>
                  {detalle && (
                    <p className="mt-0.5 text-sm whitespace-pre-line text-stone-700 dark:text-stone-300">
                      {detalle}
                    </p>
                  )}
                  <p className="mt-0.5 text-xs text-stone-500">{e.registradoPor.nombre}</p>
                </div>
                {puedeAnular && (
                  <button
                    type="button"
                    onClick={() => setAAnular(e)}
                    aria-label={`Anular ${etiqueta.toLowerCase()}`}
                    title="Anular"
                    className="self-start rounded-md p-1.5 text-stone-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
                  >
                    <Trash2 className="size-4" />
                  </button>
                )}
              </li>
            )
          })}
        </ol>
      )}
      {data && data.totalPaginas > 1 && (
        <Paginacion
          pagina={data.pagina}
          totalPaginas={data.totalPaginas}
          total={data.total}
          tamanio={data.tamanio}
          onPagina={setPagina}
        />
      )}

      <ConfirmDialog
        open={aAnular !== null}
        title={`Anular ${aAnular ? TIPOS_EVENTO[aAnular.tipo].etiqueta.toLowerCase() : ''}`}
        description="Deja de aparecer en el historial y se deshacen los cambios que haya hecho en el animal."
        confirmLabel="Anular"
        loading={anular.isPending}
        error={anular.error ? getErrorMessage(anular.error) : null}
        onClose={cerrar}
        onConfirm={() => aAnular && anular.mutate(aAnular.id, { onSuccess: cerrar })}
      />
    </Tarjeta>
  )
}

function Tarjeta({
  titulo,
  sinPadding,
  children,
}: {
  titulo: string
  sinPadding?: boolean
  children: ReactNode
}) {
  return (
    <section className="rounded-xl border border-stone-200 bg-white dark:border-stone-800 dark:bg-stone-900">
      <h2 className="px-5 pt-4 pb-3 text-sm font-semibold">{titulo}</h2>
      <div className={sinPadding ? '' : 'px-5 pb-5'}>{children}</div>
    </section>
  )
}

function Dato({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-1 text-sm">
      <span className="text-stone-500">{etiqueta}</span>
      <span className="text-right">
        {children || <span className="text-stone-300 dark:text-stone-600">—</span>}
      </span>
    </div>
  )
}

function LinkAnimal({ animal }: { animal: Referencia }) {
  return (
    <Link
      to={`/animales/${animal.id}`}
      className="text-brand-700 dark:text-brand-300 font-medium tabular-nums hover:underline"
    >
      {animal.nombre}
    </Link>
  )
}

function Volver() {
  return (
    <Link
      to="/animales"
      className="mb-4 inline-flex items-center gap-1.5 text-sm text-stone-500 hover:text-stone-800 dark:hover:text-stone-200"
    >
      <ArrowLeft className="size-4" />
      Animales
    </Link>
  )
}

function Cargando() {
  return (
    <div className="grid place-items-center py-16">
      <LoaderCircle className="size-6 animate-spin text-stone-400" />
    </div>
  )
}
