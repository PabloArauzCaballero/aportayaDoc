---
tags:
  - plan
  - informe
  - carril
titulo: "Carril F0-B — andamiaje del backoffice"
ola: F0
fase: F0
modulo: apps/backoffice
rama: dev
estado: en curso
---

# Carril F0-B — andamiaje del backoffice

**Fase** F0 · **Casos de uso** ninguno de negocio · **Puesto** P4 · Dell A
*(ejecutado desde la Legion: ver «Quién lo hizo»)*

> Tramo **T1** de [[17 Plan de acción secuencial · coordinación de cinco máquinas]].
> Ficha en [[18 Fichas de carril · las 38 unidades de trabajo]] · `F0-B`.
> Posee `apps/backoffice/**`. El andamiaje de MSW es de `F0-M` y acá **se consume**,
> no se reimplementa.

> [!note] Quién lo hizo
> La ficha asigna este carril a **P4 · Dell A**. Se ejecutó desde la Legion porque
> `F0-M` ya estaba cerrado y este carril solo necesitaba eso: el andamiaje MSW y el
> cliente generado. **No hay conflicto de propiedad**: `apps/backoffice/**` era un
> directorio nuevo y nadie más lo tocó. Si P4 estaba trabajando en paralelo, esto se
> revisa antes de fusionar, no después.

## Qué está hecho, con la salida que lo prueba

| Entregable | Evidencia | Estado |
| --- | --- | :-: |
| React 19 + Vite 8 con **TanStack Router por archivos** y TanStack Query | `vite build` → 397 módulos, 307 KB (98,7 KB comprimido) | ✅ |
| Una pantalla real contra MSW con sus **cuatro estados** | `PantallaDeBilletera` · 6 pruebas: cargando, éxito, vacío, error, sin red y `403` | ✅ |
| **`noindex` desde el primer día** | `<meta name="robots" content="noindex, nofollow, noarchive">`, verificado en el HTML servido **y** en el publicado | ✅ |
| **La prueba del gate**: agregar una pantalla vacía no toca ningún registro compartido | `enrutamiento-por-archivos.spec.ts`, 3 pruebas | ✅ |
| `jsx-a11y` como **error** y `jest-axe` en su propio corredor | `lint` sin hallazgos · 3 pruebas de accesibilidad | ✅ |
| `yarn --cwd apps/backoffice dev` con la pantalla contra MSW | Vite responde `200`, el `mockServiceWorker.js` se sirve, el `noindex` viaja | ✅ |
| La `TablaDeDatos` virtualizada | **No se hizo, a propósito**: es del carril `F6`. Adelantarla es diseñar sin el sistema de diseño, que todavía no existe | — |

### Evidencia, con el comando y su resultado

```
yarn workspace @aportaya/backoffice lint         sin hallazgos
yarn workspace @aportaya/backoffice typecheck    sin errores
yarn workspace @aportaya/backoffice test:front   3 archivos · 12 pruebas · 0 falladas
yarn workspace @aportaya/backoffice test:a11y    1 archivo  ·  3 pruebas · 0 falladas
yarn workspace @aportaya/backoffice build        397 modulos · 307,60 kB · sin msw
npx vite --port 5174                             HTTP 200 · worker servido
```

## Decisiones tomadas, y por qué

| Decisión | Por qué |
| --- | --- |
| El token del operador vive **solo en memoria**, y el refresco en cookie `HttpOnly` | Un token en `localStorage` lo lee cualquier script que llegue a la página. El backoffice mira expedientes con datos de personas |
| Un `403` responde «no tenés acceso» y nada más | Detallarlo le confirma a quien prueba que el recurso existe. El catálogo traduce por código y, si no lo conoce, por estado |
| El estado vacío **dice que no es un error de consulta** | En un backoffice, «la cuenta está en cero» y «la consulta falló» llevan a acciones distintas: una se le informa al titular, la otra abre una incidencia |
| El `cuentaId` va en la URL | Un oficial tiene que poder pegar el enlace de lo que está mirando dentro de un expediente (`web-backoffice`) |
| El `import` de MSW es dinámico y dentro de `import.meta.env.DEV` | Así Vite no lo mete en el paquete de producción. **Verificado: cero coincidencias de `msw` en `dist/`** |
| Los matchers de `jest-dom` se registran con `expect.extend` a mano | El atajo `@testing-library/jest-dom/vitest` depende de que el `expect` global exista al importarse, y con proyectos de Vitest ese orden no está garantizado. Fallaba en cinco pruebas con «Invalid Chai property» |
| `staleTime: 0` en la consulta de saldo | Un saldo cacheado en una consulta de soporte es un saldo que se le lee al titular por teléfono |

## Supuestos declarados

1. **Vite 8 y TanStack Router 1.170**, que son los actuales. El plan dice «React 19 +
   Vite» sin fijar versión, así que no hay desviación que declarar sobre Vite; sí se
   deja escrito que el enrutador es `@tanstack/react-router` con su plugin, y que el
   árbol de rutas es **generado** (`src/arbolDeRutas.gen.ts`, no versionado).
2. **La pantalla real consulta `consultarSaldo` de `nucleo-financiero`.** Es la única
   operación de lectura que hoy existe en los contratos. Cuando `2A` amplíe el
   contrato, la pantalla de operación de verdad la construye `F7`.
3. **Los tokens de `src/tokens/` son andamiaje**, no sistema de diseño: eso es de `F1`,
   que posee `apps/*/src/tokens/**`. Se borra al cerrar F1 y lo dice su `README`.

## Lo que queda abierto, y de quién es

| Qué | De quién | Cuándo |
| --- | --- | --- |
| Playwright + Chromium para E2E del backoffice | `F0-B`/`F6` | Cuando exista una pantalla con flujo real |
| `Dockerfile.backoffice` multietapa servido por NGINX | `F0-B` | Junto con `Dockerfile.web` de `F0-W` |
| La `TablaDeDatos` virtualizada con filtros en la URL | **`F6`** (P3, T3) | T3 |
| Las pantallas de operación y cumplimiento | **`F7`** y **`F8`** | T6–T7 |
| Pasos del backoffice en el CI | **P1**, por micro-PR | Antes de cerrar el tramo |

## Frases prohibidas sin evidencia

No se dice «está listo». Lo que hay es: **15 pruebas en verde repartidas en tres
corredores, 397 módulos empaquetados sin un rastro del servidor simulado, `noindex`
verificado en el HTML servido y en el publicado, y la prueba que demuestra que agregar
una pantalla no toca ningún archivo compartido.**

## Ver también

[[11 Fases F0 y F1 · Cimientos y sistema de diseño]] · [[16 Carriles de frontend]] ·
[[carril-F0-M]] · [[13 Fases F6 a F8 · Backoffice]]
