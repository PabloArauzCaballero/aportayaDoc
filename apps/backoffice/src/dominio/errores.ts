/**
 * Traduccion de `AP-CU<NN>-<nn>` a lenguaje humano.
 *
 * El backend manda el codigo y un mensaje tecnico; el backoffice **no** muestra el
 * mensaje del backend. Un operador que le lee al titular el texto de una excepcion
 * es un operador al que le dimos la herramienta equivocada.
 */
export type CuerpoDeError = {
  codigo: string
  mensaje: string
  detalle?: Record<string, unknown>
  trazaId: string
}

export class ErrorDeApi extends Error {
  readonly codigo: string
  readonly trazaId: string
  readonly estado: number

  constructor(cuerpo: CuerpoDeError, estado: number) {
    super(mensajeDe(cuerpo.codigo, estado))
    this.name = 'ErrorDeApi'
    this.codigo = cuerpo.codigo
    this.trazaId = cuerpo.trazaId
    this.estado = estado
  }
}

export class ErrorDeRed extends Error {
  constructor() {
    super('No pudimos conectarnos con el servicio. Volvé a intentar en un momento.')
    this.name = 'ErrorDeRed'
  }
}

const CATALOGO: Record<string, string> = {
  'AP-CU01-01': 'La verificación de identidad no pasó con los datos cargados.',
  'AP-CU01-03': 'Ya existe una cuenta con ese documento.',
}

/**
 * Un `403` no dice nada más que «no tenés acceso»: detallarlo le confirma a quien
 * prueba que el recurso existe.
 */
const POR_ESTADO: Record<number, string> = {
  401: 'Tu sesión venció. Volvé a ingresar.',
  403: 'No tenés acceso a esto.',
  404: 'No encontramos lo que buscabas.',
  429: 'Demasiadas consultas seguidas. Esperá un momento.',
}

const GENERICO = 'No pudimos completar la operación. Si vuelve a pasar, avisá con el código de seguimiento.'

export function mensajeDe(codigo: string, estado?: number): string {
  return CATALOGO[codigo] ?? (estado !== undefined ? (POR_ESTADO[estado] ?? GENERICO) : GENERICO)
}
