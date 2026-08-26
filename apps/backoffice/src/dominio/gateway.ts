import { ErrorDeApi, ErrorDeRed, type CuerpoDeError } from './errores'
import { nuevoIdentificador } from './identificadores'
import { cerrarSesion, guardarAcceso, tokenDeAcceso } from './sesion'

/**
 * La unica salida a la red del backoffice.
 *
 * Ningun componente hace `fetch`: lo hace esta funcion, y por eso la cabecera de
 * traza, la clave de idempotencia y el refresco de sesion existen una sola vez y no
 * once veces mal.
 *
 * **Una sola base: el gateway.** El prefijo de la ruta enruta al servicio; el
 * backoffice no conoce catorce direcciones ni le importa cuantos servicios hay.
 */
const BASE: string = import.meta.env.VITE_GATEWAY ?? '/api/v1'

export type OpcionesDeLlamada = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  cuerpo?: unknown
  /** Toda operacion con efecto la exige, y se reutiliza en el reintento. */
  claveIdempotencia?: string
  senal?: AbortSignal
}

export async function llamar<T>(ruta: string, opciones: OpcionesDeLlamada = {}): Promise<T> {
  const trazaId = nuevoIdentificador()
  const primera = await peticion(ruta, opciones, trazaId)

  // Un 401 dispara UN refresco y UN reintento. Si vuelve a fallar, sesion cerrada:
  // reintentar en bucle contra un token muerto es una pantalla trabada.
  if (primera.status !== 401) return interpretar<T>(primera, trazaId)

  if (!(await refrescar())) {
    cerrarSesion()
    return interpretar<T>(primera, trazaId)
  }
  const segunda = await peticion(ruta, opciones, trazaId)
  if (segunda.status === 401) cerrarSesion()
  return interpretar<T>(segunda, trazaId)
}

async function peticion(ruta: string, opciones: OpcionesDeLlamada, trazaId: string): Promise<Response> {
  const cabeceras: Record<string, string> = {
    Accept: 'application/json',
    // Viaja en cada peticion para que la traza del navegador llegue al log del
    // backend: sin esto, «se me colgo la pantalla» no se puede seguir.
    'x-request-id': trazaId,
  }
  const token = tokenDeAcceso()
  if (token) cabeceras.Authorization = `Bearer ${token}`
  if (opciones.cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json'
  if (opciones.claveIdempotencia) cabeceras['Idempotency-Key'] = opciones.claveIdempotencia

  try {
    return await fetch(`${BASE}${ruta}`, {
      method: opciones.metodo ?? 'GET',
      headers: cabeceras,
      body: opciones.cuerpo === undefined ? undefined : JSON.stringify(opciones.cuerpo),
      signal: opciones.senal,
      // El refresco viaja en cookie HttpOnly y el navegador la manda solo.
      credentials: 'include',
    })
  } catch {
    throw new ErrorDeRed()
  }
}

async function interpretar<T>(respuesta: Response, trazaId: string): Promise<T> {
  if (respuesta.status === 204) return undefined as T
  const texto = await respuesta.text()
  const cuerpo: unknown = texto.length > 0 ? JSON.parse(texto) : null
  if (respuesta.ok) return cuerpo as T
  throw new ErrorDeApi(deError(cuerpo, trazaId), respuesta.status)
}

/**
 * Un error sin la forma del contrato sigue siendo un error: se le pone la traza de
 * esta peticion y se lo trata como desconocido, en vez de romper la pantalla dentro
 * del manejador de errores.
 */
function deError(cuerpo: unknown, trazaId: string): CuerpoDeError {
  const c = cuerpo as Partial<CuerpoDeError> | null
  return {
    codigo: typeof c?.codigo === 'string' ? c.codigo : 'AP-DESCONOCIDO',
    mensaje: typeof c?.mensaje === 'string' ? c.mensaje : '',
    trazaId: typeof c?.trazaId === 'string' ? c.trazaId : trazaId,
  }
}

async function refrescar(): Promise<boolean> {
  try {
    const respuesta = await fetch(`${BASE}/sesion/refrescar`, {
      method: 'POST',
      headers: { 'x-request-id': nuevoIdentificador() },
      credentials: 'include',
    })
    if (!respuesta.ok) return false
    const nueva = (await respuesta.json()) as { acceso?: string }
    if (!nueva.acceso) return false
    guardarAcceso(nueva.acceso)
    return true
  } catch {
    return false
  }
}
