import Ajv2020 from 'ajv/dist/2020'
import agregarFormatos from 'ajv-formats'
import { beforeAll, describe, expect, it } from 'vitest'
import { contratoDe, fijarEscenario, type Contrato, type Operacion } from '@aportaya/simulado'

/**
 * La prueba de contrato del sitio público.
 *
 * Un mock que responde algo que el contrato no declara deja la página verde mientras
 * ya está rota contra el backend de verdad.
 */
const GATEWAY = 'http://localhost/api/v1'
const RUTA = '/grupos/calendario/calcular'
const ajv = agregarFormatos(new Ajv2020({ strict: false, allErrors: true }))

beforeAll(() => ajv.addSchema(contratoDe('grupos') as unknown as object, 'grupos'))

function estadoDeExitoDe(contrato: string, ruta: string, metodo: string): number {
  const operacion = (contratoDe(contrato) as Contrato).paths?.[ruta]?.[metodo] as Operacion | undefined
  if (!operacion) throw new Error(`${contrato} no declara ${metodo.toUpperCase()} ${ruta}`)
  const estado = Object.keys(operacion.responses ?? {})
    .map(Number)
    .filter((c) => c >= 200 && c < 300)
    .sort((a, b) => a - b)[0]
  if (estado === undefined) throw new Error(`${metodo.toUpperCase()} ${ruta} no declara respuesta de éxito`)
  return estado
}

function validarContra(esquema: string, valor: unknown): void {
  const validar = ajv.getSchema(`grupos#/components/schemas/${esquema}`)
  if (!validar) throw new Error(`el contrato no declara el esquema ${esquema}`)
  const vale = validar(valor)
  expect(validar.errors ?? []).toEqual([])
  expect(vale).toBe(true)
}

const CONSULTA = 'desde=2026-01-15&dias=5&alcance=NACIONAL'

describe('calcularPlazoHabil · grupos', () => {
  it('la respuesta simulada cumple SalidaPlazoHabil', async () => {
    const respuesta = await fetch(`${GATEWAY}${RUTA}?${CONSULTA}`)
    expect(respuesta.status).toBe(estadoDeExitoDe('grupos', RUTA, 'get'))
    validarContra('SalidaPlazoHabil', await respuesta.json())
  })

  it('el escenario vacío sigue cumpliendo el contrato: vacío no es inválido', async () => {
    fijarEscenario('calcularPlazoHabil', { tipo: 'vacio' })
    const respuesta = await fetch(`${GATEWAY}${RUTA}?${CONSULTA}`)
    const cuerpo = (await respuesta.json()) as { diasSalteados: unknown[] }
    validarContra('SalidaPlazoHabil', cuerpo)
    expect(cuerpo.diasSalteados).toHaveLength(0)
  })

  it('la respuesta de error cumple Error, con código AP-CU<NN>-<nn>', async () => {
    fijarEscenario('calcularPlazoHabil', { tipo: 'error' })
    const respuesta = await fetch(`${GATEWAY}${RUTA}?${CONSULTA}`)
    expect(respuesta.status).toBe(422)
    const cuerpo = (await respuesta.json()) as { codigo: string }
    validarContra('Error', cuerpo)
    expect(cuerpo.codigo).toMatch(/^AP-CU\d+-\d{2}$/)
  })
})
