import { Link, Stack } from 'expo-router'
import { StyleSheet, Text, View } from 'react-native'
import { tokens } from '../src/tokens'

/** Punto de entrada de la app. Las pantallas de negocio llegan en F3. */
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
    gap: tokens.espacio.md,
    justifyContent: 'center',
    padding: tokens.espacio.lg,
  },
  titulo: { color: tokens.color.texto, fontSize: tokens.tipografia.titulo, fontWeight: '700' },
  enlace: {
    color: tokens.color.acento,
    fontSize: tokens.tipografia.cuerpo,
    lineHeight: tokens.areaTactil,
  },
})
