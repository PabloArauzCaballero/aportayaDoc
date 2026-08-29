/**
 * En qué paso del recorrido estamos: los puntos del onboarding.
 *
 * El punto activo se alarga además de cambiar de color — quien no distingue el tono
 * ve igual cuál es. Y el texto dice «Paso 2 de 4», porque cuatro puntos idénticos no
 * se cuentan al oído.
 */
export function Puntos({ total, actual }: { total: number; actual: number }) {
  return (
    <span className="ay-puntos" role="status">
      {Array.from({ length: total }, (_, indice) => (
        <span key={indice} className={indice === actual ? 'ay-puntos__on' : undefined} aria-hidden="true" />
      ))}
      <span className="ay-solo-lectores">{`Paso ${actual + 1} de ${total}`}</span>
    </span>
  )
}
