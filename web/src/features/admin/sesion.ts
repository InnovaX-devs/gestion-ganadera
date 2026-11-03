/**
 * Sesión del panel de administración (#35). Va aparte de la sesión de la app: entrar al panel no
 * te loguea en ninguna cuenta de cliente, ni al revés. Un solo token, sin refresh: al vencer se
 * vuelve a pedir la contraseña.
 */
export interface SesionAdmin {
  accessToken: string
  email: string
  /** Epoch en ms */
  venceAt: number
}

const KEY = 'admin_sesion'

export const sesionAdmin = {
  leer(): SesionAdmin | null {
    try {
      const raw = localStorage.getItem(KEY)
      if (!raw) return null
      const sesion = JSON.parse(raw) as SesionAdmin
      if (!sesion.accessToken || sesion.venceAt <= Date.now()) {
        localStorage.removeItem(KEY)
        return null
      }
      return sesion
    } catch {
      return null
    }
  },
  guardar(sesion: SesionAdmin) {
    try {
      localStorage.setItem(KEY, JSON.stringify(sesion))
    } catch {
      /* sin persistencia: dura lo que la pestaña */
    }
  },
  borrar() {
    try {
      localStorage.removeItem(KEY)
    } catch {
      /* nada */
    }
  },
}
