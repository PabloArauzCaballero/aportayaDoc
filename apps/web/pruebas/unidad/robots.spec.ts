import { describe, expect, it } from 'vitest'
import { BUSCADORES_PERMITIDOS, ENTRENAMIENTO_BLOQUEADO, RUTAS_NO_INDEXABLES, robotsTxt } from '../../src/seo/robots'

/**
 * La política de rastreadores es **decisión de negocio** (ADR-042), y por eso tiene
 * prueba: un cambio silencioso acá abre el contenido al entrenamiento sin que nadie
 * lo decida.
 */
describe('robots.txt · búsqueda sí, entrenamiento no', () => {
  const texto = robotsTxt('https://aportaya.bo')

  it('los agentes de búsqueda pasan', () => {
    for (const agente of BUSCADORES_PERMITIDOS) {
      expect(texto).toContain(`User-agent: ${agente}`)
    }
    expect(texto).toContain('Allow: /')
  })

  it('los agentes de entrenamiento no pasan, y ninguno queda con Allow', () => {
    for (const agente of ENTRENAMIENTO_BLOQUEADO) {
      const bloque = texto.split(`User-agent: ${agente}`)[1]?.split('User-agent:')[0] ?? ''
      expect(bloque).toContain('Disallow: /')
      expect(bloque).not.toContain('Allow: /')
    }
  })

  it('las rutas con datos de personas quedan fuera para todos', () => {
    const paraTodos = texto.split('User-agent: *')[1] ?? ''
    for (const ruta of RUTAS_NO_INDEXABLES) {
      expect(paraTodos).toContain(`Disallow: ${ruta}`)
    }
  })

  it('un agente de entrenamiento nuevo no se cuela por el comodín', () => {
    // `User-agent: *` permite todo salvo lo no indexable, a propósito: bloquear el
    // comodín sacaría el sitio de los buscadores. Lo que protege de un rastreador de
    // entrenamiento nuevo es agregarlo a la lista, no el comodín — y por eso la lista
    // se revisa, no se da por completa.
    expect(ENTRENAMIENTO_BLOQUEADO.length).toBeGreaterThanOrEqual(4)
    expect(texto).toContain('Sitemap: https://aportaya.bo/sitemap.xml')
  })
})
