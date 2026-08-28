import type { ReactNode } from 'react'
import { ActivityIndicator, Pressable, StyleSheet, Text } from 'react-native'
import { areaTactil, espacio, radio } from '../tokens/paleta'
import type { RolesDeTema } from '../tokens/temas'
import { claro } from '../tokens/temas'

/**
 * `Boton`, en React Native. Mismo contrato que la versión DOM.
 *
 * **`cargando` deshabilita** (invariante 6): mientras la petición viaja, el botón no
 * acepta un segundo toque. En una billetera, el doble envío no es una molestia: es un
 * cobro repetido.
 */
export type PropiedadesDeBoton = {
  variante?: 'primario' | 'secundario' | 'fantasma' | 'peligro'
  tamano?: 'sm' | 'base' | 'lg'
  cargando?: boolean
  deshabilitado?: boolean
  bloque?: boolean
  onPress?: () => void
  tema?: RolesDeTema
  children: ReactNode
}

export function Boton({
  variante = 'primario',
  tamano = 'base',
  cargando = false,
  deshabilitado = false,
  bloque = false,
  onPress,
  tema = claro,
  children,
}: PropiedadesDeBoton) {
  const inactivo = deshabilitado || cargando
  const fondo = {
    primario: tema.accent,
    secundario: tema.verdeSolido,
    fantasma: 'transparent',
    peligro: '#D43E3E',
  }[variante]
  const tinta = {
    primario: tema.accentInk,
    secundario: tema.sobreVerdeSolido,
    fantasma: tema.brandTexto,
    peligro: '#FFFFFF',
  }[variante]
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: inactivo, busy: cargando }}
      disabled={inactivo}
      onPress={onPress}
      style={({ pressed }) => [
        estilos.contenedor,
        estilos[tamano],
        { backgroundColor: fondo, opacity: inactivo ? 0.5 : pressed ? 0.85 : 1 },
        variante === 'fantasma' && { borderWidth: 1, borderColor: tema.fieldBorder },
        bloque && estilos.bloque,
      ]}
    >
      {cargando ? <ActivityIndicator color={tinta} size="small" /> : null}
      <Text style={[estilos.texto, estilos[`texto_${tamano}`], { color: tinta }]}>{children}</Text>
    </Pressable>
  )
}

const estilos = StyleSheet.create({
  contenedor: {
    borderRadius: radio.md,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: espacio.s2,
    // 44 no es un numero redondo: debajo de eso la app deja afuera a quien no tiene
    // pulso firme, que en una billetera es mucha gente.
    minHeight: areaTactil,
  },
  sm: { paddingVertical: espacio.s2, paddingHorizontal: espacio.s3 },
  base: { paddingVertical: espacio.s3, paddingHorizontal: espacio.s4 },
  lg: { paddingVertical: espacio.s4, paddingHorizontal: espacio.s5 },
  bloque: { alignSelf: 'stretch' },
  texto: { fontWeight: '600' },
  texto_sm: { fontSize: 13 },
  texto_base: { fontSize: 15 },
  texto_lg: { fontSize: 16 },
})
