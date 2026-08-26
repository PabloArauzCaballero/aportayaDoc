import { readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * **La prueba que importa de la fase F0** (planes/18, gate de `F0-B`): agregar una
 * pantalla vacía no puede requerir editar ningún registro compartido. Si hubiera un
 * `rutas.tsx` central, cada carril de pantalla chocaría con los otros en cada PR.
 *
 * No se prueba leyendo la documentación de TanStack Router: se prueba agregando el
 * archivo y comprobando que la ruta existe **y que nada más cambió**.
 */
const RUTAS = join(__dirname, '..', '..', 'src', 'rutas')
const SRC = join(__dirname, '..', '..', 'src')

function archivosDe(carpeta: string): string[] {
  return readdirSync(carpeta).flatMap((entrada) => {
    const completo = join(carpeta, entrada)
    return statSync(completo).isDirectory() ? archivosDe(completo) : [completo]
  })
}

/** La regla de TanStack Router: el nombre del archivo con puntos es la ruta. */
function rutaDe(archivo: string): string {
  const base = relative(RUTAS, archivo).split(sep).join('/').replace(/\.tsx?$/, '')
  if (base === 'index') return '/'
  return `/${base.replace(/\./g, '/').replace(/\$(\w+)/g, ':$1')}`
}

function rutasDeclaradas(): string[] {
  return archivosDe(RUTAS)
    .filter((a) => a.endsWith('.tsx') && !a.endsWith('__root.tsx') && !a.endsWith('.gen.ts'))
    .map(rutaDe)
}

function huella(): Map<string, string> {
  return new Map(
    archivosDe(SRC)
      .filter((a) => !a.endsWith('.gen.ts'))
      .map((a) => [a, readFileSync(a, 'utf8')]),
  )
}

describe('enrutamiento por sistema de archivos', () => {
  it('las rutas salen de los archivos de src/rutas, no de una lista', () => {
    const rutas = rutasDeclaradas()
    expect(rutas).toContain('/')
    expect(rutas).toContain('/billetera/:cuentaId')
  })

  it('agregar una pantalla vacía no cambia ningún otro archivo escrito a mano', () => {
    const antes = huella()
    const nueva = join(RUTAS, 'pantalla-de-andamiaje.tsx')
    writeFileSync(
      nueva,
      "import { createFileRoute } from '@tanstack/react-router'\n" +
        "export const Route = createFileRoute('/pantalla-de-andamiaje')({ component: () => null })\n",
      'utf8',
    )
    try {
      expect(rutasDeclaradas()).toContain('/pantalla-de-andamiaje')
      for (const [archivo, contenido] of antes) {
        expect(huella().get(archivo)).toBe(contenido)
      }
    } finally {
      rmSync(nueva, { force: true })
    }
  })

  it('no existe ningún registro central de rutas escrito a mano', () => {
    // `arbolDeRutas.gen.ts` sí existe, pero es GENERADO por el plugin y está
    // ignorado por el linter: nadie lo edita y por eso nadie choca en él.
    const sospechosos = archivosDe(SRC).filter((a) => /(^|[\\/])(routes|rutas)\.(ts|tsx)$/.test(a))
    expect(sospechosos).toEqual([])
  })
})
