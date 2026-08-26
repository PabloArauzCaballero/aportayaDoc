/**
 * Traduccion de `AP-CU<NN>-<nn>` a lenguaje humano.
 *
 * El backend manda el codigo y un mensaje tecnico; la app **no** muestra el
 * mensaje del backend. El catalogo esta versionado aca porque el texto que lee
 * una persona es decision de producto, no del servicio que fallo.
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
    super(mensajeDe(cuerpo.codigo))
    this.name = 'ErrorDeApi'
    this.codigo = cuerpo.codigo
    this.trazaId = cuerpo.trazaId
    this.estado = estado
  }
}

/** Un fallo de red no es un error de negocio: no tiene codigo ni traza. */
export class ErrorDeRed extends Error {
  constructor() {
    super('No pudimos conectarnos. Revisá tu señal y volvé a intentar.')
    this.name = 'ErrorDeRed'
  }
}

const CATALOGO: Record<string, string> = {
  'AP-CU01-01': 'No pudimos verificar tu identidad con los datos que ingresaste.',
  'AP-CU01-02': 'No podemos abrir tu cuenta en este momento. Comunicate con soporte.',
  'AP-CU01-03': 'Ya existe una cuenta con ese documento.',
  'AP-CU01-04': 'Para abrir tu cuenta necesitás aceptar el contrato.',
  'AP-CU01-05': 'Este servicio no está habilitado por ahora.',
}

const GENERICO = 'Algo salió mal y no pudimos completar la operación. Volvé a intentar en un momento.'

export function mensajeDe(codigo: string): string {
  return CATALOGO[codigo] ?? GENERICO
}

/**
 * El codigo que el catalogo no conoce **no** se inventa: se muestra el generico
 * y se deja la traza a la vista para que soporte pueda seguirla.
 */
export function conocido(codigo: string): boolean {
  return codigo in CATALOGO
}
