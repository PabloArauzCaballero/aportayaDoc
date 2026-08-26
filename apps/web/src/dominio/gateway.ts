/**
 * La unica salida a la red del sitio publico.
 *
 * Es mas chica que la del backoffice o la de la app a proposito: **el sitio publico
 * no tiene sesion**. No manda token, no refresca nada y no guarda credenciales. Lo
 * unico que comparte con las otras dos es la traza, para que una consulta desde el
 * sitio se pueda seguir en el log del backend igual que las demas.
 */
const BASE: string = import.meta.env.PUBLIC_GATEWAY ?? '/api/v1'

export class ErrorDeApi extends Error {
  readonly codigo: string
  readonly trazaId: string
  readonly estado: number

  constructor(codigo: string, trazaId: string, estado: number, mensaje: string) {
    super(mensaje)
    this.name = 'ErrorDeApi'
    this.codigo = codigo
    this.trazaId = trazaId
    this.estado = estado
  }
}

export class ErrorDeRed extends Error {
  constructor() {
    super('No pudimos conectarnos. Revisá tu conexión y volvé a intentar.')
    this.name = 'ErrorDeRed'
  }
}

const POR_ESTADO: Record<number, string> = {
  404: 'No encontramos lo que buscabas.',
  422: 'Los datos que enviaste no cumplen una regla del servicio.',
  429: 'Demasiadas consultas seguidas. Esperá un momento.',
}

export async function consultar<T>(ruta: string, senal?: AbortSignal): Promise<T> {
  const trazaId = crypto.randomUUID()
  let respuesta: Response
  try {
    respuesta = await fetch(`${BASE}${ruta}`, {
      headers: { Accept: 'application/json', 'x-request-id': trazaId },
      signal: senal,
    })
  } catch {
    throw new ErrorDeRed()
  }

  const texto = await respuesta.text()
  const cuerpo: unknown = texto.length > 0 ? JSON.parse(texto) : null
  if (respuesta.ok) return cuerpo as T

  const c = cuerpo as { codigo?: string; trazaId?: string } | null
  throw new ErrorDeApi(
    typeof c?.codigo === 'string' ? c.codigo : 'AP-DESCONOCIDO',
    typeof c?.trazaId === 'string' ? c.trazaId : trazaId,
    respuesta.status,
    POR_ESTADO[respuesta.status] ?? 'No pudimos completar la consulta. Volvé a intentar en un momento.',
  )
}
