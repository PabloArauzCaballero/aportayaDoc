/**
 * `@aportaya/ui` — el sistema de diseño (fase F1).
 *
 * Este punto de entrada es **universal**: solo tokens y lógica pura, sin un solo
 * import de React, de `react-native` ni del DOM. Por eso lo pueden consumir los tres
 * productos y también el backend de pruebas.
 *
 * Los componentes se piden por plataforma:
 * - `@aportaya/ui/web` → `apps/backoffice` y las islas de `apps/web`
 * - `@aportaya/ui/nativo` → `apps/movil`
 */
export * from './tokens'
export * from './dinero'
