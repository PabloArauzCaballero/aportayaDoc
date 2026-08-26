import { StyleSheet, Text, View } from 'react-native'
import type { SaldoBilletera } from 'clientes/typescript/nucleo-financiero/models'
import { Monto } from '../atomos/Monto'
import { andamiaje } from '../tokens/andamiaje'

/**
 * Lo que se ve cuando la consulta salio bien. No hace red ni decide estados: los
 * recibe resueltos. Es lo que permite probarlo sin levantar nada.
 */
export function ResumenDeBilletera({ saldo }: { saldo: SaldoBilletera }) {
  return (
    <View style={estilos.tarjeta}>
      <Text accessibilityRole="header" style={estilos.rotulo}>
        Disponible
      </Text>
      <Monto valor={saldo.disponible} tamano="monto" etiqueta="Saldo disponible" />

      <View style={estilos.fila}>
        <Text style={estilos.rotulo}>Retenido</Text>
        <Monto valor={saldo.retenido} etiqueta="Saldo retenido" />
      </View>

      {/* De cuando es el dato. Sin esto, un saldo cacheado en el subte se lee
          como el saldo de ahora. */}
      <Text style={estilos.pie}>Al corte de {saldo.alCorteDe}</Text>
    </View>
  )
}

const estilos = StyleSheet.create({
  tarjeta: {
    borderColor: andamiaje.color.borde,
    borderRadius: andamiaje.radio.md,
    borderWidth: 1,
    gap: andamiaje.espacio.sm,
    padding: andamiaje.espacio.lg,
  },
  fila: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  rotulo: { color: andamiaje.color.textoSuave, fontSize: andamiaje.tipografia.cuerpo },
  pie: { color: andamiaje.color.textoSuave, fontSize: andamiaje.tipografia.pie },
})
