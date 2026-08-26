import { readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * **La prueba que importa de la fase F0**: agregar una página no puede requerir editar
 * ningún registro compartido. En Astro las rutas son los archivos de `src/pages`, y
 * esta prueba lo comprueba agregando uno — no leyendo la documentación.
 */
const PAGINAS = join(__dirname, '..', '..', 'src', 'pages')
const SRC = join(__dirname, '..', '..', 'src')

function archivosDe(carpeta: string): string[] {
  return readdirSync(carpeta).flatMap((entrada) => {
    const completo = join(carpeta, entrada)
    return statSync(completo).isDirectory() ? archivosDe(completo) : [completo]
  })
}

function rutaDe(archivo: string): string {
  const base = relative(PAGINAS, archivo).split(sep).join('/').replace(/\.(astro|ts)$/, '')
  return base === 'index' ? '/' : `/${base}`
}

describe('rutas por sistema de archivos', () => {
  it('las rutas salen de src/pages, no de una lista', () => {
    const rutas = archivosDe(PAGINAS).map(rutaDe)
    expect(rutas).toContain('/')
    expect(rutas).toContain('/plazos')
  })

  it('agregar una página vacía no cambia ningún otro archivo', () => {
    const huella = () => new Map(archivosDe(SRC).map((a) => [a, readFileSync(a, 'utf8')]))
    const antes = huella()
    const nueva = join(PAGINAS, 'pagina-de-andamiaje.astro')
    writeFileSync(nueva, '<p>andamiaje</p>\n', 'utf8')
    try {
      expect(archivosDe(PAGINAS).map(rutaDe)).toContain('/pagina-de-andamiaje')
      for (const [archivo, contenido] of antes) {
        expect(huella().get(archivo)).toBe(contenido)
      }
    } finally {
      rmSync(nueva, { force: true })
    }
  })

  it('SSR se declara página por página, y hoy solo una lo hace', () => {
    // Estático por omisión no es una preferencia: es la razón por la que este
    // producto existe aparte. Si mañana media docena de páginas declaran
    // `prerender = false`, la decisión de ADR-041 dejó de cumplirse.
    const dinamicas = archivosDe(PAGINAS)
      .filter((a) => a.endsWith('.astro'))
      .filter((a) => /export const prerender\s*=\s*false/.test(readFileSync(a, 'utf8')))
      .map(rutaDe)
    expect(dinamicas).toEqual(['/plazos'])
  })
})
