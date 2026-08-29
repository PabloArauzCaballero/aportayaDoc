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

// -------------------------------------------------------------------- campos --
export { Campo } from './Campo'
export type { EstadoDeCampo, PropiedadesDeCampo } from './Campo'
export { CampoMonto } from './CampoMonto'
export type { PropiedadesDeCampoMonto } from './CampoMonto'
export { AreaDeTexto } from './AreaDeTexto'
export type { PropiedadesDeAreaDeTexto } from './AreaDeTexto'
export { Seleccion } from './Seleccion'
export type { PropiedadesDeSeleccion } from './Seleccion'
export { Paso } from './Paso'
export type { PropiedadesDePaso } from './Paso'
export { CampoContrasena } from './CampoContrasena'
export { CampoOTP } from './CampoOTP'
export type { PropiedadesDeCampoOTP } from './CampoOTP'

// ----------------------------------------------------------------- selección --
export { Casilla, Opcion } from './Casilla'
export { Interruptor } from './Interruptor'
export type { PropiedadesDeInterruptor } from './Interruptor'
export { SelectorSegmentado } from './SelectorSegmentado'
export type { OpcionSegmentada, PropiedadesDeSelectorSegmentado } from './SelectorSegmentado'
export { Chip } from './Chip'
export type { PropiedadesDeChip } from './Chip'

// --------------------------------------------------------------- indicadores --
export { Avatar, GrupoDeAvatares } from './Avatar'
export type { TamanoDeAvatar } from './Avatar'
export { Girador } from './Girador'
export { AnilloDeProgreso, BarraDeProgreso } from './Progreso'
export type { PropiedadesDeProgreso } from './Progreso'
export { Esqueleto } from './Esqueleto'
export { Tooltip } from './Tooltip'
export { Puntos } from './Puntos'
