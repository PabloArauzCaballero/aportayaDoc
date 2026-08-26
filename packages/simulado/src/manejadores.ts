import { HttpResponse, delay, http, type DefaultBodyType, type RequestHandler } from 'msw'
import { escenarioDe } from './escenarios'
import { muestraDe, type Modo } from './muestra'
import { resolver } from './referencias'
import { METODOS, type Contrato, type Esquema, type Metodo, type Operacion } from './tipos'

export type OpcionesDelSimulado = {
  /** Prefijo del gateway. Por omision se toma de `servers[0].url` del contrato. */
  base?: string
}

/**
 * Convierte un contrato OpenAPI en manejadores de MSW. La respuesta sale del
 * esquema declarado, nunca de un literal escrito aca.
 */
export function manejadoresDe(documento: Contrato, opciones: OpcionesDelSimulado = {}): RequestHandler[] {
  const base = opciones.base ?? documento.servers?.[0]?.url ?? ''
  const manejadores: RequestHandler[] = []

  for (const [ruta, operaciones] of Object.entries(documento.paths ?? {})) {
    for (const metodo of METODOS) {
      const operacion = operaciones[metodo]
      if (!operacion) continue
      manejadores.push(manejador(documento, base, ruta, metodo, operacion))
    }
  }
  return manejadores
}

function manejador(
  documento: Contrato,
  base: string,
  ruta: string,
  metodo: Metodo,
  operacion: Operacion,
): RequestHandler {
  // `{cuentaId}` en OpenAPI es `:cuentaId` en MSW. El `*` inicial hace que el
  // simulado responda venga la peticion del emulador, del navegador o de Jest,
  // que no comparten host.
  const patron = `*${base}${ruta.replace(/\{(\w+)\}/g, ':$1')}`
  const nombre = operacion.operationId ?? `${metodo} ${ruta}`

  return http[metodo](patron, async () => {
    const escenario = escenarioDe(nombre)

    if (escenario.tipo === 'sinRed') return HttpResponse.error()
    if (escenario.tipo === 'demora') await delay(escenario.ms)

    if (escenario.tipo === 'error') {
      const estado = escenario.estado ?? primerEstado(operacion, (c) => c >= 400)
      if (estado === undefined) {
        throw new Error(`${nombre} no declara ninguna respuesta de error en el contrato`)
      }
      return HttpResponse.json(cuerpoDe(documento, operacion, estado), { status: estado })
    }

    const estado = primerEstado(operacion, (c) => c >= 200 && c < 300)
    if (estado === undefined) {
      throw new Error(`${nombre} no declara ninguna respuesta de exito en el contrato`)
    }
    const modo: Modo = escenario.tipo === 'vacio' ? 'minimo' : 'representativo'
    return HttpResponse.json(cuerpoDe(documento, operacion, estado, modo), { status: estado })
  })
}

function primerEstado(operacion: Operacion, cumple: (codigo: number) => boolean): number | undefined {
  return Object.keys(operacion.responses ?? {})
    .map(Number)
    .filter((c) => Number.isFinite(c) && cumple(c))
    .sort((a, b) => a - b)[0]
}

function cuerpoDe(
  documento: Contrato,
  operacion: Operacion,
  estado: number,
  modo: Modo = 'representativo',
): DefaultBodyType {
  const declarada = operacion.responses?.[String(estado)]
  if (!declarada) return null
  const respuesta = resolver(documento, declarada)
  const esquema = respuesta.content?.['application/json']?.schema
  return esquema ? (muestraDe(documento, esquema as Esquema, `${estado}`, modo) as DefaultBodyType) : null
}
