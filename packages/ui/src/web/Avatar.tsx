import type { ReactNode } from 'react'

/**
 * La persona, en dos letras.
 *
 * **El color se deriva del nombre, no se elige.** Así la misma persona sale siempre
 * del mismo color en las tres apps, sin que nadie mantenga una tabla; y los tonos
 * salen de la paleta de marca, que es lo único de donde pueden salir.
 *
 * El nombre completo va en `title` y en el texto accesible: dos iniciales no
 * identifican a nadie, y en una lista de participantes eso importa.
 */
const TONOS = ['verdeSolido', 'g500', 'accent'] as const

export type TamanoDeAvatar = 24 | 30 | 40 | 56

export function Avatar({ nombre, tamano = 40 }: { nombre: string; tamano?: TamanoDeAvatar }) {
  const iniciales = nombre
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((parte) => parte[0] ?? '')
    .join('')
    .toUpperCase()
  // Suma de códigos: determinista, estable y sin dependencias. No es un hash
  // criptográfico y no tiene por qué serlo — acá solo elige un color.
  const tono = TONOS[[...nombre].reduce((suma, letra) => suma + letra.charCodeAt(0), 0) % TONOS.length]
  return (
    <span className={`ay-avatar ay-avatar--${tamano} ay-avatar--${tono}`} title={nombre}>
      <span aria-hidden="true">{iniciales}</span>
      <span className="ay-solo-lectores">{nombre}</span>
    </span>
  )
}

/** Varios superpuestos. El resto se dice con número, no se recorta en silencio. */
export function GrupoDeAvatares({ children, restantes }: { children: ReactNode; restantes?: number }) {
  return (
    <span className="ay-avatares">
      {children}
      {restantes && restantes > 0 ? <span className="ay-avatares__mas">+{restantes}</span> : null}
    </span>
  )
}
