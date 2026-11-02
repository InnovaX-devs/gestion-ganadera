import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { interpretarLectura } from '../src/features/lector/lectura.ts'
import { DetectorTeclado } from '../src/features/lector/teclado.ts'

describe('interpretarLectura', () => {
  const casos: [string, string, { eid: string; kg?: number } | null][] = [
    ['15 dígitos seguidos', '982000123456789', { eid: '982000123456789' }],
    ['con espacio (Allflex, Tru-Test)', '982 000123456789', { eid: '982000123456789' }],
    ['con guion', '032-000987654321', { eid: '032000987654321' }],
    ['con prefijo de texto', 'LA 982 000123456789', { eid: '982000123456789' }],
    ['con etiqueta', 'EID:982000123456789', { eid: '982000123456789' }],
    ['con STX/ETX y fin de línea', '\u0002982000123456789\u0003\r\n', { eid: '982000123456789' }],
    ['16 dígitos con bandera de animal', '1982000123456789', { eid: '982000123456789' }],
    ['bandera separada', '1 982 000123456789', { eid: '982000123456789' }],
    ['con peso después (coma)', '982 000123456789,452.5', { eid: '982000123456789', kg: 452.5 }],
    ['con peso y decimal con coma', '982000123456789;452,5', { eid: '982000123456789', kg: 452.5 }],
    ['con peso antes y unidad', '452.5 kg 982000123456789', { eid: '982000123456789', kg: 452.5 }],
    ['peso entero con tab', '982000123456789\t380', { eid: '982000123456789', kg: 380 }],
    ['ignora un "peso" imposible', '982000123456789 99999', { eid: '982000123456789' }],
    ['bandera y peso', '1 982 000123456789 452', { eid: '982000123456789', kg: 452 }],
    ['ignora números chicos que no son peso', '982000123456789 #3', { eid: '982000123456789' }],
    ['texto cualquiera', 'hola', null],
    ['muy corto', '982000123', null],
    ['demasiados dígitos', '98200012345678912', null],
    ['vacío', '   ', null],
  ]

  for (const [nombre, entrada, esperado] of casos) {
    it(nombre, () => assert.deepEqual(interpretarLectura(entrada), esperado))
  }
})

describe('DetectorTeclado', () => {
  /** Escribe el texto con `cada` ms entre teclas y termina con Enter. */
  function escribir(detector: DetectorTeclado, texto: string, cada: number, desde = 1000) {
    let t = desde
    for (const c of texto) {
      detector.tecla(c, t)
      t += cada
    }
    return detector.tecla('Enter', t)?.lectura ?? null
  }

  it('reconoce un lector (rápido)', () => {
    assert.deepEqual(escribir(new DetectorTeclado(), '982 000123456789', 8), {
      eid: '982000123456789',
    })
  })

  it('ignora a una persona tecleando los mismos números', () => {
    assert.equal(escribir(new DetectorTeclado(), '982000123456789', 120), null)
  })

  it('ignora lo que se escribió antes de una pausa', () => {
    const d = new DetectorTeclado()
    d.tecla('7', 0)
    d.tecla('7', 10)
    assert.deepEqual(escribir(d, '982000123456789', 5, 500), { eid: '982000123456789' })
  })

  it('no confunde un Enter suelto', () => {
    assert.equal(new DetectorTeclado().tecla('Enter', 0), null)
  })

  it('ignora teclas especiales en el medio', () => {
    const d = new DetectorTeclado()
    let t = 0
    for (const c of '982000123456789') {
      d.tecla('Shift', t)
      d.tecla(c, t + 1)
      t += 6
    }
    assert.deepEqual(d.tecla('Enter', t), {
      lectura: { eid: '982000123456789' },
      crudo: '982000123456789',
    })
  })
})
