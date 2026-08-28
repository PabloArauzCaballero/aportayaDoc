/**
 * Átomos para DOM: los consumen `apps/backoffice` y las islas React de `apps/web`.
 *
 * Los de `apps/movil` viven en `@aportaya/ui/nativo`. Son dos renderizadores del
 * mismo contrato, y **lo que comparten —los tokens y el formateo de dinero— vive una
 * sola vez** en `@aportaya/ui`. Ver `README.md` §«Por qué dos renderizadores».
 */
export { Monto } from './Monto'
export type { PropiedadesDeMonto } from './Monto'
export { Boton } from './Boton'
export type { PropiedadesDeBoton } from './Boton'
export { ChipEstado } from './ChipEstado'
export type { TonoDeEstado } from './ChipEstado'
