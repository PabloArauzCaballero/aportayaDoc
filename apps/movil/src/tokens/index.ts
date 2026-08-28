import { escala, temaDe } from '@aportaya/ui/nativo'

/**
 * Los tokens de la app, derivados del sistema de diseño. **Ni un literal acá.**
 *
 * Reemplaza al `andamiaje.ts` de la fase F0, que tenía seis colores inventados para
 * que la primera pantalla se viera. Ahora cada valor sale de `@aportaya/ui`, que sale
 * de `docs/Views/Sistema-Diseno/estilos.css` y tiene una prueba que falla si diverge.
 *
 * **Sigue siendo el tema claro fijo.** El cambio claro/oscuro en vivo lo resuelve el
 * `ProveedorTema` de la fase F2 con `useColorScheme()`; `temaDe()` ya está listo para
 * recibirlo. Fijarlo acá sería inventar un proveedor que no es de este carril.
 */
const tema = temaDe('light')

export const tokens = {
  color: {
    fondo: tema.bg,
    superficie: tema.surface,
    texto: tema.text,
    textoSuave: tema.text3,
    borde: tema.border,
    acento: tema.accent,
    sobreAcento: tema.accentInk,
    error: tema.errTexto,
  },
  espacio: {
    xs: escala.espacio.s1,
    sm: escala.espacio.s2,
    md: escala.espacio.s4,
    lg: escala.espacio.s5,
    xl: escala.espacio.s6,
  },
  tipografia: { cuerpo: 16, titulo: 22, monto: 34, pie: 13 },
  radio: { md: escala.radio.md },
  areaTactil: escala.areaTactil,
} as const

export { tema }
