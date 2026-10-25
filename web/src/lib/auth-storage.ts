const TOKEN_KEY = 'access_token'

// localStorage puede fallar (modo privado, almacenamiento bloqueado): nunca romper la app por eso.
export const authStorage = {
  getToken(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  setToken(token: string) {
    try {
      localStorage.setItem(TOKEN_KEY, token)
    } catch {
      /* sin persistencia */
    }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* sin persistencia */
    }
  },
}
