/**
 * Los valores literales del sistema de diseño. **Este archivo y `temas.ts` son los
 * dos únicos lugares del frontend donde puede aparecer un hex, un `px` o una
 * familia tipográfica** (invariante 3 de `planes/10 Plan maestro del frontend`).
 *
 * **No se diseña acá.** Cada valor está copiado de
 * `docs/Views/Sistema-Diseno/estilos.css`, que es la fuente de verdad de la bóveda.
 * `pruebas/unidad/tokens-contra-boveda.spec.ts` lee ese CSS y falla si alguno
 * diverge: un color inventado en F1 se propaga a los tres productos y ya no se saca.
 */

/** Verde Pasanaku — la marca. */
export const verde = {
  g900: '#0C2C1D',
  g800: '#123A26',
  g700: '#164A30',
  g600: '#1C5A3A',
  g500: '#237349',
  g400: '#3C9366',
  g300: '#7CBE9C',
  g200: '#BCDFCC',
  g100: '#E7F2EB',
} as const

/** Naranja Aporte — el acento y la llamada a la acción. */
export const naranja = {
  o700: '#BC6217',
  o600: '#D6741C',
  o500: '#E5852B',
  o400: '#EF9E4E',
  o300: '#F6BE85',
  o200: '#FBDBB8',
  o100: '#FDF0DF',
} as const

/** Neutros con sesgo verde, a propósito: un gris puro al lado de la marca se ve sucio. */
export const neutro = {
  ink: '#10231A',
  slate: '#38473F',
  muted: '#6C7B72',
  line: '#DCE4DE',
  cloud: '#F3F6F2',
  crema: '#F6F4EC',
  white: '#FFFFFF',
} as const

/**
 * Semánticos, **separados del acento**. El naranja es la llamada a la acción; el
 * verde de éxito es otra cosa. Mezclarlos hace que un botón parezca una confirmación.
 */
export const semantico = {
  ok: '#1F9D57',
  okbg: '#E7F5EC',
  warn: '#F0B429',
  warnbg: '#FEF4DA',
  err: '#D64545',
  errbg: '#FBECEC',
  info: '#2E7FB8',
  infobg: '#E7F1F8',
} as const

/**
 * Escala de espaciado, en px. Un valor fuera de esta escala es un valor que alguien
 * eligió mirando la pantalla, y a la tercera pantalla ya no hay ritmo vertical.
 */
export const espacio = { s1: 4, s2: 8, s3: 12, s4: 16, s5: 24, s6: 32, s7: 48 } as const

export const radio = { sm: 8, md: 12, lg: 16, xl: 24, pill: 999 } as const

export const tipografia = {
  /** Display: titulares, cifras y etiquetas de control. */
  display: '"Poppins","Sora",system-ui,"Segoe UI",Roboto,Arial,sans-serif',
  /** Cuerpo: todo lo que se lee en párrafo. */
  cuerpo: '"Inter",system-ui,"Segoe UI",Roboto,Arial,sans-serif',
  mono: 'ui-monospace,"SF Mono",Menlo,Consolas,monospace',
} as const

/**
 * Área táctil mínima, en px. 44 no es un número redondo: por debajo de eso la app
 * deja afuera a quien no tiene pulso firme, que en una billetera es mucha gente.
 */
export const areaTactil = 44
