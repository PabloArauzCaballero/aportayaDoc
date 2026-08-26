import { Link, Stack } from 'expo-router'
import { StyleSheet, Text, View } from 'react-native'
import { andamiaje } from '../src/tokens/andamiaje'

/** Punto de entrada del andamiaje. Las pantallas de negocio llegan en F3. */
export default function Inicio() {
  return (
    <View style={estilos.pantalla}>
      <Stack.Screen options={{ title: 'AportaYa' }} />
      <Text accessibilityRole="header" style={estilos.titulo}>
        AportaYa
      </Text>
      <Link href="/billetera/11111111-1111-4111-8111-111111111111" style={estilos.enlace}>
        Ver mi saldo
      </Link>
    </View>
  )
}

const estilos = StyleSheet.create({
  pantalla: {
    flex: 1,
    gap: andamiaje.espacio.md,
    justifyContent: 'center',
    padding: andamiaje.espacio.lg,
  },
  titulo: { color: andamiaje.color.texto, fontSize: andamiaje.tipografia.titulo, fontWeight: '700' },
  enlace: {
    color: andamiaje.color.acento,
    fontSize: andamiaje.tipografia.cuerpo,
    lineHeight: andamiaje.areaTactil,
  },
})
