import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native'
import { andamiaje } from '../tokens/andamiaje'

/**
 * Cargando, vacio y error, escritos una vez.
 *
 * `movil-expo` los exige en **toda** pantalla con datos. Escritos una vez, una
 * pantalla nueva no puede olvidarse de ninguno: se le nota en el tipo.
 */
export function Cargando({ que }: { que: string }) {
  return (
    <View accessibilityRole="progressbar" accessibilityLabel={`Cargando ${que}`} style={estilos.centro}>
      <ActivityIndicator color={andamiaje.color.acento} />
      <Text style={estilos.suave}>Cargando {que}…</Text>
    </View>
  )
}

export function Vacio({ titulo, explicacion }: { titulo: string; explicacion: string }) {
  return (
    <View style={estilos.centro}>
      <Text accessibilityRole="header" style={estilos.titulo}>
        {titulo}
      </Text>
      {/* El vacio dice POR QUE no hay nada y que se puede hacer. Un vacio mudo
          se lee como una falla de la app. */}
      <Text style={estilos.suave}>{explicacion}</Text>
    </View>
  )
}

export function Fallo({
  mensaje,
  trazaId,
  alReintentar,
}: {
  mensaje: string
  trazaId?: string
  alReintentar: () => void
}) {
  return (
    <View style={estilos.centro}>
      <Text accessibilityRole="alert" style={estilos.error}>
        {mensaje}
      </Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Volver a intentar"
        onPress={alReintentar}
        style={estilos.boton}
      >
        <Text style={estilos.textoBoton}>Volver a intentar</Text>
      </Pressable>
      {/* La traza no es decoracion: es el hilo con el que soporte encuentra esta
          peticion exacta en el log del backend. */}
      {trazaId ? <Text style={estilos.pie}>Código de seguimiento: {trazaId}</Text> : null}
    </View>
  )
}

const estilos = StyleSheet.create({
  centro: { alignItems: 'center', gap: andamiaje.espacio.sm, padding: andamiaje.espacio.lg },
  titulo: { color: andamiaje.color.texto, fontSize: andamiaje.tipografia.titulo, fontWeight: '600' },
  suave: { color: andamiaje.color.textoSuave, fontSize: andamiaje.tipografia.cuerpo, textAlign: 'center' },
  error: { color: andamiaje.color.error, fontSize: andamiaje.tipografia.cuerpo, textAlign: 'center' },
  pie: { color: andamiaje.color.textoSuave, fontSize: andamiaje.tipografia.pie },
  boton: {
    alignItems: 'center',
    backgroundColor: andamiaje.color.acento,
    borderRadius: andamiaje.radio.md,
    justifyContent: 'center',
    minHeight: andamiaje.areaTactil,
    paddingHorizontal: andamiaje.espacio.lg,
  },
  textoBoton: { color: andamiaje.color.fondo, fontSize: andamiaje.tipografia.cuerpo, fontWeight: '600' },
})
