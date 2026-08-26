import type { Dinero } from 'clientes/typescript/nucleo-financiero/models'

/**
 * El unico lugar donde se formatea un importe.
 *
 * **No recalcula nada.** El monto llega del servidor como cadena decimal y se
 * muestra tal cual; un cliente que redondea o suma produce un numero que no es el
 * del libro contable, y esa diferencia siempre aparece en el peor momento.
 *
 * Alineado a la derecha y con cifras tabulares: en una columna de dinero, comparar
 * dos importes tiene que ser mirar, no leer.
 */
export function Monto({ valor, etiqueta, grande }: { valor: Dinero; etiqueta?: string; grande?: boolean }) {
  const texto = `${valor.moneda} ${valor.monto}`
  return (
    <span
      aria-label={etiqueta ? `${etiqueta}: ${texto}` : undefined}
      style={{
        fontVariantNumeric: 'tabular-nums',
        fontSize: grande ? 'var(--tipo-monto)' : 'inherit',
        fontWeight: grande ? 700 : 500,
        textAlign: 'right',
        display: 'inline-block',
      }}
    >
      {texto}
    </span>
  )
}
