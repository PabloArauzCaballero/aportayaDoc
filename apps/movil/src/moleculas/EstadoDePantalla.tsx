import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native'
import { tokens } from '../tokens'

/**
 * Cargando, vacio y error, escritos una vez.
 *
 * `movil-expo` los exige en **toda** pantalla con datos. Escritos una vez, una
 * pantalla nueva no puede olvidarse de ninguno: se le nota en el tipo.
 */
export function Cargando({ que }: { que: string }) {
  return (
    <View accessibilityRole="progressbar" accessibilityLabel={`Cargando ${que}`} style={estilos.centro}>
      <ActivityIndicator color={tokens.color.acento} />
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
  centro: { alignItems: 'center', gap: tokens.espacio.sm, padding: tokens.espacio.lg },
  titulo: { color: tokens.color.texto, fontSize: tokens.tipografia.titulo, fontWeight: '600' },
  suave: { color: tokens.color.textoSuave, fontSize: tokens.tipografia.cuerpo, textAlign: 'center' },
  error: { color: tokens.color.error, fontSize: tokens.tipografia.cuerpo, textAlign: 'center' },
  pie: { color: tokens.color.textoSuave, fontSize: tokens.tipografia.pie },
  boton: {
    alignItems: 'center',
    backgroundColor: tokens.color.acento,
    borderRadius: tokens.radio.md,
    justifyContent: 'center',
    minHeight: tokens.areaTactil,
    paddingHorizontal: tokens.espacio.lg,
  },
  textoBoton: { color: tokens.color.fondo, fontSize: tokens.tipografia.cuerpo, fontWeight: '600' },
})
