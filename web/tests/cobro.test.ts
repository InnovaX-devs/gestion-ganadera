import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  contratable,
  diaSiguiente,
  siguienteVencimiento,
  sumarMesesISO,
} from '../src/features/plan/cobro.ts'
import type { Plan } from '../src/features/plan/types.ts'

describe('diaSiguiente', () => {
  it('suma un día, cruzando meses y años', () => {
    assert.equal(diaSiguiente('2026-11-03'), '2026-11-04')
    assert.equal(diaSiguiente('2026-11-30'), '2026-12-01')
    assert.equal(diaSiguiente('2026-12-31'), '2027-01-01')
    assert.equal(diaSiguiente('2028-02-28'), '2028-02-29')
  })
})

describe('contratable', () => {
  const plan = (habilitado: boolean) =>
    ({ cobro: { habilitado, diasGracia: 7 } }) as unknown as Plan

  it('solo con el pago online configurado y un precio mayor a cero', () => {
    assert.equal(contratable(plan(true), 15000), true)
    assert.equal(contratable(plan(false), 15000), false)
    assert.equal(contratable(plan(true), 0), false)
    assert.equal(contratable(plan(true), null), false)
    assert.equal(contratable({} as Plan, 15000), false)
  })
})

describe('siguienteVencimiento (pago por transferencia, del 1 al 10)', () => {
  it('sin pago o vencido: hasta el 10 del mes que viene', () => {
    assert.equal(siguienteVencimiento(null, '2026-11-05', 10), '2026-12-10')
    assert.equal(siguienteVencimiento('2026-11-10', '2026-11-12', 10), '2026-12-10')
    assert.equal(siguienteVencimiento(null, '2026-12-20', 10), '2027-01-10')
  })

  it('al día: un mes más sin perder días', () => {
    assert.equal(siguienteVencimiento('2026-12-10', '2026-12-02', 10), '2027-01-10')
    assert.equal(siguienteVencimiento('2026-12-10', '2026-12-10', 10), '2027-01-10')
  })

  it('respeta el largo del mes', () => {
    assert.equal(siguienteVencimiento(null, '2027-01-31', 31), '2027-02-28')
    assert.equal(sumarMesesISO('2027-01-31', 1), '2027-02-28')
  })
})
