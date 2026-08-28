# `@aportaya/ui` — el sistema de diseño

Fase **F1**. Lo consumen los tres productos: `apps/movil`, `apps/backoffice` y las
islas de `apps/web`.

## Acá no se diseña

Cada valor de este paquete está copiado de `docs/Views/Sistema-Diseno/estilos.css`, que
es la fuente de verdad de la bóveda. `pruebas/unidad/tokens-contra-boveda.spec.ts` lee
ese CSS y **falla si alguno diverge** — se comprobó cambiando un dígito de `--g600`, y
la prueba lo señaló junto con el rol `--brand` que lo consume.

Es la protección contra el modo de falla propio de esta fase: un color inventado se
propaga a los tres productos y ya no se saca.

## Los tres puntos de entrada

| Import | Qué trae | Quién lo usa |
| --- | --- | --- |
| `@aportaya/ui` | Tokens y formateo de dinero. **Universal**: ni un import de React, de `react-native` ni del DOM | los tres, y cualquier prueba |
| `@aportaya/ui/web` | Átomos para DOM + `web/atomos.css` | `apps/backoffice`, islas de `apps/web` |
| `@aportaya/ui/nativo` | Átomos para React Native | `apps/movil` |
| `@aportaya/ui/tokens.css` | Las propiedades personalizadas, **emitidas** desde `src/tokens/` | `apps/backoffice`, `apps/web` |

### Por qué dos renderizadores y no uno

La app es React Native y el backoffice es React DOM. Compartir el componente entero
pide `react-native-web`, que mete el runtime de React Native en el paquete del sitio
público — justo lo que `F0-W` cuidó al dejar el simulado afuera de producción — y
además choca con ADR-004: «compartir componentes de dominio sí, compartir layout no».

Así que **se comparte lo que de verdad es uno solo**: los tokens y el formateo de
dinero. Los renderizadores son dos, delgados, con **el mismo contrato de propiedades**.
Si mañana cambia el formato de un importe, cambia en un archivo y los tres productos lo
ven.

## `Monto` — el átomo más importante

Es el **único lugar del proyecto donde se dibuja un importe** (invariante 5). Antes de
F1 había dos implementaciones, una en cada app, y ninguna aplicaba el formato de la
maqueta.

El contrato define el importe como **cadena** (`^-?\d+\.\d{2}$`) y no como número,
porque «un `number` JSON es un doble». `formatearMonto` respeta eso: **no convierte a
número en ningún punto**, separa enteros de centavos, agrupa los miles y vuelve a unir.
La prueba de propiedad lo verifica sobre 5.000 importes deterministas de hasta 19
dígitos: formatear y deshacer devuelve exactamente la cadena que entró.

## `generado/tokens.css` es un artefacto

Lo emite `scripts/tokens-a-css.mjs` desde `src/tokens/`, y **no se versiona**: dos
fuentes de verdad divergen, y la divergencia se descubre en producción. Lo produce
`yarn workspace @aportaya/ui build`, que corre antes de las apps por el grafo de
`turbo.json`, y los tres `yarn dev:*` de la raíz lo llaman explícitamente.

## Qué falta

Este paquete es el **piso** de F1, no F1 cerrada. Ver
`planes/informes/carril-F1.md` §«Lo que queda abierto».
