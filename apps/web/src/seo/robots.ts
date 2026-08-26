/**
 * Política de rastreadores — **búsqueda sí, entrenamiento no** (ADR-042).
 *
 * Es decisión de negocio, no técnica: queremos que la gente encuentre el sitio y no
 * queremos que el contenido alimente modelos sin acuerdo. Por eso los agentes de
 * búsqueda pasan y los de entrenamiento no.
 *
 * `robots.txt` es una convención que se respeta por buena fe: no protege nada. Lo que
 * de verdad impide leer un expediente es la sesión, y por eso `/verificar/` y
 * `/publico/` van bloqueados **acá y además** con `noindex` en la propia página.
 */
export const BUSCADORES_PERMITIDOS = ['Googlebot', 'Bingbot', 'DuckDuckBot', 'OAI-SearchBot', 'ClaudeBot', 'PerplexityBot']

export const ENTRENAMIENTO_BLOQUEADO = ['GPTBot', 'Google-Extended', 'Applebot-Extended', 'CCBot', 'Bytespider', 'Meta-ExternalAgent']

/** Nunca indexables: llevan datos de personas, aunque sean públicas. */
export const RUTAS_NO_INDEXABLES = ['/verificar/', '/publico/']

export function robotsTxt(sitio: string): string {
  const lineas: string[] = [
    '# Generado desde src/seo/robots.ts — no se edita a mano (ADR-042).',
    '# Búsqueda sí, entrenamiento no.',
    '',
  ]
  for (const agente of BUSCADORES_PERMITIDOS) {
    lineas.push(`User-agent: ${agente}`, 'Allow: /')
    for (const ruta of RUTAS_NO_INDEXABLES) lineas.push(`Disallow: ${ruta}`)
    lineas.push('')
  }
  for (const agente of ENTRENAMIENTO_BLOQUEADO) {
    lineas.push(`User-agent: ${agente}`, 'Disallow: /', '')
  }
  lineas.push('User-agent: *', 'Allow: /')
  for (const ruta of RUTAS_NO_INDEXABLES) lineas.push(`Disallow: ${ruta}`)
  lineas.push('', `Sitemap: ${sitio.replace(/\/$/, '')}/sitemap.xml`, '')
  return lineas.join('\n')
}
