/**
 * ANDAMIAJE — no es el sistema de diseno.
 *
 * Los valores de marca los define F1 en `packages/ui`. Esto existe para que la
 * pantalla de la fase F0 tenga donde tomar un color sin esparcir literales por
 * los componentes, y **se borra al cerrar F1**. Ver `tokens/README.md`.
 */
export const andamiaje = {
  color: {
    fondo: '#FFFFFF',
    texto: '#1A1A1A',
    textoSuave: '#5C5C5C',
    borde: '#E3E3E3',
    acento: '#F26A21',
    error: '#B3261E',
  },
  espacio: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 },
  tipografia: { cuerpo: 16, titulo: 22, monto: 34, pie: 13 },
  radio: { md: 12 },
  // 44dp es el area tactil comoda minima. Debajo de eso la app deja afuera a
  // quien no tiene pulso firme, que en una billetera es mucha gente.
  areaTactil: 44,
} as const
