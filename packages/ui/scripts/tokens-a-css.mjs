/**
 * Emite `generado/tokens.css` desde `src/tokens/`.
 *
 * Las propiedades personalizadas de CSS **no se escriben a mano**: si el archivo TS y
 * el CSS fueran dos fuentes, divergirían en la segunda semana y los tres productos no
 * se verían igual. Acá hay una sola fuente y el CSS es su salida.
 *
 * El archivo emitido no se versiona: lo produce `yarn workspace @aportaya/ui build`,
 * que corre antes de las apps por el grafo de `turbo.json`.
 */
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { registerHooks } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// Node resuelve ESM con la extension escrita; el resto del repositorio importa sin
// ella, porque los empaquetadores (Vite, Metro) y `moduleResolution: bundler` la
// completan. En vez de escribir `.ts` en el codigo solo para que este script corra
// -y dejar dos estilos de import en el mismo paquete-, el script completa la
// extension igual que el empaquetador. Node 22.18+ quita los tipos por su cuenta.
registerHooks({
  resolve(especificador, contexto, siguiente) {
    if (especificador.startsWith('.') && !/\.[cm]?[jt]s$/.test(especificador)) {
      const candidato = new URL(`${especificador}.ts`, contexto.parentURL)
      if (existsSync(candidato)) return { url: candidato.href, shortCircuit: true }
    }
    return siguiente(especificador, contexto)
  },
})

const { areaTactil, espacio, naranja, neutro, radio, tipografia, verde } = await import('../src/tokens/paleta.ts')
const { claro, oscuro } = await import('../src/tokens/temas.ts')

const raiz = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/**
 * `brandTexto` → `--brand-texto`, `surface2` → `--surface-2`. Un nombre en TS, su
 * sintaxis en CSS, cero listas paralelas que alguien tenga que mantener a mano.
 */
const aVariable = (nombre) =>
  `--${nombre.replace(/[A-Z]/g, (l) => `-${l.toLowerCase()}`).replace(/([a-z])(\d)/g, '$1-$2')}`

const lineas = (objeto, sufijo = '') =>
  Object.entries(objeto).map(([clave, valor]) => `  ${aVariable(clave)}: ${valor}${sufijo};`)

const primitivas = [
  '  /* Paleta — los tonos crudos. Un componente no los pide: pide un rol. */',
  ...lineas(verde),
  ...lineas(naranja),
  ...lineas(neutro),
  // Los semanticos NO se emiten aca: los roles de tema ya publican `--ok`, `--ok-bg`
  // y sus hermanos, y declarar el mismo nombre dos veces invita a que un dia tengan
  // dos valores.
  '',
  '  /* Escalas */',
  ...lineas(espacio, 'px'),
  ...Object.entries(radio).map(([clave, valor]) => `  --r-${clave}: ${valor}px;`),
  `  --area-tactil: ${areaTactil}px;`,
  `  --font-d: ${tipografia.display};`,
  `  --font-b: ${tipografia.cuerpo};`,
  `  --mono: ${tipografia.mono};`,
]

const roles = (tema) => lineas(tema)

const css = `/* GENERADO por scripts/tokens-a-css.mjs — no editar a mano. */

:root {
${primitivas.join('\n')}

  /* Roles del tema claro. El oscuro redefine estos y ningún componente. */
${roles(claro).join('\n')}
}

/* El tema del sistema, salvo que se haya elegido claro a mano. */
@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) {
${roles(oscuro).map((l) => `  ${l}`).join('\n')}
  }
}

:root[data-theme='light'] {
${roles(claro).join('\n')}
}

:root[data-theme='dark'] {
${roles(oscuro).join('\n')}
}
`

mkdirSync(resolve(raiz, 'generado'), { recursive: true })
writeFileSync(resolve(raiz, 'generado/tokens.css'), css, 'utf8')
process.stdout.write(`generado/tokens.css · ${css.split('\n').length} lineas\n`)
