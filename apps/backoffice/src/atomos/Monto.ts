/**
 * El `Monto` del backoffice es el `Monto` del sistema de diseño. No hay una segunda
 * implementación: invariante 5 dice que **hay un solo lugar donde se formatea un
 * importe**, y hasta que F1 llegó había dos (una acá y otra en `apps/movil`).
 *
 * Se reexporta en vez de importar `@aportaya/ui/web` desde cada organismo para que
 * las pantallas ya escritas no cambien de import en el mismo commit que trae el
 * sistema. Los organismos nuevos importan de `@aportaya/ui/web` directamente.
 */
export { Monto } from '@aportaya/ui/web'
export type { PropiedadesDeMonto } from '@aportaya/ui/web'
