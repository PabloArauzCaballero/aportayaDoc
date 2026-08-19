---
tags:
  - moc
  - plan
  - frontend
titulo: "Plan maestro de desarrollo del frontend — AportaYa"
fecha: 2026-08-14
alcance: apps/movil · apps/backoffice · apps/web · packages/ui
---

# Plan maestro de desarrollo del frontend

> **Para quién es este documento.** Para la IA (o la persona) que va a escribir el
> frontend. Es el espejo de [[00 Plan maestro]]: dice **qué** construir y **en qué
> orden**. El **cómo se escribe** está en
> [[10b Estándar de ejecución del frontend]], y **quién hace qué en cada máquina**
> en [[16 Carriles de frontend]].

> [!important] Se apoya en el backend, pero no lo espera
> El frontend arranca cuando existe el **contrato OpenAPI** de un caso de uso, no cuando
> existe su implementación. Los contratos se escriben **antes** que el código (skill
> `contratos-api`), así que las olas del frontend van **una ola detrás** de las del
> backend, programando contra el contrato y un servidor simulado. Ver §9.

---

## 1 · Tres productos, no dos

ADR-004 define dos. **Este plan agrega un tercero**, y hay que decirlo en voz alta:

| Producto | Usuario | Tecnología | SEO |
| --- | --- | --- | :-: |
| **`apps/movil`** — app del participante | Participante, organizador | Expo / React Native | — no es web |
| **`apps/backoffice`** — operación y cumplimiento | Oficial de cumplimiento, soporte, contabilidad, riesgos | React + Vite, TanStack | **`noindex`** — va detrás de login |
| **`apps/web`** — sitio público | Cualquiera, más auditores y terceros | **Astro 5 + islas React** | **Sí. Es la única superficie indexable** |

### Por qué el tercer producto no es un capricho

No se agrega para tener marketing. **La bóveda ya lo exige sin haberlo declarado:**

| Obligación | De dónde sale |
| --- | --- |
| Publicar el tarifario con preaviso, accesible al público | **CU-34**, `documento_publicado`, `R-TAR-08`, ASFI transparencia |
| Verificación pública del sorteo, sin sesión | **CU-61** · `GET /publico/sorteos/:id/verificacion` |
| Cadena de transparencia auditable por un tercero | **CU-72**, **CU-73** · `GET /publico/grupos/:codigo/…` |
| Certificado de reputación verificable por quien lo recibe | **CU-75** · `GET /verificar/:codigo` |
| Informar canales y puntos de reclamo | `punto_reclamo`, ASFI Libro 4 Título I |
| Publicar el contrato de adhesión vigente | **CU-05**, `contrato_adhesion`, `R-CON-06` |

Cuatro casos de uso ya devuelven **rutas públicas sin sesión**. Sin `apps/web` esas
rutas devuelven JSON a un navegador y la obligación de transparencia queda sin
superficie.

> **Requiere [[ADR-037 Sitio público]]** escrito en la Fase F0, con la plantilla de
> `decisiones-adr`, enmendando ADR-004 (que descartó Next.js **como billetera**, no
> como sitio público — no hay contradicción, pero hay que dejarlo escrito).

---

## 2 · Los ocho invariantes del frontend

Espejo de los diez del backend. Ninguna fase los suspende.

| # | Invariante | De dónde sale | Cómo se verifica |
| :-: | --- | --- | --- |
| 1 | **La vista no llama a la red.** Todo pasa por la capa de dominio | [[Prompt de frontend]] §2 | Lint: sin `fetch`/`axios` fuera de `dominio/` |
| 2 | **Los tipos vienen del contrato**, nunca se reescriben a mano | [[ADR-006 Contratos y validación]] | Lint: sin `interface` que duplique un esquema de `clientes/typescript` (generado del OpenAPI) |
| 3 | **Ningún valor de diseño literal** fuera del archivo de tokens | skill `disenar-frontend` | Lint: sin hex, sin px sueltos en componentes |
| 4 | **Los cuatro estados, siempre**: cargando, vacío, error, éxito | [[Prompt de frontend]] §3 | Prueba por pantalla con datos |
| 5 | **Ningún importe se formatea a mano.** Solo el átomo `Monto` | [[ADR-005 Dinero y decimales]] | Lint: sin `toFixed`, sin `Intl.NumberFormat` fuera de `Monto` |
| 6 | **Toda operación de dinero envía clave de idempotencia**, y el botón se bloquea | [[ADR-004 Frontend]] | Prueba de doble envío por flujo |
| 7 | **El cliente nunca es la garantía.** Valida para ayudar; el servidor protege | [[Prompt general de desarrollo]] §7 | Revisión: ninguna regla de negocio vive solo acá |
| 8 | **Un componente > ~150 líneas mezcla niveles** | [[ADR-009 Composición atómica]] | Lint de tamaño |

### Y dos más, propios del sitio público

| # | Invariante | Por qué |
| :-: | --- | --- |
| 9 | **Ninguna página con datos de terceros se indexa.** `/verificar/*` y `/publico/*` van `noindex, nofollow` | Un certificado pertenece a una persona. Indexarlo expone datos personales y viola `R-SEG-03`. **El SEO no puede pelearse con la protección de datos, y cuando se pelean gana la protección** |
| 10 | **No se publica una afirmación regulatoria que no sea cierta hoy** | La licencia está `EN_TRAMITE`. Decir "regulados por ASFI" antes de la resolución es falso, y en un sitio de finanzas es exactamente lo que un supervisor busca |

---

## 3 · Stack fijado

| Capa | Elección | Nota |
| --- | --- | --- |
| Lenguaje | TypeScript estricto | Mismo `tsconfig.base.json` del backend |
| Gestor | **yarn** workspaces | El monorepo ya existe de la Fase 0 del backend. yarn es el único gestor (nunca pnpm ni npm) |
| Orquestador | **Turborepo** | Corre y cachea las tareas del frontend (`build`, `lint`, `test:front`, `test:a11y`) sobre los workspaces yarn; el pipeline de `turbo.json` respeta el grafo de dependencias entre `apps/*` y `packages/*` |
| App | **Expo SDK 54** / React Native, **Expo Router** (file-based) | `expo-camera`, `expo-secure-store`, `expo-local-authentication`, EAS Update |
| Backoffice | **React 19 + Vite**, **TanStack Router** (file-based) + TanStack Query | Tablas virtualizadas, exportación |
| Sitio público | **Astro 5** + islas React, adaptador Node | Estático por defecto; SSR solo en verificación |
| Diseño | **`packages/ui`** — tokens, átomos, moléculas, organismos | skill `disenar-frontend` · `docs/Views/Sistema-Diseno/` |
| Estado servidor | **TanStack Query** en los tres | Caché con invalidación explícita |
| Formularios | **React Hook Form + los tipos generados del OpenAPI** | Invariante 2: el esquema no se reescribe |
| Cliente de API | **Generado** desde el contrato OpenAPI, un archivo por CU | `clientes/typescript` — no se edita, **sin dueño de carril** |
| Logs de cliente | Sin PII, con `traza` propagada al backend | Cabecera `x-request-id` |
| Pruebas unitarias | **Jest + Testing Library** | Se prueba lo que el usuario ve |
| Pruebas de componente | **Jest + RTL**, con **MSW** para la API | Mocks derivados del contrato OpenAPI |
| Pruebas E2E web | **Playwright + Chromium** | Backoffice y sitio público |
| Pruebas E2E móvil | **Maestro** sobre build de desarrollo | Playwright no maneja React Native |
| Accesibilidad | `jest-axe` + `@axe-core/playwright` | Bloqueante en CI |
| Lint | ESLint 9 + `eslint-plugin-jsx-a11y` + reglas propias | §6 |
| Empaquetado | **Docker** para backoffice y web; **EAS** para la app | ADR-012 |

### Lo que está prohibido

- `fetch` dentro de un componente.
- Un hex, un `px` o una fuente literal fuera de `tokens.ts`.
- `toFixed` o aritmética sobre importes en el cliente.
- Reescribir a mano un tipo que ya está en `clientes/typescript` (generado del OpenAPI).
- Una pantalla de datos sin sus cuatro estados.
- Un `div` que hace de botón.
- Datos sensibles en la URL, en el estado global o en `AsyncStorage` plano.

### Base URL, sesión y CORS

Los tres clientes —app, backoffice y sitio— apuntan a **una sola base URL: el
gateway**. Nunca se habla directo con un servicio: el **prefijo de la ruta** es lo que
enruta al servicio correcto ([[19 Contrato de carril · conflicto cero, skills y calidad verificada]] §3.1).

| Regla | Cómo se implementa |
| --- | --- |
| **Una sola base URL** | Toda petición sale hacia el gateway. El servicio destino se resuelve por el prefijo, no por el host |
| **Refresh vía gateway** | El refresh va a `identidad` **a través del gateway**, como cualquier otra ruta |
| **Un `401` ⇒ un intento** | Un `401` en cualquier servicio dispara **un** intento de refresh y **un** reintento de la petición original. Si el refresh falla, se cierra la sesión global (una sola vez, no en bucle) |
| **CORS en el gateway** | El gateway configura CORS para los orígenes del **backoffice** y del **sitio** —los dos clientes de navegador—. El frontend no gestiona CORS: lo recibe resuelto |

> **Esta política vive en un solo lugar del cliente:** el interceptor de la capa de
> dominio (F0.2) y el `ProveedorSesion` del shell móvil (F2). Ninguna pantalla ve un
> `401` ni conoce la URL de `identidad`.

---

## 4 · Las cuatro capas del frontend

Es [[ADR-009 Composición atómica]] aplicado a la interfaz. **Dirección única.**

```
pantallas/       PÁGINA       compone organismos + resuelve la ruta. Sin lógica
  ↓
organismos/      ORGANISMO    sección autónoma: formulario, tabla, panel
  ↓
moleculas/       MOLÉCULA     una responsabilidad: campo con error, fila, hook de un recurso
  ↓
atomos/          ÁTOMO        pieza visual mínima: Boton, Campo, Monto, ChipEstado
      ↑
dominio/                      cliente de API por CU, tipado desde contratos
tokens/                       único lugar con valores literales
```

| Capa | Puede depender de | Nunca hace | Prueba |
| --- | --- | --- | --- |
| `atomos/` | `tokens/` | Conocer una regla de negocio o llamar a la API | Rinde sus variantes y estados |
| `moleculas/` | `atomos/`, `dominio/` | Orquestar la pantalla, decidir navegación | Comportamiento: escribe, valida, emite |
| `organismos/` | `moleculas/`, `atomos/`, `dominio/` | HTTP directo | Flujo completo con API simulada, con error y reintento |
| `pantallas/` | `organismos/` | Cálculos, reglas | E2E |
| `dominio/` | `clientes/typescript` (generado del OpenAPI) | Renderizar | Contrato: la respuesta simulada valida contra el esquema del contrato OpenAPI |

**Lo que sirve a dos productos sube a `packages/ui`.** Lo que depende de una API
nativa (cámara, biometría) se queda en `apps/movil`.

---

## 5 · Las 13 fases

| Fase | Nombre | Producto | Superficie | Documento |
| :-: | --- | --- | --- | --- |
| **F0** | Cimientos del frontend | los tres | andamiaje, cliente de API, ADR | [[11 Fases F0 y F1 · Cimientos y sistema de diseño]] |
| **F1** | Sistema de diseño | `packages/ui` | tokens, átomos, moléculas, organismos | ídem |
| **F2** | Shell móvil | `apps/movil` | navegación, sesión, tema, offline | [[12 Fases F2 a F5 · App móvil]] |
| **F3** | Móvil · identidad y cuenta | `apps/movil` | CU-01…09, 40, 46 | ídem |
| **F4** | Móvil · billetera | `apps/movil` | CU-10…19, 30…33, 57 | ídem |
| **F5** | Móvil · pasanaku y comunidad | `apps/movil` | CU-20…29, 52, 53, 59…76 | ídem |
| **F6** | Shell backoffice | `apps/backoffice` | router, tabla de datos, roles | [[13 Fases F6 a F8 · Backoffice]] |
| **F7** | Backoffice · operación | `apps/backoffice` | billetera, conciliación, cobranza, reclamos | ídem |
| **F8** | Backoffice · cumplimiento y gobierno | `apps/backoffice` | UIF, ASFI, reportes, tablero, comités | ídem |
| **F9** | Sitio público · estructura y contenido | `apps/web` | páginas, contenido regulatorio, verificación | [[14 Fases F9 a F11 · Sitio público, SEO y GEO]] |
| **F10** | **SEO** | `apps/web` | metadatos, JSON-LD, sitemap, CWV | ídem |
| **F11** | **GEO** — optimización para motores generativos | `apps/web` | `llms.txt`, espejo markdown, política de crawlers | ídem |
| **F12** | Endurecimiento, accesibilidad, E2E y publicación | los tres | tiendas, despliegue | [[15 Fase F12 · Endurecimiento, E2E y publicación]] |

---

## 6 · Reglas de lint propias del frontend

| Regla | Qué prohíbe | Invariante |
| --- | --- | :-: |
| `aportaya/sin-red-en-vista` | `fetch`, `axios`, `XMLHttpRequest` fuera de `dominio/` | 1 |
| `aportaya/tipos-del-contrato` | Declarar un tipo que ya exporta `clientes/typescript` (generado del OpenAPI) | 2 |
| `aportaya/sin-literal-de-diseno` | Hex, `rgb()`, `px` y familias tipográficas fuera de `tokens/` | 3 |
| `aportaya/sin-formato-de-dinero` | `toFixed`, `Intl.NumberFormat` y concatenar `'Bs '` fuera del átomo `Monto` | 5 |
| `aportaya/capas-front` | `atomos/` importando de `dominio/` u `organismos/` | — |
| `aportaya/tamano-componente` | ≥ 150 líneas advierte · ≥ 200 bloquea | 8 |
| `aportaya/sin-console-log` | `console.*` en runtime | — |
| `jsx-a11y/*` (recomendado, como **error**) | `div` con `onClick`, imagen sin `alt`, control sin etiqueta | — |

---

## 7 · Estrategia de pruebas

| Nivel | Herramienta | Contra qué | Archivo |
| --- | --- | --- | --- |
| **Átomo** | Jest + RTL | Variantes y estados | `<Atomo>.spec.tsx` |
| **Molécula** | Jest + RTL | Comportamiento observable | `<Molecula>.spec.tsx` |
| **Organismo** | Jest + RTL + **MSW** | Flujo con API simulada, **incluidos error y reintento** | `<Organismo>.spec.tsx` |
| **Contrato** | Validación contra el OpenAPI | La respuesta simulada **valida contra el esquema del contrato OpenAPI** | `CU<NN>.contrato.spec.ts` |
| **Accesibilidad** | `jest-axe` | Cero violaciones serias por pantalla | `<Pantalla>.a11y.spec.tsx` |
| **E2E web** | Playwright + Chromium | Backoffice y sitio público | `<flujo>.e2e.spec.ts` |
| **E2E móvil** | Maestro | App en build de desarrollo | `<flujo>.maestro.yaml` |
| **Visual** | Playwright screenshots | Claro y oscuro, por componente | `<Componente>.visual.spec.ts` |

### Las cinco pruebas obligatorias de toda pantalla con datos

1. **Los cuatro estados**: cargando, vacío, error (con reintento) y éxito.
2. **Doble envío**: si produce un efecto, dos clics ⇒ **una** llamada, misma clave de
   idempotencia.
3. **Sin conexión**: muestra el último estado y no permite operar.
4. **Accesibilidad**: `jest-axe` sin violaciones serias; foco visible; navegable por
   teclado.
5. **Contrato**: la respuesta que usa el mock valida contra el esquema OpenAPI de
   `clientes/typescript` (generado del OpenAPI). Un mock que no valida es una pantalla que ya está rota.

> **La prueba 5 es la que evita el desastre clásico del frontend**: pantallas verdes
> contra mocks inventados que no se parecen a lo que la API devuelve.

---

## 8 · Metadatos, SEO y GEO — dónde vive cada cosa

Resumen; el detalle está en [[14 Fases F9 a F11 · Sitio público, SEO y GEO]].

| Superficie | Indexación | Metadatos |
| --- | --- | --- |
| `apps/web` — páginas de contenido | **`index, follow`** | Completos: title, description, canonical, hreflang `es-BO`, OG, Twitter, JSON-LD |
| `apps/web` — `/verificar/*`, `/publico/*` | **`noindex, nofollow`** | Mínimos. **Nunca** JSON-LD con datos de la persona |
| `apps/backoffice` | **`noindex, nofollow`** + `X-Robots-Tag` en NGINX | Solo `title` |
| `apps/movil` | — | *App Store Optimization*, que es otra disciplina (F12) |

**Política de rastreadores de IA elegida: búsqueda sí, entrenamiento no.**
Se permiten los bots que citan (`OAI-SearchBot`, `ClaudeBot`, `PerplexityBot`) y se
bloquean los de entrenamiento (`GPTBot`, `Google-Extended`, `Applebot-Extended`,
`CCBot`). Todos, sin excepción, bloqueados en `/verificar/` y `/publico/`.

---

## 9 · Cómo se sincroniza con el backend

**El frontend no espera la implementación: espera el contrato.**

```
backend   Ola 0 ──── Ola 1 ──── Ola 2 ──── Ola 3 ──── Ola 4 ──── Ola 5
frontend            Ola F0 ─── Ola F1 ─── Ola F2 ─── Ola F3 ─── Ola F4
                    (una ola de desfase)
```

| Qué necesita el frontend | Cuándo está |
| --- | --- |
| `openapi/<servicio>.yaml` | Lo escribe el carril de backend **antes** de implementar |
| Un servidor simulado | **MSW**, con las respuestas derivadas del OpenAPI. No espera a nadie |
| La API real | Solo para el E2E de la Fase F12 y para las pruebas de integración de cada ola |

**Regla dura:** si el contrato de un CU todavía no existe, **el frontend no lo
inventa** (regla cero). Lo pide al carril de backend correspondiente y trabaja en otro
mientras tanto.

---

## 10 · Gate de fase — el mismo para las 13

- [ ] `yarn lint` y `yarn typecheck` en verde
- [ ] `yarn test:front` (unitarias, componente, contrato) en verde
- [ ] `yarn test:a11y` sin violaciones serias
- [ ] **Los cuatro estados** implementados y probados en cada pantalla con datos
- [ ] **Cero literales de diseño** fuera de tokens (verificado por lint)
- [ ] Ningún componente sobre el límite sin justificación escrita
- [ ] Los tipos vienen del contrato; ningún mock que no valide contra su esquema OpenAPI
- [ ] Claro y oscuro probados; `prefers-reduced-motion` respetado
- [ ] Contraste AA (≥ 4.5:1), foco visible, navegación completa por teclado
- [ ] El checklist de §6 de la skill `disenar-frontend`, ejecutado
- [ ] Supuestos declarados en el informe del carril

## Ver también

[[10b Estándar de ejecución del frontend]] · [[16 Carriles de frontend]] · [[00 Plan maestro]] · [[ADR-004 Frontend]] · [[Prompt de frontend]] · [[AportaYa-Identidad]] · [[Index]]
