/**
 * La sesion del operador, en memoria y solo en memoria.
 *
 * Ni `localStorage` ni `sessionStorage`: un token en almacenamiento del navegador lo
 * lee cualquier script que llegue a la pagina. El refresco viaja en cookie
 * `HttpOnly`, que el navegador manda solo y el JavaScript no puede leer
 * ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]).
 */
let acceso: string | null = null
const escuchas = new Set<() => void>()

export function tokenDeAcceso(): string | null {
  return acceso
}

export function guardarAcceso(nuevo: string): void {
  acceso = nuevo
}

export function cerrarSesion(): void {
  acceso = null
  escuchas.forEach((avisar) => avisar())
}

export function alCerrarSesion(escucha: () => void): () => void {
  escuchas.add(escucha)
  return () => escuchas.delete(escucha)
}
