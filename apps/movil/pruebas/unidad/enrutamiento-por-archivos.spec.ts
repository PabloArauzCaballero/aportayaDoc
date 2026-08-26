import { readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { join, relative, sep } from 'node:path'

/**
 * **La prueba que importa de la fase F0** (planes/18, gate de `F0-M`):
 * agregar una pantalla vacia no puede requerir editar ningun registro
 * compartido. Si hubiera un `routes.tsx` central, cada carril de pantalla
 * chocaria con los otros en cada PR — el conflicto n.º 1, vivo otra vez.
 *
 * No se prueba leyendo la documentacion de Expo Router: se prueba agregando el
 * archivo y comprobando que la ruta existe **y que nada mas cambio**.
 */
const APP = join(__dirname, '..', '..', 'app')
const SRC = join(__dirname, '..', '..', 'src')

function archivosDe(carpeta: string): string[] {
  return readdirSync(carpeta).flatMap((entrada) => {
    const completo = join(carpeta, entrada)
    return statSync(completo).isDirectory() ? archivosDe(completo) : [completo]
  })
}

/** La regla de Expo Router: la ruta es la ruta del archivo, sin extension. */
function rutaDe(archivo: string): string {
  const relativa = relative(APP, archivo).split(sep).join('/').replace(/\.tsx?$/, '')
  if (relativa === 'index') return '/'
  return `/${relativa.replace(/\/index$/, '').replace(/\[(\w+)\]/g, ':$1')}`
}

function huella(): Map<string, string> {
  const todos = [...archivosDe(APP), ...archivosDe(SRC)]
  return new Map(todos.map((a) => [a, readFileSync(a, 'utf8')]))
}

describe('enrutamiento por sistema de archivos', () => {
  it('las rutas salen de los archivos de app/, no de una lista', () => {
    const rutas = archivosDe(APP)
      .filter((a) => a.endsWith('.tsx') && !a.endsWith('_layout.tsx'))
      .map(rutaDe)
    expect(rutas).toContain('/')
    expect(rutas).toContain('/billetera/:cuentaId')
  })

  it('agregar una pantalla vacia no cambia ningun otro archivo', () => {
    const antes = huella()
    const nueva = join(APP, 'pantalla-de-andamiaje.tsx')

    writeFileSync(nueva, 'export default function Andamiaje() {\n  return null\n}\n', 'utf8')
    try {
      const rutas = archivosDe(APP)
        .filter((a) => a.endsWith('.tsx') && !a.endsWith('_layout.tsx'))
        .map(rutaDe)
      expect(rutas).toContain('/pantalla-de-andamiaje')

      const despues = huella()
      for (const [archivo, contenido] of antes) {
        expect(despues.get(archivo)).toBe(contenido)
      }
    } finally {
      rmSync(nueva, { force: true })
    }
  })

  it('no existe ningun registro central de rutas que editar', () => {
    const sospechosos = [...archivosDe(APP), ...archivosDe(SRC)].filter((a) =>
      /(^|[\/])(routes|rutas)\.(ts|tsx)$/.test(a),
    )
    expect(sospechosos).toEqual([])
  })
})
