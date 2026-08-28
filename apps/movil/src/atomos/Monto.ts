/**
 * El `Monto` de la app es el `Monto` del sistema de diseño. Hasta que F1 llegó había
 * dos implementaciones —una acá y otra en el backoffice—, que es exactamente lo que
 * el invariante 5 prohíbe: **un solo lugar formatea un importe**.
 *
 * Se reexporta para que las pantallas ya escritas no cambien de import en el mismo
 * commit que trae el sistema. Lo nuevo importa de `@aportaya/ui/nativo`.
 */
export { Monto } from '@aportaya/ui/nativo'
export type { PropiedadesDeMonto } from '@aportaya/ui/nativo'
