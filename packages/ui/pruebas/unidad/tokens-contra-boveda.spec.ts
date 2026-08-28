import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { areaTactil, espacio, naranja, neutro, radio, semantico, tipografia, verde } from '../../src/tokens/paleta'
import { claro, oscuro } from '../../src/tokens/temas'

/**
 * **La prueba que impide inventar.**
 *
 * El riesgo de la fase F1 no es escribir un componente flojo: es escribir un color
 * que no existe en la bóveda. Un tono inventado acá se propaga a los tres productos
 * y ya no se saca (`planes/18`, ficha `F1`, «Dónde se rompe»).
 *
 * Así que los tokens no se comparan contra lo que alguien recuerda: se leen de
 * `docs/Views/Sistema-Diseno/estilos.css`, que es la fuente de verdad, y se exige
 * que coincidan valor por valor. Si mañana la bóveda cambia un hex y `tokens.ts` no,
 * esta prueba lo dice.
 */

const CSS_DE_LA_BOVEDA = resolve(__dirname, '../../../../docs/Views/Sistema-Diseno/estilos.css')

/** Lee las declaraciones `--x: valor` de un bloque, por su selector. */
function bloque(css: string, selector: string): Record<string, string> {
  const inicio = css.indexOf(selector)
  if (inicio === -1) throw new Error(`la bóveda ya no declara el bloque ${selector}`)
  const abre = css.indexOf('{', inicio)
  const cierra = css.indexOf('}', abre)
  const declaraciones: Record<string, string> = {}
  for (const linea of css.slice(abre + 1, cierra).split(';')) {
    const corte = linea.indexOf(':')
    if (corte === -1) continue
    const nombre = linea.slice(0, corte).trim()
    if (!nombre.startsWith('--')) continue
    declaraciones[nombre] = linea.slice(corte + 1).trim()
  }
  return declaraciones
}

/** `#FFF` y `#ffffff` son el mismo color; la comparación no puede decir que no. */
function normalizar(valor: string): string {
  const corto = /^#([0-9a-f])([0-9a-f])([0-9a-f])$/i.exec(valor)
  if (corto) return `#${corto[1]}${corto[1]}${corto[2]}${corto[2]}${corto[3]}${corto[3]}`.toLowerCase()
  return /^#[0-9a-f]{6}$/i.test(valor) ? valor.toLowerCase() : valor
}

// Los comentarios se quitan antes de partir: la bóveda documenta cada contraste al
// lado del valor, y un `/* ... */` entre dos declaraciones rompe el troceo por `;`.
const css = readFileSync(CSS_DE_LA_BOVEDA, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')
const raiz = bloque(css, ':root{')
// El tema oscuro hereda de `:root` lo que no redefine — `--accent-ink` y el verde
// sólido, por ejemplo. Resolverlo de otra forma haría fallar la prueba por algo que
// el navegador resuelve solo.
const escuro = { ...raiz, ...bloque(css, ':root[data-theme="dark"]') }

/** Resuelve `var(--x)` dentro del mismo bloque, cuantos saltos haga falta. */
function valorDe(declaraciones: Record<string, string>, nombre: string): string {
  let valor = declaraciones[nombre]
  if (valor === undefined) throw new Error(`la bóveda no declara ${nombre}`)
  for (let salto = 0; salto < 8; salto += 1) {
    const referencia = /^var\((--[a-z0-9-]+)\)$/i.exec(valor)
    if (!referencia) break
    const siguiente = declaraciones[referencia[1]!]
    if (siguiente === undefined) throw new Error(`${nombre} apunta a ${referencia[1]}, que no existe`)
    valor = siguiente
  }
  return normalizar(valor)
}

function comparar(declaraciones: Record<string, string>, pares: Record<string, string | number>, sufijo = '') {
  for (const [variable, mio] of Object.entries(pares)) {
    expect(valorDe(declaraciones, variable), `${variable} divergió de la bóveda`).toBe(
      normalizar(`${mio}${sufijo}`),
    )
  }
}

describe('los tokens salen de la bóveda, no de la memoria de nadie', () => {
  it('la paleta de marca', () => {
    comparar(raiz, Object.fromEntries(Object.entries(verde).map(([k, v]) => [`--${k}`, v])))
    comparar(raiz, Object.fromEntries(Object.entries(naranja).map(([k, v]) => [`--${k}`, v])))
    comparar(raiz, Object.fromEntries(Object.entries(neutro).map(([k, v]) => [`--${k}`, v])))
    comparar(raiz, Object.fromEntries(Object.entries(semantico).map(([k, v]) => [`--${k}`, v])))
  })

  it('las escalas', () => {
    comparar(raiz, Object.fromEntries(Object.entries(espacio).map(([k, v]) => [`--${k}`, v])), 'px')
    comparar(
      raiz,
      Object.fromEntries(Object.entries(radio).map(([k, v]) => [`--r-${k}`, v])),
      'px',
    )
    comparar(raiz, {
      '--font-d': tipografia.display,
      '--font-b': tipografia.cuerpo,
      '--mono': tipografia.mono,
    })
  })

  it('los roles del tema claro', () => {
    comparar(raiz, {
      '--bg': claro.bg,
      '--surface': claro.surface,
      '--surface-2': claro.surface2,
      '--text': claro.text,
      '--text-2': claro.text2,
      '--text-3': claro.text3,
      '--border': claro.border,
      '--brand': claro.brand,
      '--brand-ink': claro.brandInk,
      '--brand-texto': claro.brandTexto,
      '--accent': claro.accent,
      '--accent-ink': claro.accentInk,
      '--accent-texto': claro.accentTexto,
      '--field': claro.field,
      '--field-border': claro.fieldBorder,
      '--verde-solido': claro.verdeSolido,
      '--sobre-verde-solido': claro.sobreVerdeSolido,
      '--okbg': claro.okBg,
      '--ok-texto': claro.okTexto,
      '--warnbg': claro.warnBg,
      '--aviso-texto': claro.avisoTexto,
      '--errbg': claro.errBg,
      '--err-texto': claro.errTexto,
      '--infobg': claro.infoBg,
      '--info-texto': claro.infoTexto,
      '--sh-1': claro.sombra1,
      '--sh-2': claro.sombra2,
      '--sh-3': claro.sombra3,
    })
  })

  it('los roles del tema oscuro', () => {
    comparar(escuro, {
      '--bg': oscuro.bg,
      '--surface': oscuro.surface,
      '--surface-2': oscuro.surface2,
      '--text': oscuro.text,
      '--text-2': oscuro.text2,
      '--text-3': oscuro.text3,
      '--border': oscuro.border,
      '--brand': oscuro.brand,
      '--brand-ink': oscuro.brandInk,
      '--brand-texto': oscuro.brandTexto,
      '--accent': oscuro.accent,
      '--accent-ink': oscuro.accentInk,
      '--accent-texto': oscuro.accentTexto,
      '--field': oscuro.field,
      '--field-border': oscuro.fieldBorder,
      '--verde-solido': oscuro.verdeSolido,
      '--sobre-verde-solido': oscuro.sobreVerdeSolido,
      '--okbg': oscuro.okBg,
      '--ok-texto': oscuro.okTexto,
      '--warnbg': oscuro.warnBg,
      '--aviso-texto': oscuro.avisoTexto,
      '--errbg': oscuro.errBg,
      '--err-texto': oscuro.errTexto,
      '--infobg': oscuro.infoBg,
      '--info-texto': oscuro.infoTexto,
      '--sh-1': oscuro.sombra1,
      '--sh-2': oscuro.sombra2,
      '--sh-3': oscuro.sombra3,
    })
  })

  it('el área táctil mínima no baja de 44', () => {
    // No sale de la bóveda porque el CSS del catálogo no la declara: sale de la
    // pauta de accesibilidad, y es un piso, no un valor a ajustar.
    expect(areaTactil).toBeGreaterThanOrEqual(44)
  })
})
