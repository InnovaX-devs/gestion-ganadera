import { useState } from 'react'
import { LoaderCircle, Pencil, Plus, Trash2 } from 'lucide-react'
import { Button } from '@/components/Button'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { Dialog } from '@/components/Dialog'
import { FormError } from '@/components/FormError'
import { getErrorMessage } from '@/lib/api'
import { TIPOS_PRODUCTO, formatearDiasCarencia } from './config'
import { ProductoFormDialog } from './ProductoFormDialog'
import { useEliminarProducto, useProductos } from './queries'
import type { ProductoSanitario } from './types'

interface Props {
  establecimientoId: string
  open: boolean
  /** Agregar, editar y dar de baja productos. Ver el catálogo lo puede cualquier miembro. */
  puedeEditar: boolean
  onClose: () => void
}

/** Catálogo de productos sanitarios del establecimiento. */
export function CatalogoDialog({ establecimientoId, open, puedeEditar, onClose }: Props) {
  const { data: productos, isPending, error } = useProductos(establecimientoId)
  const eliminar = useEliminarProducto(establecimientoId)
  const [formulario, setFormulario] = useState<{ producto: ProductoSanitario | null } | null>(null)
  const [aBorrar, setABorrar] = useState<ProductoSanitario | null>(null)

  const cerrarBorrado = () => {
    setABorrar(null)
    eliminar.reset()
  }

  return (
    <>
      <Dialog
        open={open}
        onClose={onClose}
        title="Productos sanitarios"
        description="Vacunas, antiparasitarios, antibióticos y otros que usás en el campo."
      >
        <div className="space-y-4">
          {error && <FormError message={getErrorMessage(error)} />}
          {isPending && <LoaderCircle className="mx-auto size-5 animate-spin text-stone-400" />}
          {productos && productos.length === 0 && (
            <p className="rounded-lg border border-dashed border-stone-300 px-4 py-6 text-center text-sm text-stone-500 dark:border-stone-700">
              Todavía no cargaste productos.
              {puedeEditar && ' Agregá el primero para usarlo en los tratamientos.'}
            </p>
          )}
          {productos && productos.length > 0 && (
            <ul className="divide-y divide-stone-100 rounded-lg border border-stone-200 dark:divide-stone-800 dark:border-stone-800">
              {productos.map((p) => {
                const { etiqueta, icono: Icono } = TIPOS_PRODUCTO[p.tipo]
                return (
                  <li key={p.id} className="flex items-center gap-3 px-3 py-2.5">
                    <Icono className="size-4 shrink-0 text-stone-400" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{p.nombre}</p>
                      <p className="truncate text-xs text-stone-500">
                        {[etiqueta, formatearDiasCarencia(p.diasCarencia)].join(' · ')}
                      </p>
                    </div>
                    {puedeEditar && (
                      <div className="flex shrink-0 gap-1">
                        <button
                          type="button"
                          onClick={() => setFormulario({ producto: p })}
                          aria-label={`Editar ${p.nombre}`}
                          title="Editar"
                          className="rounded-md p-2 text-stone-500 hover:bg-stone-100 dark:hover:bg-stone-800"
                        >
                          <Pencil className="size-4" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setABorrar(p)}
                          aria-label={`Quitar ${p.nombre}`}
                          title="Quitar del catálogo"
                          className="rounded-md p-2 text-stone-500 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/50"
                        >
                          <Trash2 className="size-4" />
                        </button>
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={onClose}>
              Cerrar
            </Button>
            {puedeEditar && (
              <Button onClick={() => setFormulario({ producto: null })}>
                <Plus className="size-4" />
                Nuevo producto
              </Button>
            )}
          </div>
        </div>
      </Dialog>

      <ProductoFormDialog
        establecimientoId={establecimientoId}
        open={formulario !== null}
        producto={formulario?.producto ?? null}
        onClose={() => setFormulario(null)}
      />

      <ConfirmDialog
        open={aBorrar !== null}
        title={`Quitar ${aBorrar?.nombre ?? ''}`}
        description="Deja de ofrecerse al cargar tratamientos. Los que ya registraste lo siguen mostrando."
        confirmLabel="Quitar"
        loading={eliminar.isPending}
        error={eliminar.error ? getErrorMessage(eliminar.error) : null}
        onClose={cerrarBorrado}
        onConfirm={() => aBorrar && eliminar.mutate(aBorrar.id, { onSuccess: cerrarBorrado })}
      />
    </>
  )
}
