import { type FormEvent, useState } from 'react'
import { Plus } from 'lucide-react'
import { Button } from '@/components/Button'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { SelectField } from '@/components/SelectField'
import { TextField } from '@/components/TextField'
import { useAgrupaciones } from '@/features/agrupaciones/queries'
import type { Referencia } from '@/features/animales/types'
import { hoyISO, formatearFecha } from '@/features/eventos/fechas'
import { useRegistrarEvento } from '@/features/eventos/queries'
import type { Operacion } from '@/features/eventos/types'
import { getErrorMessage, getFieldErrors } from '@/lib/api'
import { libreDesdeDe } from './carencia'
import { LISTA_VIAS, MAX_DIAS_CARENCIA, TIPOS_PRODUCTO, VIAS } from './config'
import { ElegirAnimales } from './ElegirAnimales'
import { ProductoFormDialog } from './ProductoFormDialog'
import { useProductos } from './queries'
import type { ProductoSanitario, TratamientoDatos, ViaAdministracion } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /**
   * Si viene, el tratamiento es de esos animales (desde la ficha o la selección del listado) y no
   * se elige a quién. Si no, se elige: animales sueltos, un lote o un rodeo.
   */
  animalIds?: string[]
  /** Quien puede cargar tratamientos puede también agregar productos al catálogo. */
  puedeCrearProductos?: boolean
  /** Valores con los que arranca el formulario (ej: desde un tratamiento planificado, #34). */
  inicial?: {
    loteId?: string
    rodeoId?: string
    productoId?: string
    dosis?: string
    via?: ViaAdministracion
    diasCarencia?: number
  }
  onClose: () => void
  /** Recibe la operación registrada (con `pendiente` si se guardó sin conexión). */
  onHecho?: (operacion: Operacion) => void
}

type Destino = 'ANIMALES' | 'LOTE' | 'RODEO'

/** Nuevo tratamiento sanitario: individual, de varios animales o de todo un lote o rodeo. */
export function TratamientoDialog({
  establecimientoId,
  open,
  animalIds,
  puedeCrearProductos = true,
  inicial,
  onClose,
  onHecho,
}: Props) {
  const cantidad = animalIds?.length ?? 0
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Nuevo tratamiento"
      description={
        cantidad > 0
          ? `Para ${cantidad} ${cantidad === 1 ? 'animal seleccionado' : 'animales seleccionados'}.`
          : 'Vacunas, antiparasitarios, antibióticos y otros. Queda en el historial de cada animal.'
      }
    >
      {open && (
        <Formulario
          establecimientoId={establecimientoId}
          animalIds={animalIds}
          puedeCrearProductos={puedeCrearProductos}
          inicial={inicial}
          onClose={onClose}
          onHecho={(operacion) => {
            onHecho?.(operacion)
            onClose()
          }}
        />
      )}
    </Dialog>
  )
}

function Formulario({
  establecimientoId,
  animalIds,
  puedeCrearProductos,
  inicial,
  onClose,
  onHecho,
}: Omit<Props, 'open'> & { onHecho: (operacion: Operacion) => void }) {
  const animalesFijos = (animalIds?.length ?? 0) > 0
  const { data: productos } = useProductos(establecimientoId)
  const { data: lotes } = useAgrupaciones('lotes', establecimientoId)
  const { data: rodeos } = useAgrupaciones('rodeos', establecimientoId)
  const registrar = useRegistrarEvento<TratamientoDatos>(establecimientoId)

  const [destino, setDestino] = useState<Destino>(
    inicial?.loteId ? 'LOTE' : inicial?.rodeoId ? 'RODEO' : 'ANIMALES',
  )
  const [elegidos, setElegidos] = useState<Referencia[]>([])
  const [loteId, setLoteId] = useState(inicial?.loteId ?? '')
  const [rodeoId, setRodeoId] = useState(inicial?.rodeoId ?? '')
  const [fecha, setFecha] = useState(hoyISO)
  const [productoId, setProductoId] = useState(inicial?.productoId ?? '')
  const [dosis, setDosis] = useState(inicial?.dosis ?? '')
  const [via, setVia] = useState<ViaAdministracion | ''>(inicial?.via ?? '')
  const [veterinario, setVeterinario] = useState('')
  const [dias, setDias] = useState(String(inicial?.diasCarencia ?? 0))
  const [creandoProducto, setCreandoProducto] = useState(false)
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)

  /** Al elegir un producto se completan dosis, vía y carencia con los habituales (se pueden cambiar). */
  function elegirProducto(producto: ProductoSanitario | undefined) {
    setProductoId(producto?.id ?? '')
    if (!producto) return
    setDosis(producto.dosisHabitual ?? '')
    setVia(producto.viaHabitual ?? '')
    setDias(String(producto.diasCarencia))
  }

  const diasNumero = Number(dias)
  const diasValidos =
    dias.trim() !== '' &&
    Number.isInteger(diasNumero) &&
    diasNumero >= 0 &&
    diasNumero <= MAX_DIAS_CARENCIA
  const grupo =
    destino === 'LOTE' ? lotes?.find((l) => l.id === loteId) : rodeos?.find((r) => r.id === rodeoId)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const nuevos: Record<string, string> = {}
    if (!productoId) nuevos['datos.productoId'] = 'Elegí el producto'
    if (!dosis.trim()) nuevos['datos.dosis'] = 'Indicá la dosis'
    if (!via) nuevos['datos.via'] = 'Elegí la vía de aplicación'
    if (!diasValidos) nuevos['datos.diasCarencia'] = `Un número de 0 a ${MAX_DIAS_CARENCIA}`
    if (!animalesFijos) {
      if (destino === 'ANIMALES' && elegidos.length === 0)
        nuevos.destino = 'Elegí al menos un animal'
      if (destino === 'LOTE' && !loteId) nuevos.destino = 'Elegí el lote'
      if (destino === 'RODEO' && !rodeoId) nuevos.destino = 'Elegí el rodeo'
    }
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0 || !via) return

    try {
      const operacion = await registrar.mutateAsync({
        // Generado acá: si la red falla y se reintenta, el backend no la duplica
        operacionId: crypto.randomUUID(),
        tipo: 'TRATAMIENTO',
        fecha,
        animalIds: animalesFijos
          ? animalIds
          : destino === 'ANIMALES'
            ? elegidos.map((a) => a.id)
            : [],
        loteId: !animalesFijos && destino === 'LOTE' ? loteId : null,
        rodeoId: !animalesFijos && destino === 'RODEO' ? rodeoId : null,
        datos: {
          productoId,
          dosis: dosis.trim(),
          via,
          veterinario: veterinario.trim() || null,
          diasCarencia: diasNumero,
        },
      })
      onHecho(operacion)
    } catch (err) {
      setErrores(getFieldErrors(err))
      setError(getErrorMessage(err))
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <FormError message={error} />

      {!animalesFijos && (
        <div className="space-y-3">
          <SelectField
            label="Se aplica a"
            value={destino}
            onChange={(e) => {
              setDestino(e.target.value as Destino)
              setErrores({})
            }}
          >
            <option value="ANIMALES">Animales puntuales</option>
            <option value="LOTE">Todo un lote</option>
            <option value="RODEO">Todo un rodeo</option>
          </SelectField>
          {destino === 'ANIMALES' && (
            <ElegirAnimales
              establecimientoId={establecimientoId}
              elegidos={elegidos}
              onChange={setElegidos}
              error={errores.destino}
            />
          )}
          {destino === 'LOTE' && (
            <SelectField
              label="Lote"
              value={loteId}
              onChange={(e) => setLoteId(e.target.value)}
              error={errores.destino}
              hint={grupo ? textoGrupo(grupo.cantidadAnimales) : undefined}
            >
              <option value="">Elegí un lote</option>
              {lotes?.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.nombre}
                </option>
              ))}
            </SelectField>
          )}
          {destino === 'RODEO' && (
            <SelectField
              label="Rodeo"
              value={rodeoId}
              onChange={(e) => setRodeoId(e.target.value)}
              error={errores.destino}
              hint={grupo ? textoGrupo(grupo.cantidadAnimales) : undefined}
            >
              <option value="">Elegí un rodeo</option>
              {rodeos?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.nombre}
                </option>
              ))}
            </SelectField>
          )}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="Fecha"
          type="date"
          required
          max={hoyISO()}
          value={fecha}
          onChange={(e) => setFecha(e.target.value)}
          error={errores.fecha}
        />
        <div>
          <SelectField
            label="Producto"
            value={productoId}
            onChange={(e) => elegirProducto(productos?.find((p) => p.id === e.target.value))}
            error={errores['datos.productoId']}
          >
            <option value="">Elegí un producto</option>
            {productos?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.nombre} ({TIPOS_PRODUCTO[p.tipo].etiqueta.toLowerCase()})
              </option>
            ))}
          </SelectField>
          {puedeCrearProductos && (
            <button
              type="button"
              onClick={() => setCreandoProducto(true)}
              className="text-brand-700 dark:text-brand-300 mt-1.5 inline-flex items-center gap-1 text-xs font-medium hover:underline"
            >
              <Plus className="size-3" />
              {productos?.length === 0 ? 'Cargá el primer producto' : 'Producto nuevo'}
            </button>
          )}
        </div>
        <TextField
          label="Dosis"
          placeholder="Ej: 5 ml"
          maxLength={100}
          value={dosis}
          onChange={(e) => setDosis(e.target.value)}
          error={errores['datos.dosis']}
        />
        <SelectField
          label="Vía"
          value={via}
          onChange={(e) => setVia(e.target.value as ViaAdministracion | '')}
          error={errores['datos.via']}
        >
          <option value="">Elegí la vía</option>
          {LISTA_VIAS.map((v) => (
            <option key={v} value={v}>
              {VIAS[v]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Veterinario (opcional)"
          maxLength={100}
          value={veterinario}
          onChange={(e) => setVeterinario(e.target.value)}
          error={errores['datos.veterinario']}
        />
        <TextField
          label="Días de carencia"
          type="number"
          inputMode="numeric"
          min={0}
          max={MAX_DIAS_CARENCIA}
          value={dias}
          onChange={(e) => setDias(e.target.value)}
          error={errores['datos.diasCarencia']}
          hint={
            diasValidos && diasNumero > 0
              ? `No faenar ni vender hasta el ${formatearFecha(libreDesdeDe(fecha, diasNumero))}`
              : 'Los del producto; podés cambiarlos'
          }
        />
      </div>

      <div className="flex justify-end gap-2 pt-2">
        <Button variant="secondary" onClick={onClose} disabled={registrar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" loading={registrar.isPending}>
          Guardar tratamiento
        </Button>
      </div>

      <ProductoFormDialog
        establecimientoId={establecimientoId}
        open={creandoProducto}
        producto={null}
        onClose={() => setCreandoProducto(false)}
        onGuardado={elegirProducto}
      />
    </form>
  )
}

function textoGrupo(cantidad: number) {
  if (cantidad === 0) return 'No tiene animales'
  return `Se registra un tratamiento por cada uno de los ${cantidad} ${cantidad === 1 ? 'animal' : 'animales'}`
}
