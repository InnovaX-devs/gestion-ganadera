import { type RefObject, useEffect } from 'react'

/** Llama a `onDismiss` al hacer clic fuera de `ref` o al apretar Escape. Para menús desplegables. */
export function useDismiss(
  ref: RefObject<HTMLElement | null>,
  abierto: boolean,
  onDismiss: () => void,
) {
  useEffect(() => {
    if (!abierto) return
    const onClick = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) onDismiss()
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onDismiss()
    }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [ref, abierto, onDismiss])
}
