/**
 * Átomos para React Native: los consume `apps/movil`.
 *
 * Los de DOM viven en `@aportaya/ui/web`. Ver `README.md` §«Por qué dos
 * renderizadores»: lo que se comparte de verdad —tokens y formateo de dinero— está en
 * `@aportaya/ui` y lo importan los dos.
 */
export { Monto } from './Monto'
export type { PropiedadesDeMonto } from './Monto'
export { Boton } from './Boton'
export type { PropiedadesDeBoton } from './Boton'
export { temaDe, escala } from './tema'
