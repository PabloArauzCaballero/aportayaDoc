import { naranja, neutro, semantico, verde } from './paleta'

/**
 * Los **roles**: lo que un componente pide. Un componente nunca pide `--g600`, pide
 * `brand`. Por eso el tema oscuro **redefine solo tokens y ningún componente**
 * (`planes/11` F1.1): si un componente conociera un tono, el tema tendría que
 * conocer el componente.
 */
export type RolesDeTema = {
  bg: string
  surface: string
  surface2: string
  text: string
  text2: string
  /** Neutro que **sí** pasa AA como texto chico. `muted` da 4.05:1 y no alcanza. */
  text3: string
  border: string
  brand: string
  brandInk: string
  /** El verde **como texto**. Separado de `brand` porque en oscuro `brand` se aclara. */
  brandTexto: string
  accent: string
  accentInk: string
  /** El naranja **como texto**: `accent` sobre crema da 2.47:1 y no pasa AA. */
  accentTexto: string
  field: string
  fieldBorder: string
  /**
   * Relleno sólido fijo, **no se redefine por tema**: es el verde que sostiene texto
   * blanco encima. En oscuro `brand` se aclara para servir como texto y deja de
   * sostener blanco (3.59:1), así que este es un token aparte.
   */
  verdeSolido: string
  sobreVerdeSolido: string
  /**
   * El rojo de la acción destructiva, **oscurecido y fijo**. Blanco sobre `--err`
   * (`#D64545`) da 4.38:1 y no pasa AA; así da 4.61:1. La bóveda lo resuelve igual en
   * `.btn-danger`, con el mismo comentario. Es un par fijo por la misma razón que el
   * verde sólido: sostiene texto blanco, y un tono que se aclara por tema deja de
   * sostenerlo.
   */
  rojoSolido: string
  sobreRojoSolido: string
  ok: string
  okBg: string
  okTexto: string
  warn: string
  warnBg: string
  avisoTexto: string
  err: string
  errBg: string
  errTexto: string
  info: string
  infoBg: string
  infoTexto: string
  sombra1: string
  sombra2: string
  sombra3: string
}

const solido = {
  verdeSolido: '#1C5A3A',
  sobreVerdeSolido: '#F4FBF6',
  rojoSolido: '#D43E3E',
  sobreRojoSolido: '#FFFFFF',
} as const

export const claro: RolesDeTema = {
  bg: neutro.crema,
  surface: neutro.white,
  surface2: neutro.cloud,
  text: neutro.ink,
  text2: neutro.slate,
  text3: '#647169', // 4.64:1 sobre crema
  border: neutro.line,
  brand: verde.g600,
  brandInk: verde.g800,
  brandTexto: verde.g600, // 8.16:1 sobre blanco
  accent: naranja.o500,
  accentInk: '#3A1E02',
  accentTexto: '#A65B14', // 4.62:1 sobre crema
  field: neutro.white,
  fieldBorder: '#C9D4CD',
  ...solido,
  ok: semantico.ok,
  okBg: semantico.okbg,
  okTexto: '#197C45', // 4.66:1 sobre okBg
  warn: semantico.warn,
  warnBg: semantico.warnbg,
  avisoTexto: '#AA5915', // 4.62:1 sobre warnBg
  err: semantico.err,
  errBg: semantico.errbg,
  errTexto: '#C13A3A',
  info: semantico.info,
  infoBg: semantico.infobg,
  infoTexto: '#256C9E',
  sombra1: '0 1px 2px rgba(16,35,26,.06)',
  sombra2: '0 4px 16px rgba(16,35,26,.08)',
  sombra3: '0 18px 44px rgba(16,35,26,.16)',
}

export const oscuro: RolesDeTema = {
  bg: '#0A1F15',
  surface: '#0F2B1D',
  surface2: '#0C2418',
  text: '#EAF3ED',
  text2: '#B7CCC0',
  text3: '#89998E',
  border: '#1C3A2A',
  brand: verde.g400,
  brandInk: '#EAF3ED',
  brandTexto: '#419F6E',
  accent: naranja.o400,
  accentInk: '#3A1E02',
  accentTexto: naranja.o400,
  field: '#0C2418',
  fieldBorder: '#2A4A38',
  ...solido,
  ok: semantico.ok,
  okBg: 'rgba(31,157,87,.14)',
  okTexto: verde.g300,
  warn: semantico.warn,
  warnBg: 'rgba(240,180,41,.14)',
  avisoTexto: semantico.warn,
  err: semantico.err,
  errBg: 'rgba(214,69,69,.14)',
  errTexto: '#F08C8C',
  info: semantico.info,
  infoBg: 'rgba(46,127,184,.14)',
  infoTexto: '#8FC2E4',
  sombra1: '0 1px 2px rgba(0,0,0,.3)',
  sombra2: '0 6px 20px rgba(0,0,0,.32)',
  sombra3: '0 20px 46px rgba(0,0,0,.4)',
}

export const temas = { claro, oscuro } as const
export type NombreDeTema = keyof typeof temas
