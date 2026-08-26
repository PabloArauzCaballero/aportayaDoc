import type { APIRoute } from 'astro'
import { robotsTxt } from '../seo/robots'

// Se calcula UNA vez al construir. Un `robots.txt` servido por el proceso obliga a
// tener el servidor arriba para responderle a un rastreador, y a pagar el arranque
// en frio en la peticion que decide si el sitio se indexa.
export const prerender = true

/** Estático: se calcula una vez al construir, no en cada visita de un rastreador. */
export const GET: APIRoute = ({ site }) =>
  new Response(robotsTxt(site?.toString() ?? 'https://aportaya.bo'), {
    headers: { 'Content-Type': 'text/plain; charset=utf-8' },
  })
