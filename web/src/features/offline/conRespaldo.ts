import { esErrorDeRed } from './red'

/**
 * Primero el servidor; si no hay conexión, lo guardado en el dispositivo (#30). Si tampoco hay nada
 * guardado (nunca se sincronizó), se mantiene el error de red original.
 */
export async function conRespaldo<T>(
  remoto: () => Promise<T>,
  local: () => Promise<T | undefined>,
): Promise<T> {
  try {
    return await remoto()
  } catch (error) {
    if (!esErrorDeRed(error)) throw error
    const guardado = await local().catch(() => undefined)
    if (guardado === undefined) throw error
    return guardado
  }
}
