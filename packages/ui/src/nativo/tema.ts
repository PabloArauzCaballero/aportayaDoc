import { areaTactil, espacio, radio, tipografia } from '../tokens/paleta'
import type { RolesDeTema } from '../tokens/temas'
import { claro, oscuro } from '../tokens/temas'

/**
 * En React Native no hay variables CSS: el tema se resuelve en JavaScript.
 *
 * `useColorScheme()` de React Native devuelve `'light' | 'dark' | null`; el
 * `ProveedorTema` de la fase F2 es quien lo lee y pasa el resultado. Esta función es
 * la traducción, y vive acá para que la app no vuelva a decidir qué significa `null`
 * (significa claro).
 */
export function temaDe(esquema: 'light' | 'dark' | null | undefined): RolesDeTema {
  return esquema === 'dark' ? oscuro : claro
}

/** Lo que no cambia con el tema, junto y a mano para los `StyleSheet` de la app. */
export const escala = { espacio, radio, tipografia, areaTactil } as const
