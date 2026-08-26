import Ajv2020 from 'ajv/dist/2020'
import agregarFormatos from 'ajv-formats'
import { beforeAll, describe, expect, it } from 'vitest'
import { contratoDe, fijarEscenario, type Contrato, type Operacion } from '@aportaya/simulado'

/**
 * La prueba de contrato del backoffice.
 *
 * Un mock que responde algo que el contrato no declara deja la pantalla verde
 * mientras el backoffice ya está roto contra el backend de verdad. Acá la respuesta
 * simulada se valida contra **el esquema del contrato**, no contra lo que alguien
 * esperaba.
 */
const GATEWAY = 'http://localhost/api/v1'
const CUENTA = '11111111-1111-4111-8111-111111111111'
const ajv = agregarFormatos(new Ajv2020({ strict: false, allErrors: true }))

beforeAll(() => {
  ajv.addSchema(contratoDe('nucleo-financiero') as unknown as object, 'nucleo-financiero')
})

/** El estado de éxito sale del contrato, no de un número escrito acá. */
function estadoDeExitoDe(contrato: string, ruta: string, metodo: string): number {
  const documento = contratoDe(contrato) as Contrato
  const operacion = documento.paths?.[ruta]?.[metodo] as Operacion | undefined
  if (!operacion) throw new Error(`${contrato} no declara ${metodo.toUpperCase()} ${ruta}`)
  const estado = Object.keys(operacion.responses ?? {})
    .map(Number)
    .filter((c) => c >= 200 && c < 300)
    .sort((a, b) => a - b)[0]
  if (estado === undefined) throw new Error(`${metodo.toUpperCase()} ${ruta} no declara respuesta de éxito`)
  return estado
}

function validarContra(contrato: string, esquema: string, valor: unknown): void {
  const validar = ajv.getSchema(`${contrato}#/components/schemas/${esquema}`)
  if (!validar) throw new Error(`el contrato ${contrato} no declara el esquema ${esquema}`)
  const vale = validar(valor)
  expect(validar.errors ?? []).toEqual([])
  expect(vale).toBe(true)
}

describe('consultarSaldo · nucleo-financiero', () => {
  it('la respuesta simulada cumple SaldoBilletera', async () => {
    const respuesta = await fetch(`${GATEWAY}/billetera/${CUENTA}/saldo`)
    expect(respuesta.status).toBe(estadoDeExitoDe('nucleo-financiero', '/billetera/{cuentaId}/saldo', 'get'))
    validarContra('nucleo-financiero', 'SaldoBilletera', await respuesta.json())
  })

  it('el escenario vacío sigue cumpliendo el contrato: vacío no es inválido', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'vacio' })
    const respuesta = await fetch(`${GATEWAY}/billetera/${CUENTA}/saldo`)
    validarContra('nucleo-financiero', 'SaldoBilletera', await respuesta.json())
  })

  it('el importe viaja como CADENA decimal, nunca como número', async () => {
    const respuesta = await fetch(`${GATEWAY}/billetera/${CUENTA}/saldo`)
    const cuerpo = (await respuesta.json()) as { disponible: { monto: unknown } }
    expect(typeof cuerpo.disponible.monto).toBe('string')
    expect(cuerpo.disponible.monto).toMatch(/^-?\d+\.\d{2}$/)
  })
})
