/**
 * Los estados que una pantalla tiene que saber mostrar, elegibles por operacion.
 *
 * No es un adorno de desarrollo: `movil-expo` exige cargando, vacio, error y
 * exito en toda pantalla con datos. Si el simulado solo sabe responder que si,
 * los otros tres estados se escriben de memoria y nadie los ve nunca.
 */
export type Escenario =
  | { tipo: 'exito' }
  | { tipo: 'vacio' }
  | { tipo: 'error'; estado?: number }
  | { tipo: 'demora'; ms: number }
  | { tipo: 'sinRed' }

const elegidos = new Map<string, Escenario>()

/** `operacion` es el `operationId` del contrato, que es el nombre del caso de uso. */
export function fijarEscenario(operacion: string, escenario: Escenario): void {
  elegidos.set(operacion, escenario)
}

export function escenarioDe(operacion: string): Escenario {
  return elegidos.get(operacion) ?? { tipo: 'exito' }
}

/** Entre pruebas se reinicia: un escenario que sobrevive contamina la siguiente. */
export function reiniciarEscenarios(): void {
  elegidos.clear()
}
