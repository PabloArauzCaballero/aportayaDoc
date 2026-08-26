import RandExp from 'randexp'
import { resolver } from './referencias'
import type { Contrato, Esquema } from './tipos'

/**
 * Cuanto llena la muestra.
 *
 * - `representativo`: una respuesta con contenido — es el exito.
 * - `minimo`: lo mas vacio que el contrato permite — listas en su minimo y
 *   numeros en cero. Es el estado **vacio**, que NO es un error: una billetera
 *   recien abierta responde 200 con el saldo en cero.
 */
export type Modo = 'representativo' | 'minimo'

/**
 * Construye un valor que **cumple** el esquema de salida del contrato.
 *
 * Un mock escrito a mano diverge del contrato en la segunda semana y deja la
 * pantalla verde mientras ya esta rota (planes/11 F0.3). Por eso la respuesta
 * simulada no se escribe: se deriva del esquema, y `CU01.contrato.spec.ts`
 * comprueba que lo cumple — tambien en el modo `minimo`.
 *
 * Es **determinista**: la misma entrada da siempre la misma salida. Un simulado
 * que cambia entre corridas convierte una prueba en un sorteo.
 */
export function muestraDe(
  documento: Contrato,
  esquema: Esquema,
  sendero = 'raiz',
  modo: Modo = 'representativo',
): unknown {
  const e = resolver(documento, esquema)

  if (e.const !== undefined) return e.const
  if (e.example !== undefined) return e.example
  if (Array.isArray(e.enum) && e.enum.length > 0) return e.enum[0]

  if (e.allOf && e.allOf.length > 0) {
    return Object.assign({}, ...e.allOf.map((p) => muestraDe(documento, p, sendero, modo)))
  }
  const alternativa = (e.oneOf ?? e.anyOf)?.[0]
  if (alternativa) return muestraDe(documento, alternativa, sendero, modo)

  switch (tipoDe(e)) {
    case 'object':
      return objeto(documento, e, sendero, modo)
    case 'array':
      return lista(documento, e, sendero, modo)
    case 'integer':
      return Math.trunc(e.minimum ?? (modo === 'minimo' ? 0 : 1))
    case 'number':
      return e.minimum ?? (modo === 'minimo' ? 0 : 1)
    case 'boolean':
      return modo !== 'minimo'
    case 'null':
      return null
    default:
      return cadena(e, sendero, modo)
  }
}

function tipoDe(e: Esquema): string {
  const t = Array.isArray(e.type) ? e.type.find((x) => x !== 'null') : e.type
  if (t) return t
  return e.properties ? 'object' : e.items ? 'array' : 'string'
}

function objeto(documento: Contrato, e: Esquema, sendero: string, modo: Modo): Record<string, unknown> {
  // Se emiten TODAS las propiedades declaradas, no solo las requeridas: una
  // pantalla que solo ve lo obligatorio no descubre que rompe con lo opcional.
  const salida: Record<string, unknown> = {}
  for (const [nombre, sub] of Object.entries(e.properties ?? {})) {
    salida[nombre] = muestraDe(documento, sub, `${sendero}.${nombre}`, modo)
  }
  for (const obligatoria of e.required ?? []) {
    if (!(obligatoria in salida)) {
      salida[obligatoria] = muestraDe(documento, {}, `${sendero}.${obligatoria}`, modo)
    }
  }
  return salida
}

function lista(documento: Contrato, e: Esquema, sendero: string, modo: Modo): unknown[] {
  // El minimo del contrato manda tambien en el modo vacio: una lista con
  // `minItems: 1` vacia no es una respuesta vacia, es una respuesta invalida.
  const cuantos = modo === 'minimo' ? (e.minItems ?? 0) : Math.max(e.minItems ?? 1, 1)
  const item = e.items ?? {}
  return Array.from({ length: cuantos }, (_, i) => muestraDe(documento, item, `${sendero}[${i}]`, modo))
}

function cadena(e: Esquema, sendero: string, modo: Modo): string {
  if (e.pattern) return desdePatron(e.pattern, modo)
  switch (e.format) {
    case 'uuid':
      return uuidDe(sendero)
    case 'date':
      return '2026-01-15'
    case 'date-time':
      return '2026-01-15T10:30:00Z'
    case 'email':
      return 'persona@ejemplo.bo'
    case 'uri':
      return 'https://ejemplo.bo/recurso'
    default:
      return rellenar(ultimoTramo(sendero), e)
  }
}

/**
 * randexp genera un valor que cumple el patron; estas dos lineas lo vuelven
 * determinista. Sin ellas la misma prueba pasaria o fallaria por sorteo.
 *
 * - `max = 0` deja las repeticiones abiertas (`+`, `*`) en su minimo: sin esto,
 *   `\d+` puede salir con cien digitos.
 * - `randInt` fijo elige siempre el mismo caracter del rango: el minimo da
 *   ceros —el modo vacio— y el punto medio da un valor con contenido.
 */
function desdePatron(patron: string, modo: Modo): string {
  const generador = new RandExp(patron)
  generador.max = 0
  generador.randInt =
    modo === 'minimo' ? (desde: number) => desde : (desde: number, hasta: number) => desde + ((hasta - desde) >> 1)
  return generador.gen()
}

function rellenar(base: string, e: Esquema): string {
  const minimo = e.minLength ?? 0
  const maximo = e.maxLength ?? Math.max(minimo, base.length)
  let texto = base.length > 0 ? base : 'valor'
  while (texto.length < minimo) texto = `${texto}-${base || 'valor'}`
  return texto.slice(0, Math.max(maximo, minimo))
}

function ultimoTramo(sendero: string): string {
  const tramos = sendero.split('.')
  return tramos[tramos.length - 1] ?? 'valor'
}

/**
 * UUID valido derivado del sendero: el mismo campo devuelve siempre el mismo
 * identificador, y dos campos distintos devuelven identificadores distintos —
 * que es lo que permite que una pantalla los distinga.
 */
export function uuidDe(semilla: string): string {
  let h1 = 0x811c9dc5
  let h2 = 0x01000193
  for (let i = 0; i < semilla.length; i++) {
    h1 = Math.imul(h1 ^ semilla.charCodeAt(i), 0x01000193) >>> 0
    h2 = Math.imul(h2 + semilla.charCodeAt(i), 0x85ebca6b) >>> 0
  }
  const hex = (n: number) => n.toString(16).padStart(8, '0')
  const crudo = `${hex(h1)}${hex(h2)}${hex(h1 ^ h2)}${hex((h1 + h2) >>> 0)}`
  // Version 4 y variante 8: un UUID que no lo sea seria rechazado por el
  // validador de formato del contrato.
  return [
    crudo.slice(0, 8),
    crudo.slice(8, 12),
    `4${crudo.slice(13, 16)}`,
    `8${crudo.slice(17, 20)}`,
    crudo.slice(20, 32),
  ].join('-')
}
