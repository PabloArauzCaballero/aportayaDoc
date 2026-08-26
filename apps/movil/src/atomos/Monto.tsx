import { StyleSheet, Text } from 'react-native'
import type { Dinero } from 'clientes/typescript/nucleo-financiero/models'
import { andamiaje } from '../tokens/andamiaje'

/**
 * El unico lugar donde se formatea un importe.
 *
 * **No recalcula nada.** El monto llega del servidor como cadena decimal y se
 * muestra tal cual; un cliente que redondea o suma produce un numero que no es
 * el del libro contable, y esa diferencia siempre aparece en el peor momento
 * (`dinero-decimal`).
 */
export type PropiedadesDeMonto = {
  valor: Dinero
  tamano?: 'cuerpo' | 'monto'
  etiqueta?: string
}

export function Monto({ valor, tamano = 'cuerpo', etiqueta }: PropiedadesDeMonto) {
  const texto = `${valor.moneda} ${valor.monto}`
  return (
    <Text
      accessibilityLabel={etiqueta ? `${etiqueta}: ${texto}` : texto}
      style={[estilos.base, tamano === 'monto' && estilos.grande]}
    >
      {texto}
    </Text>
  )
}

const estilos = StyleSheet.create({
  base: {
    color: andamiaje.color.texto,
    fontSize: andamiaje.tipografia.cuerpo,
    fontVariant: ['tabular-nums'],
  },
  grande: {
    fontSize: andamiaje.tipografia.monto,
    fontWeight: '700',
  },
})
