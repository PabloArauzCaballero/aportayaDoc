/**
 * El hueco mientras llega el dato.
 *
 * **Nunca sobre un importe.** Un rectángulo gris donde va a aparecer un saldo se lee
 * como «ya casi», y si la petición falla la persona se queda mirando una promesa. El
 * esqueleto es para listas y textos; para dinero, o está el número o está el error.
 *
 * `aria-hidden`: es decoración pura, y anunciar «cargando» seis veces —una por línea—
 * no ayuda a nadie. El contenedor ya lo dice una vez con `Girador`.
 */
export function Esqueleto({ ancho = '100%', alto = 16 }: { ancho?: string | number; alto?: string | number }) {
  return <span className="ay-esqueleto" style={{ width: ancho, height: alto }} aria-hidden="true" />
}
