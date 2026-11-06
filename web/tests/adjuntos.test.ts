import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  TAMANIO_MAXIMO,
  comoJpg,
  formatearTamanio,
  validarTamanio,
  validarTipo,
} from '../src/features/adjuntos/validacion.ts'

const archivo = (name: string, type: string, size = 1000) => ({ name, type, size })

describe('validarTipo', () => {
  it('acepta fotos y PDF', () => {
    for (const [nombre, tipo] of [
      ['vaca.jpg', 'image/jpeg'],
      ['vaca.png', 'image/png'],
      ['vaca.webp', 'image/webp'],
      ['vaca.heic', 'image/heic'],
      ['informe.pdf', 'application/pdf'],
    ]) {
      assert.equal(validarTipo(archivo(nombre!, tipo!)), null, nombre)
    }
  })

  it('una foto HEIC sin tipo informado se reconoce por la extensión', () => {
    assert.equal(validarTipo(archivo('IMG_0001.HEIC', '')), null)
  })

  it('rechaza lo demás', () => {
    for (const [nombre, tipo] of [
      ['planilla.xlsx', 'application/vnd.ms-excel'],
      ['video.mp4', 'video/mp4'],
      ['virus.exe', 'application/x-msdownload'],
      ['pagina.html', 'text/html'],
      ['dibujo.svg', 'image/svg+xml'],
    ]) {
      assert.match(
        validarTipo(archivo(nombre!, tipo!)) ?? '',
        /solo se pueden adjuntar fotos y archivos PDF/,
        nombre,
      )
    }
  })
})

describe('validarTamanio', () => {
  it('acepta hasta el máximo inclusive', () => {
    assert.equal(validarTamanio(archivo('a.jpg', 'image/jpeg', TAMANIO_MAXIMO)), null)
  })

  it('rechaza lo que pasa del máximo, con el tamaño real', () => {
    assert.equal(
      validarTamanio(archivo('grande.pdf', 'application/pdf', 12 * 1024 * 1024)),
      'grande.pdf: pesa 12 MB y el máximo es 10 MB',
    )
  })

  it('rechaza archivos vacíos', () => {
    assert.equal(validarTamanio(archivo('a.jpg', 'image/jpeg', 0)), 'a.jpg: el archivo está vacío')
  })
})

describe('formatearTamanio', () => {
  it('KB hasta el mega y MB después', () => {
    assert.equal(formatearTamanio(1), '1 KB')
    assert.equal(formatearTamanio(850 * 1024), '850 KB')
    assert.equal(formatearTamanio(1024 * 1024), '1 MB')
    assert.equal(formatearTamanio(2.5 * 1024 * 1024), '2,5 MB')
  })
})

describe('comoJpg', () => {
  it('cambia la extensión', () => {
    assert.equal(comoJpg('vaca.png'), 'vaca.jpg')
    assert.equal(comoJpg('mi.foto.webp'), 'mi.foto.jpg')
    assert.equal(comoJpg('sin-extension'), 'sin-extension.jpg')
  })
})
