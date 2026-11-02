import { type Lectura, interpretarLectura } from './lectura.ts'

/**
 * Reconoce un lector en "modo teclado" (HID): el bastón se empareja como un teclado Bluetooth y
 * "escribe" la caravana con un Enter al final. La diferencia con una persona es la velocidad: el
 * lector escribe todo en unas pocas decenas de milisegundos.
 *
 * Es lógica pura (sin DOM) para poder probarla: recibe cada tecla con su hora.
 */
export class DetectorTeclado {
  private texto = ''
  private inicio = 0
  private ultima = 0

  /** Si pasa más que esto entre dos teclas (ms), es una persona: se empieza de nuevo */
  private readonly pausaMaxima: number
  /** Promedio máximo entre teclas (ms) para considerarlo un lector */
  private readonly promedioMaximo: number

  constructor(pausaMaxima = 80, promedioMaximo = 35) {
    this.pausaMaxima = pausaMaxima
    this.promedioMaximo = promedioMaximo
  }

  /**
   * Una tecla, con su `key` (como en KeyboardEvent). Si con esta tecla terminó de llegar una
   * caravana, devuelve la lectura y el texto tal cual se tecleó (por si quedó escrito en un campo).
   */
  tecla(key: string, ahora: number): { lectura: Lectura; crudo: string } | null {
    if (key === 'Enter' || key === 'Tab') {
      const lectura = this.terminar(ahora)
      const crudo = this.texto
      this.reiniciar()
      return lectura && { lectura, crudo }
    }
    if (key.length !== 1) return null // Shift, flechas, etc.

    if (!this.texto || ahora - this.ultima > this.pausaMaxima) {
      this.texto = ''
      this.inicio = ahora
    }
    this.texto += key
    this.ultima = ahora
    return null
  }

  /** Si lo que se viene tecleando parece un lector (para no dejar que un espacio haga scroll). */
  get enCurso(): boolean {
    return this.texto.length > 0
  }

  reiniciar() {
    this.texto = ''
  }

  private terminar(ahora: number): Lectura | null {
    if (this.texto.length < 15 || ahora - this.ultima > this.pausaMaxima) return null
    const promedio = (this.ultima - this.inicio) / (this.texto.length - 1)
    if (promedio > this.promedioMaximo) return null
    return interpretarLectura(this.texto)
  }
}
