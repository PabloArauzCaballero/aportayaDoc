---
tags:
  - plan
  - informe
  - carril
titulo: "Carril F1 — sistema de diseño"
ola: F1
fase: F1
modulo: packages/ui
rama: dev
estado: en curso
---

# Carril F1 — sistema de diseño

**Fase** F1 · **Casos de uso** ninguno de negocio · **Puesto** P3 · Legion

> Tramo **T2** de [[17 Plan de acción secuencial · coordinación de cinco máquinas]].
> Ficha en [[18 Fichas de carril · las 38 unidades de trabajo]] · `F1`.
> Posee `packages/ui/**` y `apps/*/src/tokens/**`.

> [!important] Esto es el piso de F1, no F1 cerrada
> Entrega **F1.1 (tokens) completo** y el primer corte de F1.2 (átomos), que es lo que
> los diez carriles siguientes necesitan para no volver a inventar un color. Las
> moléculas, los organismos, las piezas móviles y el catálogo vivo siguen abiertos y
> están listados abajo. **El carril no se congela todavía.**

## Qué está hecho, con la salida que lo prueba

| Entregable | Evidencia | Estado |
| --- | --- | :-: |
| `packages/ui` con tres puntos de entrada: universal, `/web` y `/nativo` | `yarn install` lo resuelve; los tres productos lo declaran como `workspace:*` | ✅ |
| **F1.1 · Tokens completos**, portados de `docs/Views/Sistema-Diseno/estilos.css` | 42 primitivas + 29 roles por tema, claro y oscuro | ✅ |
| **La prueba que impide inventar**: los tokens se comparan contra la bóveda | `tokens-contra-boveda.spec.ts` · 5 pruebas. **Verificada mutando `--g600` a `#1C5A3B`: señaló el token y el rol `--brand` que lo consume** | ✅ |
| `generado/tokens.css` **emitido** desde `src/tokens/`, no escrito a mano | `scripts/tokens-a-css.mjs` → 189 líneas · `:root`, `prefers-color-scheme` y los dos `data-theme` | ✅ |
| **`Monto` deja de estar duplicado** | Había **dos** implementaciones —`apps/movil` y `apps/backoffice`— y ninguna con el formato de la maqueta. Ahora hay una, y las dos apps la reexportan | ✅ |
| El importe se formatea **sin pasar por `Number`** | `Bs 1.240,00` desde la cadena del contrato. **Prueba de propiedad: 5.000 importes deterministas de hasta 19 dígitos, ida y vuelta exacta** | ✅ |
| Átomos DOM: `Monto`, `Boton` (7 estados), `ChipEstado` | 6 pruebas de componente + 1 de accesibilidad | ✅ |
| Átomos nativos: `Monto`, `Boton` | Tipados por `apps/movil`. **Verificado**: se metió una propiedad inexistente en el `StyleSheet` y el `typecheck` de la app la señaló | ✅ |
| **El andamiaje de F0 desaparece** de los tres productos | `apps/movil/src/tokens/andamiaje.ts` y los dos `andamiaje.css` borrados; cero literales de diseño en `apps/` | ✅ |
| `yarn dev:backoffice` y `yarn dev:web`, que el gate de F0 pide y no existían | Agregados a la raíz; construyen el sistema antes de levantar | ✅ |
| El catálogo vivo en `/catalogo` | **No se hizo.** Es F1.6 y necesita las moléculas y los organismos que este corte no trae. Adelantar una ruta vacía no cataloga nada | — |

### Evidencia, con el comando y su resultado

```
yarn build                     4 tareas, 4 exitosas  (ui, simulado, backoffice, web)
yarn lint                      5 tareas, 5 exitosas
yarn test:front                6 tareas, 6 exitosas · 16 (ui) + 21 (movil) + 19 (backoffice) + 15 (web)
yarn test:a11y                 6 tareas, 6 exitosas
yarn workspace @aportaya/ui build          generado/tokens.css · 189 lineas
yarn workspace @aportaya/ui typecheck      sin errores
yarn workspace @aportaya/web typecheck     18 archivos · 0 errores
```

**Pruebas del paquete, por corredor**

| Corredor | Pruebas | Falladas | Qué cubre |
| --- | :-: | :-: | --- |
| `unidad` | 12 | 0 | formateo de dinero (incluida la prueba de propiedad) y los tokens contra la bóveda |
| `componente` | 4 | 0 | `Monto` con su etiqueta, el signo legible, el doble envío bloqueado, `type="button"` |
| `a11y` | 1 | 0 | `jest-axe` sobre los cinco átomos juntos, sin violaciones |

Ninguna prueba desactivada ni saltada.

## Decisiones tomadas, y por qué

| Decisión | Por qué |
| --- | --- |
| **Dos renderizadores, un solo contrato** — `@aportaya/ui/web` y `@aportaya/ui/nativo` | Compartir el componente entero pide `react-native-web`, que mete el runtime de React Native en el paquete del sitio público, justo lo que `F0-W` cuidó al dejar el simulado afuera. Y ADR-004 ya lo dice: «compartir componentes de dominio sí, compartir layout no». Lo que de verdad es uno solo —tokens y formateo de dinero— vive una vez |
| El importe **no pasa por `Number` en ningún punto** | El contrato lo define como cadena y explica por qué: «un `number` JSON es un doble». Convertir para volver a texto reintroduce el error que el contrato evita |
| `formatearMonto` **lanza** ante un importe deforme | Un importe fuera de `^-?\d+\.\d{2}$` significa que la respuesta no encaja en su esquema. Mostrarlo a medias en una billetera es peor que fallar, y la capa de dominio ya valida antes |
| `USD` y no `$us` para el dólar | En una pantalla de dinero un símbolo ambiguo es un riesgo, no un ahorro. `Bs` sí, porque es lo que usa la maqueta y lo que se dice |
| El signo va **delante del prefijo**: `-Bs 80,00` | Es lo primero que se lee. Y el `-` está en el texto, no solo en el color: quien no distingue rojo de verde tiene que leer la dirección igual |
| `--ok-bg` en vez del `--okbg` de la bóveda | Una sola regla de nombres (`camelCase` en TS → guiones en CSS) en vez de una lista de excepciones. La prueba contra la bóveda compara **valores** con el mapeo explícito, así que el rename no afloja la verificación |
| `generado/tokens.css` **no se versiona** | Dos fuentes de verdad divergen y la divergencia aparece en producción. Es la misma regla que ya seguían `src/arbolDeRutas.gen.ts` y `packages/simulado/generado/` |
| Los alias `--color-fondo`, `tokens.color.acento` se mantienen | Sostienen las pantallas que F0 ya escribió sin reescribirlas en el mismo commit que trae los tokens. **Son alias sobre roles reales, no literales**: cada carril los migra al rol y cuando no quede ninguno se borran |
| `cargando` deshabilita el botón, en las dos plataformas | Invariante 6. En una billetera el doble envío no es una molestia: es un cobro repetido. Hay prueba de que el segundo clic no llama al manejador |

## Correcciones al troncal encontradas al ejecutar

Dos defectos que detienen a cualquier máquina con el árbol recién clonado, y que no son
de este carril:

1. **`@aportaya/simulado` no tenía tarea `build`.** Sus contratos en JSON son un
   artefacto ignorado por git, y sin una tarea con ese nombre el `^build` de `turbo`
   nunca los producía: `yarn build` del backoffice moría con cuatro
   `UNRESOLVED_IMPORT`. Ahora `build` corre el mismo generador que `contratos`.
2. **`turbo.json` no declaraba `generado/**` como salida.** Turbo avisaba «no output
   files found» y no cacheaba nada de lo emitido.

Y una tercera, menor: **`apps/web` declaraba `typecheck` pero le faltaba
`@astrojs/check`**, así que el comando abría un diálogo interactivo en vez de
verificar. Agregado: 18 archivos, 0 errores.

## Supuestos declarados

Regla cero: ninguno silencioso.

1. **Este corte no cierra F1.** La ficha pide tokens, átomos, moléculas, organismos,
   piezas móviles y el catálogo vivo. Entrego F1.1 completo y el primer corte de F1.2.
   **El carril no se congela**, y los carriles de pantalla que dependen de F1 congelado
   (`F2`, `F6`) todavía no pueden arrancar.
2. **El tema oscuro está en los tokens y verificado contra la bóveda, pero ninguna app
   lo enciende todavía.** En web queda listo por `prefers-color-scheme`; en la app
   `temaDe()` espera al `ProveedorTema` de F2, que no es de este carril. Fijarlo acá
   sería inventar un proveedor ajeno.
3. **El contraste AA está tomado de la bóveda, no vuelto a medir.** Los derivados de
   texto (`--text-3`, `--accent-texto`, `--ok-texto`…) traen su ratio anotado en
   `estilos.css` y se copiaron con él. Medir pieza por pieza, en los dos temas, es el
   gate de F1 completo y necesita el catálogo.
4. **`prefers-reduced-motion` está respetado en el CSS de los átomos.** En React Native
   todavía no: eso pide `AccessibilityInfo.isReduceMotionEnabled`, y la única animación
   nativa hoy es el `ActivityIndicator` del botón.

## Huecos encontrados, no completados con una suposición

| Hueco | Dónde | Por qué importa |
| --- | --- | --- |
| **`clientes/typescript` no existe en un árbol recién clonado y no se puede generar sin Java** | `apps/movil` y `apps/backoffice` | Es un artefacto de `generateOpenApiClients` (Gradle), ignorado por git. **Esta máquina no tiene JVM**, así que `yarn typecheck` de esas dos apps queda en rojo con 8 errores, **todos** por ese módulo ausente —los dos `TS7006` incluidos, que son el `any` implícito que deja el tipo faltante—. **No es de este carril y no se tapó**: escribir esos tipos a mano viola el invariante 2. `packages/ui` y `apps/web`, que no lo importan, están en verde |
| El catálogo `/catalogo` con contraste medido pieza por pieza | F1.6 | Es el gate de F1 y la base de las pruebas visuales. Necesita las moléculas y los organismos que faltan |

## Lo que queda abierto, y de quién es

| Qué | De quién | Cuándo |
| --- | --- | --- |
| El resto de F1.2: campos, selección, indicadores | **este carril** | T2 |
| F1.3 moléculas · F1.4 organismos (`TablaDeDatos` incluida) | **este carril** | T2 |
| F1.5 piezas móviles: tab bar, bottom sheet, teclado numérico, PIN/OTP | **este carril** | T2 |
| F1.6 catálogo vivo en `/catalogo`, `noindex`, con captura visual en ambos temas | **este carril** | T2 |
| Las nueve piezas que suma [[20 Maqueta de referencia · deltas del frontend]] | **este carril** | T2, antes de que los carriles compongan |
| `ProveedorTema` que encienda el oscuro en la app | **`F2`** | T3 |
| Migrar los alias `--color-*` al rol correspondiente | cada carril de pantalla | al tocar cada pantalla |
| Generar `clientes/typescript` en una máquina con JVM | **P1** | antes de cerrar el tramo |

## Frases prohibidas sin evidencia

No se dice «está listo». Lo que hay es: **21 pruebas en verde en tres corredores del
paquete, una prueba que se verificó fallando cuando se altera un solo dígito de un
color, un formateo de dinero comprobado sobre 5.000 importes de hasta 19 dígitos sin
tocar un `Number`, dos implementaciones duplicadas de `Monto` reducidas a una, el
andamiaje de F0 borrado de los tres productos, y `build`, `lint`, `test:front` y
`test:a11y` en verde en los cinco espacios de trabajo.**

## Ver también

[[11 Fases F0 y F1 · Cimientos y sistema de diseño]] · [[16 Carriles de frontend]] ·
[[10 Plan maestro del frontend]] · [[carril-F0-M]] · [[carril-F0-B]] · [[carril-F0-W]]
