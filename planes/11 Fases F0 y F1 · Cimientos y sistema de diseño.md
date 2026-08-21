---
tags:
  - plan
  - fase
  - frontend
titulo: "Fases F0 y F1 — Cimientos y sistema de diseño"
fases: [F0.0, F0, F1]
depende_de: []
habilita: [F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12]
---

# Fases F0.0, F0 y F1 — Maqueta, cimientos y sistema de diseño

> **Se ejecuta en:** Ola F0 · carril T (troncal, máquina única). **Ningún otro carril
> de frontend trabaja hasta que su gate esté ejecutado.** Ver
> [[16 Carriles de frontend]].

> [!important] Antes de escribir la primera línea
> [[10b Estándar de ejecución del frontend]] y
> [[00b Estándar de ejecución · código limpio, pruebas y calidad]] aplican en las dos
> fases: regla cero, composición atómica, KISS, tokens, los cuatro estados,
> accesibilidad. **Se declara cada componente por nivel antes de crearlo.**

---

# FASE F0.0 — Maqueta navegable con backend simulado

> **Objetivo.** Que exista el **recorrido completo de los dos productos, navegable y
> presentable**, con cada llamada al backend simulada, **antes de escribir una sola línea
> de `apps/`**. No es un borrador que se tira: es la **especificación visual** que F1 y las
> fases de pantalla reproducen.

> **Se ejecuta en:** una máquina, antes de la Ola F0. **Entregable:**
> [[AportaYa-Maqueta|docs/Views/AportaYa-Maqueta.html]] + su ficha.

## Por qué es la primera fase y no un adorno

1. **Quien financia decide sobre algo que puede tocar**, no sobre un documento de pantallas.
   Un recorrido clickeable en el celular de cada director hace por la decisión lo que
   cuarenta páginas de prosa no hacen.
2. **Adelanta las decisiones caras al momento en que son baratas.** Cuántas pestañas, dónde
   vive pagar el aporte, si el saldo se relee o se ajusta, si el efectivo entra: resolverlo
   en un HTML cuesta una tarde; resolverlo en F4, con el organismo ya construido y consumido
   por tres pantallas, cuesta diez veces más.
3. **Convierte la prosa en criterio de aceptación.** F1 no discute cómo se ve una
   `TarjetaSaldo`: la compara contra la maqueta.

## Gate de entrada

- [ ] `python3 scripts/verificar_boveda.py` → **TODO OK**
- [ ] [[Flujo de pantallas · app del participante]] y
      [[Flujo de pantallas · backoffice administrador]] escritos
- [ ] el sistema de diseño con sus tokens: `docs/Views/Sistema-Diseno/estilos.css`

## Las seis reglas de la maqueta — para que no mienta

Una maqueta que muestra algo que el sistema no va a hacer es peor que no tenerla: fija
expectativas que después hay que romper.

| # | Regla | Por qué |
| :-: | --- | --- |
| 1 | **Los tokens se copian de `estilos.css`**, no se reinterpretan | Un hex que diverja convierte la maqueta en una fuente de verdad falsa |
| 2 | **Rutas, códigos de error y servicio dueño salen de la bóveda** (`docs/CasosDeUso/`, `scripts/modelo.py` → `PREFIJOS`) | Es lo que la vuelve reutilizable como referencia de los manejadores de MSW |
| 3 | **Ningún dato es real** y el pie lo dice | Montos, nombres y grupos son de ejemplo; nadie debe poder citar una cifra de acá |
| 4 | **Tres escenarios de red**: todo bien · intermitente con reintento idempotente · rechazo con el código del CU | Los cuatro estados y la idempotencia se **muestran**, no se prometen |
| 5 | **El saldo se relee del backend**, nunca se ajusta en memoria | Es la regla §0.2b del flujo de pantallas: si la maqueta la viola, la enseña mal |
| 6 | **Todo hueco o divergencia que aparezca se declara**, no se completa con una suposición | Es la regla cero de [[Contrato de implementación para IA]] |

## Gate de salida F0.0

- [ ] Recorrido **completo** de participante y de operador, navegable de punta a punta
- [ ] Cada pantalla cita **su CU y su endpoint reales**; el servicio dueño se resuelve con `PREFIJOS`
- [ ] Los tres escenarios de red funcionan, incluido el **reintento con la misma clave de idempotencia**
- [ ] **Cada pantalla del backoffice declara para qué sirve** en lenguaje de negocio
      ([[Flujo de pantallas · backoffice administrador]] §0.6)
- [ ] `python3 scripts/verificar_boveda.py` → **TODO OK** *después* de corregir lo que la maqueta destape
- [ ] Las divergencias encontradas están **corregidas en la bóveda o declaradas** como hueco
- [ ] Publicada, y **abierta en un dispositivo que no sea el de quien la hizo**

## Lo que F0.0 **no** es

- **No es código de `apps/`.** No crea el monorepo, no instala Expo, no toca `packages/ui`.
- **No se reutiliza el HTML.** Lo que sobrevive es la decisión de diseño y el mapa
  pantalla ↔ CU ↔ endpoint, no el marcado.
- **No reemplaza a F1.** El sistema de diseño se construye igual, átomo por átomo.

## Cómo la consumen las fases siguientes

| Fase | Qué toma de la maqueta |
| --- | --- |
| **F0.3** Servidor simulado con MSW | Las **rutas, los códigos de error y los tres escenarios** ya elegidos: los manejadores los reproducen en vez de inventarlos |
| **F1** Sistema de diseño | El **criterio de aceptación visual** de cada átomo, molécula y organismo |
| **F2–F8** Pantallas | Cada pantalla se compara contra la suya; lo que difiera se justifica o se corrige |

## Evidencia de que la fase sirve

La primera corrida de F0.0 (agosto de 2026) **destapó ocho defectos en la bóveda** que la
prosa no había mostrado, y todos se corrigieron antes de que existiera código que los
heredara:

| # | Lo que apareció | Dónde |
| :-: | --- | --- |
| 1 | `CU-08` usaba un ámbito `PLATAFORMA` **que no existe** en el enum (`GLOBAL \| GRUPO \| ORGANIZACION`) | contrato y `gherkin` de [[CU-08 Asignar y revocar roles de operador]] |
| 2 | `R-SEG-04` y `R-SEG-07` **intercambiadas** en dos documentos | `CU-08` y `planes/06` §17.5 |
| 3 | `CU-08` describía un **estado que la tabla no tiene** («asignación inactiva») | [[CU-08 Asignar y revocar roles de operador]] |
| 4 | «Pagar» figuraba en los accesos rápidos, **contra el sistema de diseño** | [[Flujo de pantallas · app del participante]] §4.1 |
| 5 | La maqueta tenía una pestaña «Avisos» que **la documentación nunca pidió** | §3 del mismo documento |
| 6 | **Nadie había escrito** que el saldo se relee en vez de ajustarse en memoria | §0.2b, regla nueva |
| 7 | `POST /sesion` en el flujo de pantallas contra `POST /sesiones` en el CU | divergencia declarada |
| 8 | 24 permisos que los CU exigen y el catálogo no tiene · 3 propósitos de token sin canal activo | brechas **S-8** y **S-9** de [[Seguridad]] |

Ninguno se habría visto leyendo. Todos aparecieron al intentar dibujarlos.

---

# FASE F0 — Cimientos del frontend

> **Objetivo.** Que `yarn dev:movil`, `yarn dev:backoffice` y `yarn dev:web` levanten
> los tres productos, cada uno mostrando una pantalla real contra un servidor
> simulado derivado de los contratos. Sin una sola pantalla de negocio todavía.

## Gate de entrada

- [ ] Fase 0 del backend cerrada: el monorepo yarn, el `tsconfig.base.json`, el lint y
      el CI ya existen
- [ ] el `openapi/identidad.yaml` existe con al menos la operación de CU-01 escrita, y
      su cliente TypeScript ya está generado en `clientes/typescript` — lo entrega
      **`01 §0.8`** del backend, no la Fase 2 (delta 1 de
      [[17 Plan de acción secuencial · coordinación de cinco máquinas]])

> [!note] Esta fase se ejecuta en tres puestos a la vez
> Los tres andamiajes de F0.1 son **tres directorios nuevos**: móvil, backoffice y
> web se reparten entre tres máquinas sin colisión (delta 2). Lo único compartido son
> el lint y el CI, y eso lo toca **solo el puesto del troncal**, por micro-PR.
> **F1 no se parte**: `packages/ui` es de un solo puesto.

## Leer antes

`docs/Arquitectura/ADR-004 Frontend.md` · `docs/Arquitectura/Prompts/Prompt de frontend.md` ·
`docs/Views/AportaYa-Identidad.md` · `docs/Views/Sistema-Diseno/` (el catálogo visual
completo, HTML navegable) · skills `disenar-frontend`, `movil-expo`, `web-backoffice`

## F0.1 · Los tres andamiajes

```
turbo.json          pipeline de tareas del frontend (build, lint, test:front, test:a11y)
apps/
├── movil/          Expo SDK 54 · Expo Router (file-based)
│   └── src/{tokens,atomos,moleculas,organismos,pantallas,dominio}/
├── backoffice/     React 19 + Vite · TanStack Router (file-based) + Query
│   └── src/{atomos,moleculas,organismos,rutas,dominio}/
└── web/            Astro 5 + islas React · adaptador Node
    └── src/{content,pages,componentes,seo,estilos}/
packages/
└── ui/             tokens + átomos + moléculas + organismos compartidos
clientes/
└── typescript/     cliente generado desde el OpenAPI · no se edita · sin dueño de carril
```

> **`turbo.json` es el orquestador**, no un gestor: yarn resuelve las dependencias de los
> workspaces y **Turborepo** corre y cachea las tareas respetando el grafo `apps/*` ↔
> `packages/*` (así `packages/ui` compila antes que las apps que lo consumen). Lo crea el
> andamiaje móvil (F0-M, primer usuario) y queda de solo lectura para el resto, como el
> `tsconfig.base.json`.

> **`clientes/typescript` no es un paquete de este monorepo con dueño: es un artefacto
> generado.** Lo regenera quien corre `generateOpenApiClients` a partir de los
> `openapi/*.yaml` del backend; no se versiona a mano y por eso no produce conflictos.
> La **capa de dominio por CU** (los hooks de TanStack Query) vive en el `dominio/` de
> cada app y **importa** de este cliente generado.

> **Los tres usan enrutamiento por sistema de archivos.** No es un detalle: es lo que
> permite que varios carriles agreguen pantallas **sin editar nunca un registro
> compartido** ([[16 Carriles de frontend]] §4). Un `routes.tsx` central sería un
> conflicto de merge por PR.

## F0.2 · La capa de dominio sobre `clientes/typescript`

Un archivo por caso de uso en el `dominio/` de cada app, que **envuelve** el cliente
generado y lo tipa desde el contrato:

```ts
// apps/movil/src/dominio/CU21.ts
import { EntradaCU21, SalidaCU21, ErroresCU21 } from 'clientes/typescript/CU21'

export function usarCobrarAporte() { /* TanStack Query mutation */ }
```

Reglas:
- **Los tipos vienen del cliente generado** (`clientes/typescript`). Nunca se declaran a
  mano (invariante 2).
- Cada mutación acepta y reenvía la **clave de idempotencia**.
- La respuesta se **valida** contra el esquema OpenAPI de salida en desarrollo y en pruebas: si la
  API devuelve algo que no encaja, se descubre acá y no en la pantalla.
- Errores traducidos: `AP-CU<NN>-<nn>` → mensaje en voz de marca, en un catálogo
  versionado.
- La cabecera `x-request-id` viaja en cada request para que la traza del cliente
  llegue al log del backend.
- **Una sola base URL: el gateway.** El cliente apunta siempre al gateway y el prefijo
  enruta al servicio; el refresh va a `identidad` vía gateway. Un `401` dispara **un**
  intento de refresh y **un** reintento; si falla, sesión cerrada global. Es el
  interceptor de esta capa quien lo maneja, no la pantalla (ver
  [[10 Plan maestro del frontend]] §3).

## F0.3 · Servidor simulado con MSW

Los *handlers* se generan **desde los contratos**: la respuesta simulada se construye
con el esquema OpenAPI de salida, no a mano.

> **Un mock escrito a mano diverge del contrato en la segunda semana y deja la
> pantalla verde mientras ya está rota.** Por eso la prueba de contrato
> (`CU<NN>.contrato.spec.ts`) es obligatoria: valida que el mock encaje en el esquema
> del contrato OpenAPI.

## F0.4 · Herramientas de calidad

| Pieza | Configuración |
| --- | --- |
| Jest + Testing Library | Proyectos `front-unit`, `front-componente`, `front-a11y` |
| MSW | Servidor simulado en pruebas y en `dev` |
| `jest-axe` | Como **error**, no advertencia |
| Playwright + Chromium | E2E de backoffice y web |
| Maestro | E2E de la app; Playwright no maneja React Native |
| ESLint | `jsx-a11y` recomendado **como error** + las 7 reglas propias de §6 del maestro |
| Docker | `Dockerfile.backoffice` y `Dockerfile.web` multietapa, sin root, servidos por NGINX |
| EAS | Perfiles `development`, `preview`, `production`; EAS Update para OTA |

## F0.5 · Los dos ADR

| ADR | Qué decide |
| --- | --- |
| **ADR-037 · Sitio público** | Que existe un tercer producto web y por qué (§1 de [[10 Plan maestro del frontend]]: CU-34, CU-61, CU-72, CU-73, CU-75, punto de reclamo, contrato de adhesión). Elige **Astro + islas React**: estático por defecto ⇒ menos JS ⇒ mejores Core Web Vitals ⇒ mejor posicionamiento, y *content collections* para los documentos regulatorios. Enmienda ADR-004, que descartó Next.js **como billetera**, no como sitio público |
| **ADR-038 · Política de rastreadores de IA** | **Búsqueda sí, entrenamiento no.** Se permiten `OAI-SearchBot`, `ClaudeBot`, `PerplexityBot`; se bloquean `GPTBot`, `Google-Extended`, `Applebot-Extended`, `CCBot`. Todos bloqueados en `/verificar/` y `/publico/`. Es decisión de negocio, no técnica, y por eso lleva ADR |

**Entregable F0:** los tres productos levantan; el cliente de API valida contra los
contratos; MSW responde; CI en verde con los cinco corredores.

## Gate de salida F0

```bash
yarn lint && yarn typecheck
yarn dev:movil     # abre en Expo Go o simulador
yarn dev:backoffice && yarn dev:web
yarn test:front && yarn test:a11y
docker build -f docker/Dockerfile.web . && docker build -f docker/Dockerfile.backoffice .
```

- [ ] Gate común de §10 del plan maestro del frontend
- [ ] Una pantalla real por producto, contra MSW, con sus cuatro estados
- [ ] ADR-037 y ADR-038 escritos; `verificar_boveda.py` en verde
- [ ] Enrutamiento por archivos funcionando en los tres: una pantalla nueva **no
      requiere editar ningún registro compartido** (probado agregando una vacía)

---

# FASE F1 — Sistema de diseño (`packages/ui`)

> **Objetivo.** Que exista el catálogo completo de piezas visuales, con tokens, tema
> claro y oscuro y accesibilidad verificada, para que las diez fases siguientes
> **compongan** en vez de inventar.

**El sistema de diseño ya está especificado.** No se diseña acá: se implementa lo que
la skill `disenar-frontend` y `docs/Views/Sistema-Diseno/` ya definieron, con sus hex
exactos. Inventar un color o un espaciado en esta fase es el error más caro del
frontend, porque se propaga a los tres productos.

## Gate de entrada

- [ ] F0 cerrada

## F1.1 · Tokens — el único archivo con literales

`packages/ui/src/tokens/tokens.ts`, portado a CSS custom properties para web.

| Familia | Valores |
| --- | --- |
| Verde Pasanaku | `--g900 #0C2C1D` … `--g600 #1C5A3A` (**marca**) … `--g100 #E7F2EB` |
| Naranja Aporte | `--o700 #BC6217` · **`--o500 #E5852B` (acento/CTA)** · `--o100 #FDF0DF` |
| Neutros | con sesgo verde, a propósito |
| Semánticos | `--ok`, `--warn`, `--err`, `--info` — **separados del acento** |
| Espaciado | `--s1 4` · `--s2 8` · `--s3 12` · `--s4 16` · `--s5 24` · `--s6 32` · `--s7 48` |
| Radio | `--r-sm 8` · `--r-md 12` · `--r-lg 16` · `--r-xl 24` · `--r-pill 999` |
| Sombra | `--sh-1` · `--sh-2` · `--sh-3` |
| Tipografía | `--font-d Poppins` · `--font-b Inter` · `--mono` |

**Dark mode redefine solo tokens**, nunca componentes.

## F1.2 · Átomos

Botones (primario `--accent`, secundario `--brand`, fantasma, peligro, enlace, ícono,
FAB) en sm/base/lg, con los siete estados: normal, hover, active, foco, deshabilitado,
cargando · Campos (texto, con ícono, con addon `Bs`, **monto**, búsqueda, textarea,
select, fecha, stepper, contraseña con ojo, **PIN/OTP**) con normal/foco/error/éxito/
deshabilitado · Selección (checkbox, radio, switch, segmentado, chip) · Indicadores
(badge, chip removible, avatar 24/30/40/56, spinner, barra y anillo de progreso,
skeleton, tooltip, dot).

> **`Monto` es el átomo más importante del sistema.** Es el único lugar donde se
> formatea dinero: `tabular-nums`, prefijo `Bs`, coma decimal, `Bs 1.240,00`. Lleva
> prueba unitaria propia y una prueba de propiedad sobre el formateo.

## F1.3 · Moléculas

Campo de formulario (label + input + ayuda/error) · búsqueda · menú desplegable ·
selector de fecha · **input de monto con moneda** · grupo de filtros · **tarjeta
KPI** · **tarjeta de saldo** · **ítem de pasanaku** (avatar + info + progreso + badge)
· **fila de movimiento** · alerta/banner · toast · tabs · breadcrumb · paginación ·
acordeón · **stepper/wizard** · ítem de notificación · **fila de acciones rápidas**.

## F1.4 · Organismos

Navbar · sidebar · formulario completo · **tabla de datos** (toolbar con búsqueda y
filtros, orden por columna, selección múltiple, paginación, **virtualización**) ·
barra de filtros · modal · diálogo de confirmación · **`EstadoVacio`** ·
**`EstadoError`** (con variante `sinConexion`) · grilla de tarjetas · **lista de
movimientos agrupada por fecha**.

## F1.5 · Móviles (solo `apps/movil`)

Marco con status bar · app bar · **tab bar** (3–5 destinos) · **FAB** · **bottom
sheet** · **teclado numérico 3×4** · **entrada de PIN/OTP** · snackbar · **tarjeta de
saldo móvil** · onboarding · `ProveedorTema`.

## F1.6 · Catálogo vivo

Una ruta `/catalogo` en `apps/web`, **`noindex`**, que renderiza cada pieza en sus
variantes y estados, claro y oscuro. Es la referencia para revisión y la base de las
pruebas visuales.

## F1.7 · Los organismos que exige el recorrido del participante

El recorrido de [[Flujo funcional · recorrido del usuario]], desglosado en
[[Flujo de pantallas · app del participante]], fija **qué organismos concretos** tienen que
existir para que M1/M2/M3 solo compongan. F1 los entrega; los transversales suben a
`packages/ui`, los que dependen de una API nativa (cámara, biometría) se quedan en
`apps/movil`:

| Dominio de pantallas | Organismos (los construye F1, los compone el carril) | Dónde vive |
| --- | --- | --- |
| Identidad (M1) | `PanelBienvenida` · `FormularioRegistro` · `CapturaDocumento` · `VisorContrato` · `FormularioLogin` · `RegistroDispositivo` · `FormularioKYCReforzado` (`DeclaracionPEP`) · `FormularioPerfil` · `FormularioCambioContrasena` · `AsistenteBaja` | forma en `packages/ui`; captura/biometría en `apps/movil` |
| Billetera (M2) | `TarjetaSaldo` · `AccesosRapidos` · `ListaMovimientos` (`FilaMovimiento`) · `ResumenRecarga` · `PantallaQR` · `FormularioCuentaBancaria` · `FormularioAporte` (`FilaAporte`) · `PantallaResultado` | `packages/ui`; QR en `apps/movil` |
| Pasanaku (M3) | `CanjearInvitacion` · `FormularioPostulacion` · `TarjetaGrupo` · `ReglamentoGrupo` · `PanelSorteo` (`ListaTurnos`) · `AsistenteOrganizador` · `FormularioGrupo` · `TarjetaReputacion` · `FormularioReseña` (`EstrellasCalificacion`) | `packages/ui` |
| Shell (M) | `BarraPestanas` · `BandejaNotificaciones` (`FilaNotificacion`) | `packages/ui` |

Átomos y moléculas nuevos que esto obliga a incluir en F1.2/F1.3 (además de los ya listados):
**`CampoOTP`** (entrada de 6 casillas, además del PIN de F1.5), **`EstrellasCalificacion`**,
**`CampoCodigo`** (invitación), **`SelectorCuentaBancaria`** y **`SelectorCategoria`** (reclamo).
Todos con sus **cuatro estados** donde apliquen — es lo que hace que ninguna pantalla de dinero
pueda saltárselos.

### Los nueve que suma la maqueta de referencia

[[20 Maqueta de referencia · deltas del frontend]] fija nueve piezas más, y entran en F1
**antes** de que los carriles compongan: partirlas después es rehacer pantallas ya hechas.

| Componente | Nivel | Dónde se usa |
| --- | :-: | --- |
| `BarraDePasos` | molécula | Alta de cuenta, que pasa a ser de **ocho pasos** |
| `MarcoDeCamara` | organismo | Captura de documento y selfie, con sus controles de calidad a la vista |
| `FilaDeCotejo` | molécula | Declarado contra leído, campo por campo, con la diferencia marcada |
| `ChipsDeFiltro` | molécula | Movimientos, aportes pendientes, cualquier bandeja del backoffice |
| `ResumenDePeriodo` | molécula | Entró y salió del filtro activo |
| `FilaDeMovimiento` | molécula | **Tipada por lo que pasó**, con saldo corrido |
| `BannerDePauta` | organismo | Publicidad rotulada, con control para ocultarla |
| `SeccionDeExpediente` | organismo | KYC, incumplimientos, disputas y reclamos |
| `SelectorSegmentado` | átomo | Cambio entre vistas equivalentes |

Y dos reglas que `disenar-frontend` recoge y que valen para todo el sistema:

1. **El ícono dice qué pasó, no si el número sube o baja.** Un aporte, una recarga por
   QR, una comisión y un débito rechazado llevan íconos distintos: el color y el signo
   ya dicen la dirección. Un `+` y un `−` repetidos en toda la lista no informan nada.
2. **Los movimientos se agrupan por día**, con el neto del día y el **saldo corrido**
   al costado de cada línea. Una lista plana de importes no es un extracto.

## F1.8 · Los organismos que exige el recorrido del administrador

El recorrido de [[Flujo funcional · usuario administrador]], desglosado en
[[Flujo de pantallas · backoffice administrador]], fija los organismos del backoffice. La pieza
central es **`TablaDeDatos`** (F1.4, virtualizada); el resto son paneles y fichas específicos.
El backoffice **no es un segundo sistema de diseño**: usa los mismos tokens y átomos, portados a
densidad de escritorio.

| Área | Organismos (los construye F1/shell, los compone el carril) | Carril |
| --- | --- | --- |
| Shell / tablero | `TablaDeDatos`, `PanelKPIs` (`TarjetaKPI`), `PanelEstadoPlataforma` | B (F6) |
| Operación | `PanelDescuadre`, `ResumenCierre`, `PanelAprobacion`/`PanelEjecucion`, `FichaReclamo` (`LineaDeTiempo`), `PanelResolucion`, `EditorDePolitica`/`SimuladorDeReglas`, `EditorDeRoles`, `FormularioTarifario`/`PanelPreaviso`, `ConstructorDeReporte`, `FormularioInstrumentoFondeo`/`VistaPreviaQR`/`HistorialFondeo`, `EditorDePlantilla`/`MatrizDeCanales`/`PanelRuteoProveedor` | B1 (F7) |
| Cumplimiento | `RevisorDeIdentidad` (= cola + **`SeccionDeExpediente`** ×9), `PanelTriaje`, `FichaCaso`, `PanelROS`, `FormularioRequerimiento`, `ActaComite` | B2 (F8) |
| Sistemas | `PanelSLO`, `PanelDespliegues`, `PanelColas`, `PanelWebhooks`, `PanelRespaldos`, `PanelSaludProveedor`, `EditorDeRoles`, `FichaIncidente` | B5 (F8.D) |
| Contabilidad | `PanelPeriodo`, `EditorPresupuesto`, `FichaFactura`, `PanelDepreciacion`, `VisorEstadoFinanciero` | B3 (F13) |
| Publicidad | `FormularioPartner`, `FormularioAnunciante`, `PanelAprobacionCampana`, `ColaModeracion` | B4 (F14) |

Suben a `packages/ui` los transversales (`TablaDeDatos`, `PanelAprobacion`/`PanelEjecucion`,
`LineaDeTiempo`, `PanelSaludProveedor` —lo comparten fondeo y mensajería—, `SimuladorDeReglas`);
las fichas y paneles de un solo dominio viven en su ruta.

**Entregable F1:** el inventario completo con prueba unitaria por átomo, prueba de
comportamiento por molécula, `jest-axe` limpio y captura visual en ambos temas.

## Gate de salida F1

- [ ] Gate común
- [ ] Los cuatro grupos de piezas implementados y catalogados
- [ ] **Cero hex fuera de `tokens.ts`** en todo `packages/ui` (verificado por lint)
- [ ] `Monto` con su prueba de propiedad: nunca pierde ni inventa un centavo al formatear
- [ ] Contraste AA verificado **pieza por pieza**, en claro y en oscuro
- [ ] Área táctil ≥ 44 px en todas las piezas móviles
- [ ] `prefers-reduced-motion` respetado
- [ ] El catálogo `/catalogo` renderiza todo y va `noindex`

## Ver también

[[17 Plan de acción secuencial · coordinación de cinco máquinas]] · [[00c Recetario · implementar un caso de uso]] · [[16 Carriles de frontend]] · [[10 Plan maestro del frontend]] · [[10b Estándar de ejecución del frontend]] · [[12 Fases F2 a F5 · App móvil]] · [[AportaYa-Identidad]]
