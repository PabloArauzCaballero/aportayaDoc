import identidad from '../generado/identidad.json'
import notificaciones from '../generado/notificaciones.json'
import nucleoFinanciero from '../generado/nucleo-financiero.json'
import { manejadoresDe, type OpcionesDelSimulado } from './manejadores'
import type { Contrato } from './tipos'

/**
 * Los contratos que el simulado sabe responder. El JSON es **generado** desde el
 * `.yaml` de cada servicio con `yarn workspace @aportaya/simulado contratos`; no
 * se versiona y no se edita. Si falta, no compila — que es como tiene que ser:
 * un simulado que arranca sin contrato simula lo que se le ocurre.
 */
export const CONTRATOS: Contrato[] = [
  identidad as unknown as Contrato,
  nucleoFinanciero as unknown as Contrato,
  notificaciones as unknown as Contrato,
]

export function manejadoresDeTodos(opciones?: OpcionesDelSimulado) {
  return CONTRATOS.flatMap((contrato) => manejadoresDe(contrato, opciones))
}

export function contratoDe(titulo: string): Contrato {
  const encontrado = CONTRATOS.find((c) => c.info.title === titulo)
  if (!encontrado) throw new Error(`no hay contrato generado para ${titulo}`)
  return encontrado
}
