import { StyleSheet, Text } from 'react-native'
import type { ImporteDelContrato } from '../dinero/formatear'
import { formatearMonto, montoParaLectura } from '../dinero/formatear'
import type { RolesDeTema } from '../tokens/temas'
import { claro } from '../tokens/temas'

/**
 * `Monto`, en React Native. **Mismo contrato de propiedades y mismo formateo** que la
 * versión DOM: las dos llaman a `formatearMonto`, que es el único lugar del proyecto
 * donde se arma la cadena de un importe (invariante 5).
 *
 * Lo único distinto es el renderizador. Si mañana el formato cambia, cambia una vez.
 */
export type PropiedadesDeMonto = {
  valor: ImporteDelContrato
  etiqueta?: string
  tamano?: 'cuerpo' | 'destacado' | 'titular'
  sentido?: 'entra' | 'sale' | 'neutro'
  /** El tema resuelto. Lo entrega el `ProveedorTema` de F2; por omisión, claro. */
  tema?: RolesDeTema
}

export function Monto({
  valor,
  etiqueta,
  tamano = 'cuerpo',
  sentido = 'neutro',
  tema = claro,
}: PropiedadesDeMonto) {
  const texto = formatearMonto(valor)
  const color = sentido === 'entra' ? tema.okTexto : sentido === 'sale' ? tema.errTexto : tema.text
  return (
    <Text accessibilityLabel={montoParaLectura(valor, etiqueta)} style={[estilos.base, estilos[tamano], { color }]}>
      {texto}
    </Text>
  )
}

const estilos = StyleSheet.create({
  // `tabular-nums` es lo que hace que dos importes se comparen mirando, no leyendo.
  base: { fontVariant: ['tabular-nums'] },
  cuerpo: { fontSize: 16, fontWeight: '500' },
  destacado: { fontSize: 28, fontWeight: '700' },
  titular: { fontSize: 34, fontWeight: '700' },
})
