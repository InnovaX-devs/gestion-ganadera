import { useContext } from 'react'
import { EstablecimientoActivoContext } from './EstablecimientoActivoContext'

/**
 * Establecimiento elegido en el header. Las pantallas de lotes, animales, eventos, etc. lo usan
 * para saber de qué campo mostrar datos.
 */
export function useEstablecimientoActivo() {
  const context = useContext(EstablecimientoActivoContext)
  if (!context) {
    throw new Error(
      'useEstablecimientoActivo tiene que usarse dentro de <EstablecimientoActivoProvider>',
    )
  }
  return context
}
