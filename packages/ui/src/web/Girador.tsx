/**
 * Que algo está pasando.
 *
 * **Lleva texto.** Un círculo que gira sin decir qué espera es, para un lector de
 * pantalla, silencio. `role="status"` lo anuncia sin robar el foco, que es lo que
 * corresponde: la persona no pidió ir a ninguna parte, pidió esperar.
 *
 * Con `prefers-reduced-motion` deja de girar y el texto queda. La información no
 * estaba en el movimiento.
 */
export function Girador({ etiqueta = 'Cargando…' }: { etiqueta?: string }) {
  return (
    <span className="ay-girador" role="status">
      <span className="ay-girador__rueda" aria-hidden="true" />
      <span className="ay-solo-lectores">{etiqueta}</span>
    </span>
  )
}
