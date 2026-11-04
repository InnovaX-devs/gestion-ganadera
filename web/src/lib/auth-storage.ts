const ACCESS_KEY = 'access_token'
const REFRESH_KEY = 'refresh_token'
/** Último usuario validado: para abrir la app sin conexión (#30). */
const USUARIO_KEY = 'usuario'

// localStorage puede fallar (modo privado, almacenamiento bloqueado): nunca romper la app por eso.
function leer(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function escribir(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    /* sin persistencia */
  }
}

export const authStorage = {
  getAccessToken: () => leer(ACCESS_KEY),
  getRefreshToken: () => leer(REFRESH_KEY),
  hasSession: () => leer(ACCESS_KEY) !== null || leer(REFRESH_KEY) !== null,
  setTokens(accessToken: string, refreshToken: string) {
    escribir(ACCESS_KEY, accessToken)
    escribir(REFRESH_KEY, refreshToken)
  },
  clear() {
    escribir(ACCESS_KEY, null)
    escribir(REFRESH_KEY, null)
    escribir(USUARIO_KEY, null)
  },
  /** Se guarda cada vez que el backend confirma la sesión. */
  setUsuario(usuario: unknown) {
    escribir(USUARIO_KEY, JSON.stringify(usuario))
  },
  getUsuario<T>(): T | null {
    try {
      const guardado = leer(USUARIO_KEY)
      return guardado ? (JSON.parse(guardado) as T) : null
    } catch {
      return null
    }
  },
}
