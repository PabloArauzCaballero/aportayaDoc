import Ajv2020 from 'ajv/dist/2020'
import agregarFormatos from 'ajv-formats'
import { contratoDe, fijarEscenario, type Contrato, type Operacion } from '@aportaya/simulado'

/**
 * La prueba de contrato de CU-01.
 *
 * Un mock que responde algo que el contrato no declara deja la pantalla verde
 * mientras la app ya esta rota contra el backend de verdad. Esto lo impide: la
 * respuesta simulada se valida contra **el esquema del contrato**, no contra lo
 * que alguien espero.
 */
const GATEWAY = 'http://localhost/api/v1'

// El servidor simulado lo levanta `preparar.tsx`, uno para todos los corredores.
// Dos instancias de MSW en la misma corrida se pisan los interceptores, y la que
// falla nunca es la que tiene el defecto.
const ajv = agregarFormatos(new Ajv2020({ strict: false, allErrors: true }))

beforeAll(() => {
  for (const titulo of ['identidad', 'nucleo-financiero']) {
    ajv.addSchema(contratoDe(titulo) as unknown as object, titulo)
  }
})

/**
 * El estado de éxito **sale del contrato**, no de un número escrito acá.
 *
 * Fijarlo a mano convierte una decisión legítima del servicio dueño —CU-01 pasó de
 * `201` a `202` cuando la verificación de identidad dejó de ser sincrónica— en una
 * prueba en rojo que no denuncia ningún defecto. Lo que sí se verifica es que el
 * simulado responda **el** código declarado, y que sea de éxito.
 */
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

const REGISTRO = {
  telefonoE164: '+59170000000',
  nombres: 'Ana',
  apellidos: 'Quispe',
  fechaNacimiento: '1995-04-12',
  documento: { tipo: 'CI', numero: '1234567' },
  aceptaContratos: ['11111111-1111-4111-8111-111111111111'],
}

async function registrar(cabeceras: Record<string, string> = {}) {
  return fetch(`${GATEWAY}/usuarios`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'clave-de-prueba', ...cabeceras },
    body: JSON.stringify(REGISTRO),
  })
}

describe('CU-01 · registro y apertura de billetera', () => {
  it('la respuesta simulada de exito cumple SalidaRegistro', async () => {
    const respuesta = await registrar()
    expect(respuesta.status).toBe(estadoDeExitoDe('identidad', '/usuarios', 'post'))
    validarContra('identidad', 'SalidaRegistro', await respuesta.json())
  })

  it('la respuesta simulada de error cumple Error, con codigo AP-CU<NN>-<nn>', async () => {
    fijarEscenario('registrarUsuario', { tipo: 'error' })
    const respuesta = await registrar()
    expect(respuesta.status).toBe(422)
    const cuerpo = (await respuesta.json()) as { codigo: string }
    validarContra('identidad', 'Error', cuerpo)
    expect(cuerpo.codigo).toMatch(/^AP-CU\d+-\d{2}$/)
  })

  it('el importe de un limite viaja como CADENA decimal, nunca como numero', async () => {
    const respuesta = await registrar()
    const cuerpo = (await respuesta.json()) as {
      limites?: { monto: { monto: unknown; moneda: string } }[]
    }
    // El contrato declara `limites` opcional, pero el simulado emite todas las
    // propiedades declaradas: si no viene ninguno, el generador dejó de hacerlo y
    // esta prueba tiene que enterarse.
    expect(cuerpo.limites ?? []).not.toHaveLength(0)
    for (const limite of cuerpo.limites ?? []) {
      expect(typeof limite.monto.monto).toBe('string')
      expect(limite.monto.monto).toMatch(/^-?\d+\.\d{2}$/)
    }
  })
})

describe('consultarSaldo · nucleo-financiero', () => {
  const cuenta = '11111111-1111-4111-8111-111111111111'

  it('la respuesta simulada cumple SaldoBilletera', async () => {
    const respuesta = await fetch(`${GATEWAY}/billetera/${cuenta}/saldo`)
    expect(respuesta.status).toBe(
      estadoDeExitoDe('nucleo-financiero', '/billetera/{cuentaId}/saldo', 'get'),
    )
    validarContra('nucleo-financiero', 'SaldoBilletera', await respuesta.json())
  })

  it('el escenario vacio sigue cumpliendo el contrato: vacio no es invalido', async () => {
    fijarEscenario('consultarSaldo', { tipo: 'vacio' })
    const respuesta = await fetch(`${GATEWAY}/billetera/${cuenta}/saldo`)
    expect(respuesta.status).toBe(200)
    validarContra('nucleo-financiero', 'SaldoBilletera', await respuesta.json())
  })
})
