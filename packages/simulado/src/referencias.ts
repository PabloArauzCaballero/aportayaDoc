import type { Contrato, Esquema, Respuesta } from './tipos'

/**
 * Resuelve un `$ref` local del documento. Solo locales: un contrato que apunta a
 * otro archivo no se puede simular sin salir a buscarlo, y salir a buscarlo
 * convierte al servidor simulado en un cliente de red.
 */
export function resolver<T extends Esquema | Respuesta>(documento: Contrato, nodo: T): T {
  const visitados = new Set<string>()
  let actual: T = nodo
  while (actual?.$ref) {
    const ref = actual.$ref
    if (!ref.startsWith('#/')) {
      throw new Error(`referencia externa no soportada: ${ref}`)
    }
    if (visitados.has(ref)) {
      throw new Error(`referencia circular: ${ref}`)
    }
    visitados.add(ref)
    const destino = ref
      .slice(2)
      .split('/')
      .reduce<unknown>(
        (nivel, tramo) => (nivel as Record<string, unknown> | undefined)?.[descodificar(tramo)],
        documento as unknown,
      )
    if (destino === undefined) {
      throw new Error(`referencia rota: ${ref}`)
    }
    actual = destino as T
  }
  return actual
}

// Un `/` o un `~` dentro de un nombre de propiedad viaja escapado en un JSON Pointer.
function descodificar(tramo: string): string {
  return tramo.replace(/~1/g, '/').replace(/~0/g, '~')
}
