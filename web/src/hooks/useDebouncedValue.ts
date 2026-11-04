import { useEffect, useState } from 'react'

/** Devuelve `valor` recién cuando dejó de cambiar por `ms`. Para no pedir al backend en cada tecla. */
export function useDebouncedValue<T>(valor: T, ms = 300): T {
  const [demorado, setDemorado] = useState(valor)

  useEffect(() => {
    const timer = setTimeout(() => setDemorado(valor), ms)
    return () => clearTimeout(timer)
  }, [valor, ms])

  return demorado
}
