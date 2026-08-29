import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * **El barrido que el lint no puede hacer.**
 *
 * `no-restricted-syntax` mira TypeScript. El CSS no lo mira nadie, y `atomos.css` es
 * exactamente donde es más cómodo escribir un `#d43e3e` «solo por esta vez» — de
 * hecho ahí estaba uno cuando se escribió esta prueba.
 *
 * El gate de salida de F1 dice «cero hex fuera de `tokens.ts`». Esto lo verifica sobre
 * el paquete entero, en cualquier extensión, y falla nombrando el archivo y la línea.
 */
const RAIZ = resolve(__dirname, '../../src')
const PERMITIDOS = ['tokens']
const HEX = /#[0-9a-fA-F]{3,8}\b/g

function archivos(directorio: string): string[] {
  return readdirSync(directorio).flatMap((entrada) => {
    const ruta = join(directorio, entrada)
    if (statSync(ruta).isDirectory()) {
      return PERMITIDOS.includes(entrada) ? [] : archivos(ruta)
    }
    return [ruta]
  })
}

describe('cero literales de diseño fuera de los tokens', () => {
  it('ningún hex vive en un componente ni en su CSS', () => {
    const hallazgos: string[] = []
    for (const ruta of archivos(RAIZ)) {
      readFileSync(ruta, 'utf8')
        .split('\n')
        .forEach((linea, indice) => {
          // El comentario que explica *por qué* un token vale lo que vale puede
          // nombrar el hex; lo que no puede es usarlo.
          const sinComentario = linea.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/, '')
          for (const hex of sinComentario.match(HEX) ?? []) {
            hallazgos.push(`${relative(RAIZ, ruta)}:${indice + 1} → ${hex}`)
          }
        })
    }
    expect(hallazgos, 'un color fuera de tokens/ se propaga a los tres productos').toEqual([])
  })
})
