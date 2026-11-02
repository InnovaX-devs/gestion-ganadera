import { ListadoReproductivo } from '@/features/reproduccion/ListadoReproductivo'
import { ServicioDialog } from '@/features/reproduccion/ServicioDialog'
import { resumenServicio } from '@/features/reproduccion/config'
import type { ServicioDatos } from '@/features/reproduccion/types'

export function ServiciosPage() {
  return (
    <ListadoReproductivo<ServicioDatos>
      tipo="SERVICIO"
      titulo="Servicios"
      descripcion={(est) => `Servicios naturales e inseminaciones en ${est}.`}
      vacio={{
        titulo: 'Todavía no hay servicios',
        texto: 'Registrá la entrada de los toros a un rodeo o una inseminación puntual.',
      }}
      botonNuevo="Nuevo servicio"
      alAnular="y su estado reproductivo vuelve a lo que marcaba antes."
      detalle={(e) => <span className="whitespace-pre-line">{resumenServicio(e.datos)}</span>}
      dialogo={(p) => <ServicioDialog {...p} />}
    />
  )
}
