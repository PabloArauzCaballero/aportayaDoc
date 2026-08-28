import type { ImporteDelContrato } from '../dinero/formatear'
import { formatearMonto, montoParaLectura } from '../dinero/formatear'

/**
 * El átomo más importante del sistema: **el único lugar del proyecto donde se dibuja
 * un importe** (invariante 5).
 *
 * No recalcula nada. El importe llega del servidor como cadena decimal y se presenta;
 * un cliente que redondea o suma produce un número que no es el del libro contable.
 *
 * Cifras tabulares y alineado a la derecha: en una columna de dinero, comparar dos
 * importes tiene que ser mirar, no leer.
 */
export type PropiedadesDeMonto = {
  valor: ImporteDelContrato
  /** Qué importe es. Sin esto, un lector de pantalla anuncia una cifra sin concepto. */
  etiqueta?: string
  tamano?: 'cuerpo' | 'destacado' | 'titular'
  /** Marca el signo con color además del `-`. El color nunca va solo: el signo queda. */
  sentido?: 'entra' | 'sale' | 'neutro'
}

export function Monto({ valor, etiqueta, tamano = 'cuerpo', sentido = 'neutro' }: PropiedadesDeMonto) {
  const texto = formatearMonto(valor)
  return (
    <span
      className={`ay-monto ay-monto--${tamano} ay-monto--${sentido}`}
      aria-label={etiqueta ? montoParaLectura(valor, etiqueta) : undefined}
    >
      {texto}
    </span>
  )
}
