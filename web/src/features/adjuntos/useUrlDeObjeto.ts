import { useEffect, useState } from 'react'

/**
 * URL temporal para mostrar un archivo en memoria (`<img src>`). Se crea en un efecto y se suelta
 * en su limpieza: así funciona también con StrictMode, que monta, desmonta y vuelve a montar, y una
 * URL creada en el render quedaría ya soltada en el segundo montaje.
 */
export function useUrlDeObjeto(archivo: Blob | null | undefined): string | null {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!archivo) return
    const nueva = URL.createObjectURL(archivo)
    // Es sincronizar con algo externo (el registro de URLs del navegador), que es para lo que sirven los efectos
    // oxlint-disable-next-line react/set-state-in-effect
    setUrl(nueva)
    return () => URL.revokeObjectURL(nueva)
  }, [archivo])
  // Sin archivo no se muestra nada, aunque quede la URL del anterior un instante
  return archivo ? url : null
}
