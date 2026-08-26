// @ts-check
import { defineConfig } from 'astro/config'
import react from '@astrojs/react'
import node from '@astrojs/node'

/**
 * **Estatico por omision, SSR solo donde hace falta** (planes/11 F0.1).
 *
 * No es una preferencia de rendimiento: es la razon por la que el sitio publico
 * existe como tercer producto. Estatico ⇒ menos JavaScript ⇒ mejores metricas de
 * carga ⇒ mejor posicionamiento. Poner el sitio entero en SSR «por las dudas» tira
 * eso a la basura y ademas obliga a tener el servidor arriba para mostrar una
 * pagina que no cambia nunca.
 *
 * Las unicas rutas dinamicas son las de verificacion (CU-61, CU-73, CU-75) y las que
 * consultan un servicio en vivo. Cada una lo declara con `export const prerender = false`.
 */
export default defineConfig({
  output: 'static',
  adapter: node({ mode: 'standalone' }),
  integrations: [react()],
  site: 'https://aportaya.bo',
  build: { format: 'directory' },
  vite: {
    resolve: {
      alias: { clientes: new URL('../../clientes', import.meta.url).pathname },
    },
  },
})
