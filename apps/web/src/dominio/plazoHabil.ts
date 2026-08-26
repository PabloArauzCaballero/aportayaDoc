import { consultar } from './gateway'

/**
 * `calcularPlazoHabil` del contrato de `grupos` (CU-59).
 *
 * El resultado **se guarda donde se use y no se recalcula**: si después se declara un
 * feriado, un plazo ya fijado no se mueve. Acá se consulta para mostrar, que es otra
 * cosa — y por eso la página dice de cuándo es el cálculo.
 */
export type DiaSalteado = { fecha: string; motivo: string }
export type SalidaPlazoHabil = { fechaLimite: string; diasSalteados: DiaSalteado[] }

export type Consulta = {
  desde: string
  dias: number
  alcance: 'NACIONAL' | 'DEPARTAMENTAL' | 'PLATAFORMA' | 'GRUPO'
}

export function calcularPlazo(consulta: Consulta, senal?: AbortSignal): Promise<SalidaPlazoHabil> {
  const parametros = new URLSearchParams({
    desde: consulta.desde,
    dias: String(consulta.dias),
    alcance: consulta.alcance,
  })
  return consultar<SalidaPlazoHabil>(`/grupos/calendario/calcular?${parametros}`, senal)
}
