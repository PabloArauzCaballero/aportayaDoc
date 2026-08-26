---
tags:
  - plan
  - informe
  - carril
titulo: "Carril F0-M — andamiaje móvil"
ola: F0
fase: F0
modulo: apps/movil
rama: pablo/feature/carril-F0-M-andamiaje-movil
estado: en curso
---

# Carril F0-M — andamiaje móvil

**Fase** F0 · **Casos de uso** ninguno de negocio (este carril construye el piso del
frontend) · **Máquina** Legion

> Tramo **T1** de [[17 Plan de acción secuencial · coordinación de cinco máquinas]] §5.
> Ficha en [[18 Fichas de carril · las 38 unidades de trabajo]] · `F0-M`.
> Posee `apps/movil/**` y el andamiaje de MSW. `clientes/typescript` es generado y no
> tiene dueño de carril.

## Qué está hecho, con la salida que lo prueba

| Entregable | Evidencia | Estado |
| --- | --- | :-: |
| Monorepo yarn 4 con espacios de trabajo, `tsconfig.base.json` y alias `@aportaya/*` | `yarn install` resuelve 1.132 paquetes; `yarn typecheck` en verde en los dos paquetes | ✅ |
| `turbo.json` — orquestador de `build`, `lint`, `typecheck`, `test:front`, `test:a11y` | `yarn lint` → 2 tareas, 2 exitosas | ✅ |
| Expo SDK 54 con **Expo Router** (enrutamiento por archivos) | `npx expo export --platform android` → **1.285 módulos**, Hermes de 3,81 MB | ✅ |
| Servidor simulado **derivado de los contratos**, compartido: `packages/simulado` | 21 pruebas en verde, entre ellas la de contrato de CU-01 | ✅ |
| Capa de dominio sobre el cliente generado, con traza, idempotencia y refresco | `useSaldo` y `useRegistro`; `llamar()` es la única salida a la red | ✅ |
| Una pantalla real contra MSW con sus **cuatro estados** | `PantallaDeSaldo` · 5 pruebas: cargando, éxito, vacío, error y sin red | ✅ |
| Herramientas de calidad: tres corredores de Jest + ESLint plano | `front-unit` · `front-componente` · `front-a11y` | ✅ |
| **La prueba del gate**: agregar una pantalla vacía no toca ningún registro compartido | `enrutamiento-por-archivos.spec.ts`, 3 pruebas | ✅ |
| `yarn --cwd apps/movil start` abierto en Expo Go | **no ejecutado**: no hay dispositivo ni emulador acelerado en esta máquina | 🟡 |
| ADR-037 y ADR-038 | **no son de este carril**: los escribe `F0-W` (planes/11 F0.5) | — |

### Evidencia, con el comando y su resultado

```
yarn install                                 Done with warnings (1.132 paquetes)
yarn workspace @aportaya/simulado contratos  identidad · notificaciones · nucleo-financiero
yarn lint                                    2 tareas, 2 exitosas
yarn typecheck                               2 tareas, 2 exitosas
yarn test:front                              5 suites · 21 pruebas · 0 falladas
yarn test:a11y                               1 suite · 2 pruebas · 0 falladas
npx expo export --platform android           1.285 modulos · Hermes 3,81 MB
python3 scripts/verificar_seguridad.py       sin hallazgos nuevos en apps/ ni packages/
```

**Pruebas por corredor**

| Corredor | Pruebas | Falladas | Qué cubre |
| --- | :-: | :-: | --- |
| `front-unit` | 14 | 0 | generador de muestras, enrutamiento por archivos, contrato de CU-01 |
| `front-componente` | 7 | 0 | los cuatro estados de la pantalla · clave de idempotencia reutilizada |
| `front-a11y` | 2 | 0 | el importe se anuncia con su concepto · encabezado marcado como encabezado |

Ninguna prueba desactivada ni saltada.

## Decisiones tomadas, y por qué

| Decisión | Por qué | Dónde |
| --- | --- | --- |
| `nodeLinker: node-modules` en yarn 4 | Metro resuelve recorriendo `node_modules`; con Plug'n'Play no hay árbol que recorrer y la app no arranca | `.yarnrc.yml` |
| El simulado vive en `packages/simulado`, no dentro de `apps/movil` | La ficha dice que F0-M **posee el andamiaje de MSW** y que `F0-B` y `F0-W` lo consumen. Dentro de la app no habría forma de importarlo sin copiarlo, y tres copias son tres servidores simulados distintos | `packages/simulado/` |
| La respuesta simulada se **genera del esquema**, con dos modos | `representativo` es el éxito y `minimo` es el vacío. Un vacío hecho podando la respuesta puede dejarla fuera del contrato: con el mínimo de la lista respetado, el vacío sigue siendo válido — y la prueba de contrato lo comprueba | `muestra.ts` |
| El generador es **determinista** | Un simulado que cambia entre corridas convierte cada prueba del frontend en un sorteo. Y sin acotar las repeticiones abiertas, un dígito repetible puede salir cien veces | `muestra.ts` |
| Los contratos se convierten a JSON en `packages/simulado/generado/` | Metro y Jest empaquetan JSON, no YAML. La conversión no interpreta nada: si el contrato está mal, el JSON sale mal y la prueba de contrato lo dice | `scripts/contratos-a-json.mjs` |
| Los ganchos se llaman `use<Concepto>` y no `usar<Concepto>` | Es la forma que usa la propia skill `movil-expo` (`useAporte`). El prefijo no es gusto: sin él, `react-hooks/rules-of-hooks` deja de verificar las reglas de los ganchos, y lo dijo el linter antes que nadie | `src/dominio/` |
| Los tres corredores usan el preset de Expo, y se le **agregan** los paquetes ESM de MSW | Escribir la lista entera a mano la deja vieja en cuanto Expo cambie la suya | `jest.config.js` |
| `msw/native` y no `msw/node` en las pruebas | El corredor resuelve con las condiciones de React Native, donde el punto de entrada de node no existe | `pruebas/servidorDePruebas.ts` |

## Correcciones al troncal encontradas al ejecutar

Las cuatro salieron al montar la Legion sobre el carril 0, y **ninguna es visible desde
macOS**. Van en su propio commit, porque detienen a la máquina entera y no a un carril.

1. **`inputSpec` con ruta absoluta de Windows.** El generador de OpenAPI la interpreta
   como URI y falla con «Illegal character in opaque part at index 2»: se lleva puesto
   `compileJava` de los catorce servicios. Pasa a forma `file:`.
2. **`gradlew` registrado con CRLF.** La conversión automática viene encendida por
   omisión en Windows; el Dockerfile lo copia y la construcción del gateway muere con
   `/bin/sh: 1: ./gradlew: not found`. Se declara en `.gitattributes`: el fin de línea
   es del repositorio, no de la configuración de cada máquina.
3. **Los verificadores en cp1252.** `verificar_boveda.py` y `verificar_carriles.py`
   morían con un error de codificación antes de decir si algo falla.
4. **El resolvedor de wikilinks comparaba rutas con separador de Windows.** Cuatro
   notas sanas quedaban declaradas rotas.

## Supuestos declarados

Regla cero: ninguno silencioso.

1. **El monorepo yarn no existía y lo creé.** El gate de entrada de la fase F0 pide
   «el monorepo yarn, el `tsconfig.base.json`, el lint y el CI ya existen», y la ficha
   `T0` lo lista como entrega del troncal — pero en `dev` no había `package.json` de
   raíz, ni `tsconfig.base.json`, ni `apps/`. Sin eso no hay carril. **Lo creé como
   parte de esta rama y lo declaro acá**: es material del troncal, y si P1 prefiere
   otra forma, se cambia **antes** de que los otros dos andamiajes lo consuman.
2. **`clientes/typescript` no expone modelos para CU-01.** El borrador de la Fase 0
   genera `SaldoBilletera` y `Dinero`, pero la operación de registro no produjo modelos
   propios. Los tipos de `registro.ts` se declaran contra el mismo contrato y **se
   verifican** en `CU01.contrato.spec.ts`. Cuando el carril `1A` amplíe el contrato,
   esos tipos tienen que salir del cliente generado y no de ahí.
3. **Los tokens de `src/tokens/` son andamiaje, no sistema de diseño.**
   `apps/*/src/tokens/**` es del carril `F1`. Existe `andamiaje.ts` para que la pantalla
   de F0 tenga de dónde tomar un color sin esparcir literales por los componentes, y
   **se borra al cerrar F1**. Está dicho en su cabecera y en `tokens/README.md`.
4. **Versiones del SDK 54.** Salen del manifiesto de módulos nativos de `expo@54`, no de
   memoria: `expo-router ~6.0.24`, `react-native 0.81.5`, `react 19.1.0`,
   `expo-secure-store ~15.0.8`, `expo-crypto ~15.0.9`.

## Huecos encontrados, no completados con una suposición

| Hueco | Dónde | Por qué importa |
| --- | --- | --- |
| **`verificar_seguridad.py` rechaza al propio troncal.** Todo `permitAll()` es falla y no hay forma de declarar la excepción; la configuración de seguridad del carril 0 abre la sonda de salud y las rutas públicas, y por eso el gate está en rojo | `scripts/verificar_seguridad.py` vs. `plataforma/comun-web/.../ConfiguracionDeSeguridad.java` | Es decisión del dueño del troncal: o el verificador gana una marca declarada, o la configuración cambia. **No lo toqué**: `scripts/` y `plataforma/` no son de este carril |
| `verificar_boveda.py` sigue en rojo por un wikilink de `docs/Views/AportaYa-Maqueta.md` a una nota de `planes/`, que está fuera de la bóveda | `docs/Views/` | Es parte del gate de salida de `F0-W`. Ya estaba declarado en el informe del carril 0 |
| La validación de respuesta contra el esquema **no corre en el dispositivo** | `src/dominio/` | El motor de JavaScript de la app no tiene `eval`, y el validador compila con `new Function`. Corre en Jest sobre las mismas respuestas que ve la app. Resolverlo de verdad pide validadores precompilados, y eso es trabajo de F12 |
| El código del simulado **queda dentro del paquete exportado** | `npx expo export` | El interruptor de `app.json` impide que se ejecute, pero no que se empaquete. Sacarlo pide un perfil de compilación que lo elimine, y los perfiles de EAS son de F12 |
| Jest avisa «a worker process has failed to exit gracefully» al correr dos corredores en paralelo | `apps/movil` | Con detección de handles abiertos **no aparece ninguno** y las 21 pruebas pasan. Queda anotado: no se declara resuelto lo que no se entendió |

## Lo que queda abierto, y de quién es

| Qué | De quién | Cuándo |
| --- | --- | --- |
| `yarn --cwd apps/movil start` verificado en Expo Go o emulador | este carril | Cuando haya emulador Android acelerado en esta máquina |
| `apps/backoffice` y `apps/web` consumiendo `@aportaya/simulado` | **P4 Dell A** y **P5 Dell B** | Ola F0 · mismo tramo |
| ADR-037 y ADR-038 | **P5 Dell B** (`F0-W`) | Ola F0 |
| Playwright, Maestro y los `Dockerfile` de web y backoffice | `F0-B` y `F0-W` | Ola F0 |
| `packages/ui` y los tokens de verdad | **F1**, este mismo puesto | T2 |
| Pasos de frontend en el CI (`yarn lint`, `typecheck`, `test:front`, `test:a11y`) | **P1**, por micro-PR | Antes de cerrar el tramo |

## Frases prohibidas sin evidencia

No se dice «está listo». Lo que hay es: **21 pruebas en verde repartidas en tres
corredores, 1.285 módulos empaquetados por Metro para Android, tres clientes TypeScript
generados del contrato, una pantalla con sus cuatro estados probados contra el mismo
simulado que ve la app, y la prueba que demuestra que agregar una pantalla no toca
ningún archivo compartido.**

## Ver también

[[11 Fases F0 y F1 · Cimientos y sistema de diseño]] · [[16 Carriles de frontend]] ·
[[18 Fichas de carril · las 38 unidades de trabajo]] · [[10 Plan maestro del frontend]]
