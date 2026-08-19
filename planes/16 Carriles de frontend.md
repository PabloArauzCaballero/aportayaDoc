---
tags:
  - moc
  - plan
  - frontend
  - carriles
titulo: "Carriles de frontend — varias máquinas en paralelo"
fecha: 2026-08-14
---

# Carriles de frontend

> Espejo de [[07 Carriles de trabajo concurrente]], para los tres productos de
> interfaz. Misma regla de oro: **un carril = un producto o un dominio de pantallas =
> un directorio = una rama = una máquina = un chat**, con propiedad exclusiva de
> archivos para que el conflicto de merge sea imposible por diseño.

> [!important] Tres correcciones desde el plan secuencial
> [[17 Plan de acción secuencial · coordinación de cinco máquinas]] manda en **quién,
> en qué máquina y en qué tramo**, porque este documento y el del backend cuentan
> máquinas por separado y sumados piden ocho. Tres cambios concretos a lo que sigue:
> **la Ola F0 se parte en tres andamiajes concurrentes** (delta 2), **la rama es
> `<usuario>/feature/carril-<id>`** como manda `git-flujo` y no `carril/f…` (delta 3),
> y **la unidad de planificación es el tramo, no la ola** (delta 4). Lo demás de este
> documento —qué archivos posee cada carril— sigue vigente sin cambios.

---

## 1 · Por qué esto se paraleliza todavía mejor que el backend

Por tres razones, y las tres son decisiones de la Fase F0:

| Razón | Consecuencia |
| --- | --- |
| **Enrutamiento por sistema de archivos** en los tres productos (Expo Router, TanStack Router, Astro) | Una pantalla nueva es **un archivo nuevo**. No existe un `routes.tsx` central que todos editen |
| **El contrato OpenAPI existe antes que la implementación** | El frontend nunca espera al backend: programa contra el contrato y MSW |
| **`packages/ui` se congela al cerrar F1** | Los carriles **componen**, no diseñan. Nadie edita tokens ni átomos |

> **La consecuencia práctica:** el frontend no espera a que el backend termine un caso
> de uso. Espera a que **escriba su contrato**, que es lo primero que hace.

---

## 2 · Mapa de olas

### Ola F0 · Andamiajes y sistema de diseño — **tres máquinas en paralelo** (delta 2)

La fase F0 se parte en **tres andamiajes concurrentes**: son tres directorios
nuevos, colisión cero. Lo único compartido —lint, CI, `package.json`— lo toca
**solo P1**, por micro-PR. El cliente de API es **generado**
(`clientes/typescript/`) y no tiene dueño. `packages/ui` (F1) sigue siendo de
**un solo puesto**: partirlo es partir el sistema de diseño.

| Carril | Fase | Puesto | Posee |
| --- | :-: | :-: | --- |
| **F0-M** | F0 | P3 | andamiaje móvil (`apps/movil/**`) + MSW |
| **F0-B** | F0 | P4 | andamiaje backoffice (`apps/backoffice/**`) |
| **F0-W** | F0 | P5 | andamiaje web (`apps/web/**`, Astro) + ADR-037 y ADR-038 |
| **F1** | F1 | P3 | `packages/ui` — el sistema de diseño entero |

Bloquea las trece fases restantes. **`packages/ui` queda congelado al cerrar F1**: a
partir de ahí, un átomo nuevo se pide por micro-PR.

### Ola F1 · 3 carriles — los tres shells

| Carril | Fase | Producto | Directorio propio |
| --- | :-: | --- | --- |
| **M** | F2 | app móvil | `apps/movil/src/{navegacion,proveedores}/` |
| **B** | F6 | backoffice | `apps/backoffice/src/{layout,proveedores}/` + `organismos/TablaDeDatos` |
| **W** | F9 | sitio público | `apps/web/src/{pages,content,componentes}/` |

### Ola F2 · 5 carriles — máxima concurrencia

| Carril | Fase | Directorio propio | CU |
| --- | :-: | --- | --- |
| **M1** | F3 | `apps/movil/src/pantallas/identidad/` | 01–09, 40, 46 |
| **M2** | F4 | `apps/movil/src/pantallas/billetera/` | 10–19, 30–33, 57 |
| **B1** | F7 | `apps/backoffice/src/rutas/operacion/` | 26 CU de operación |
| **W1** | F10 | `apps/web/src/seo/` + `astro.config` de sitemap | SEO |
| **W2** | F11 | `apps/web/public/` + `src/geo/` | GEO |

> **W1 y W2 conviven sin pisarse** porque tocan cosas distintas: SEO escribe el
> componente `<Meta>` y el JSON-LD en `src/seo/`; GEO escribe `robots.txt`,
> `llms.txt`, el generador de espejos `.md` y la guía de redacción. El único punto de
> contacto es el `<head>`, y ahí **manda el componente `<Meta>` de W1**: W2 le pasa lo
> suyo por props (`alternateMarkdown`), no edita el componente.

### Ola F3 · 2 carriles

| Carril | Fase | Directorio propio | CU |
| --- | :-: | --- | --- |
| **M3** | F5 | `apps/movil/src/pantallas/pasanaku/` | 20–29, 52, 53, 59–76 |
| **B2** | F8 | `apps/backoffice/src/rutas/cumplimiento/` | 38 CU de cumplimiento y gobierno |

### Ola F4 · 3 carriles — publicación y los carriles nuevos

| Carril | Fase | Alcance |
| --- | :-: | --- |
| **T** | F12 | E2E, accesibilidad, rendimiento, seguridad, publicación |
| **B3** | F13 | `apps/backoffice/src/rutas/contabilidad/` — ERP (CU-100–106) |
| **B4** | F14 | `apps/backoffice/src/rutas/publicidad/` — publicidad (CU-110–114) |

### Resumen

```
Ola F0 ──► 3 máquinas   (andamiajes · delta 2) + 1 (F1, sistema de diseño)
Ola F1 ──► 3 máquinas
Ola F2 ──► 5 máquinas   ← pico
Ola F3 ──► 2 máquinas
Ola F4 ──► 3 máquinas   (F12 + los carriles nuevos F13 y F14)
```

**17 carriles en total** — eran 12: el delta 2 parte la troncal en cuatro y los
módulos 13 y 14 suman dos ([[18 Fichas de carril · las 38 unidades de trabajo]]).

### 2.6 · Las pantallas de cada carril móvil

El recorrido del participante ([[Flujo funcional · recorrido del usuario]]) está desglosado
pantalla por pantalla en [[Flujo de pantallas · app del participante]] — cada pantalla trae su
ruta de Expo Router, los organismos que compone, los cuatro estados y su endpoint. El reparto
por carril es directo (una pantalla = un archivo, sin router central):

| Carril | Fase | Pantallas que posee (`apps/movil/src/…`) |
| :-: | :-: | --- |
| **M** | F2 | `navegacion/` (tab bar, `ProveedorSesion`, deep links) · `pantallas/notificaciones/bandeja` |
| **M1** | F3 | `pantallas/identidad/`: bienvenida · registro · verificación básica · contrato · sesión · mfa · dispositivo · **verificación profunda** · perfil · contraseña · baja |
| **M2** | F4 | `pantallas/billetera/`: inicio (saldo) · recargar · retirar · cuenta-bancaria · extracto · pagar-aporte (saga) · confirmación |
| **M3** | F5 | `pantallas/pasanaku/`: unirse · postular · grupo/[codigo] · **organizador** · **crear** 🔒 · sorteo · reputación · reseñar · **reclamo** · **denunciar** |

> **El gate básica/profunda es del shell, no de cada pantalla.** El `ProveedorSesion` de **M**
> expone el nivel de verificación; las acciones de nivel profundo (crear grupo, habilitarse
> como organizador) las pinta M3 **deshabilitadas con motivo**. Así el gate se prueba una vez,
> en el shell, y no se repite en cada pantalla.

### 2.7 · Las pantallas de cada carril de backoffice

El recorrido del administrador ([[Flujo funcional · usuario administrador]]) está desglosado en
[[Flujo de pantallas · backoffice administrador]]. El backoffice es React + Vite con TanStack
Router (una ruta = un archivo) y el organismo `TablaDeDatos` como pieza de trabajo. Reparto:

| Carril | Fase | Rutas que posee (`apps/backoffice/src/rutas/…`) |
| :-: | :-: | --- |
| **B** | F6 | shell: `layout/`, `proveedores/`, `organismos/TablaDeDatos`, tablero, **`operacion/estado`** (estado de plataforma) |
| **B1** | F7 | `operacion/`: conciliación · cierre-diario · desembolsos (autorizar/ejecutar) · reclamos · **políticas-resolución** · roles · tarifario · reportes · **fondeo (QR)** · **mensajería** |
| **B2** | F8 | `cumplimiento/`: **verificaciones** (aceptar/rechazar) · alertas · casos/ROS · uif · riesgo · gobierno |
| **B3** | F13 | `contabilidad/`: período · presupuesto · compras/CxP · cobros · activos · estados (mini-contable esencial) |
| **B4** | F14 | `publicidad/`: partners · anunciantes · campañas (aprobar) · moderación · liquidación |

> **La segregación de funciones se pinta en pantalla:** desembolsos, compras/CxP y campañas
> muestran solo el lado (autorizar **o** ejecutar / gestionar **o** aprobar) que el rol del
> operador permite — nunca los dos botones. El guard de rutas monta cada una **según el permiso
> del token**, así que la navegación misma refleja el `roles-y-accesos` del backend.

---

## 3 · Sincronía con el backend

Las olas de frontend van **una detrás** de las de backend. Cada carril de frontend
consume los **contratos** de la ola anterior de backend, no su implementación.

```
backend    Ola 0 ─── Ola 1 ─── Ola 2 ─── Ola 3 ─── Ola 4 ─── Ola 5
frontend             Ola F0 ── Ola F1 ── Ola F2 ── Ola F3 ── Ola F4
```

| Carril de frontend | Contratos que necesita | Los escribe |
| --- | --- | --- |
| M1 · F3 identidad | CU-01…09, 40, 46 | backend Olas 1A y 1C |
| M2 · F4 billetera | CU-10…19, 30…33, 57 | backend Olas 2A y 2B |
| B1 · F7 operación | los 26 de operación | backend Olas 2 y 3 |
| M3 · F5 pasanaku | CU-20…29, 59…76 | backend Olas 2C, 3 y 4 |
| B2 · F8 cumplimiento | los 38 | backend Olas 2D, 2E y 3C |
| W · F9 sitio | CU-30, 34, 61, 72, 73, 75 | backend Olas 2B y 3B |

**Si un contrato no existe todavía, el carril de frontend no lo inventa.** Lo pide al
carril de backend y trabaja en otra pantalla mientras tanto (regla cero).

---

## 4 · Propiedad de archivos

### Lo que un carril posee en exclusiva

| Ruta | Nota |
| --- | --- |
| Su directorio de pantallas o rutas | Todo lo de adentro, incluido su `dominio/` (los hooks por CU que envuelven el cliente generado) |
| Sus *handlers* de MSW | `pruebas/mocks/<servicio>/` — organizados **por servicio**, no por dominio de carril. El handler de un CU lo crea el **primer carril** que lo necesita; el segundo lo importa (barrido: un CU no tiene dos handlers) |
| `planes/informes/carril-<id>.md` | Su informe |

> **El cliente de API no aparece acá porque no tiene dueño.** `clientes/typescript` es
> **generado** desde los `openapi/*.yaml` del backend: no se edita a mano, lo regenera
> quien corre `generateOpenApiClients` y por eso el solape de CU **deja de ser un
> conflicto** ([[20 Saneamiento del plan · huecos de la migración a microservicios]] §6.2–6.3).

### Lo que ningún carril toca (solo lectura)

| Ruta | Quién la cambia |
| --- | --- |
| **`packages/ui/**`** | Ola F0. **Congelado al cerrar F1.** Un átomo nuevo = micro-PR |
| El `openapi/` de cada servicio | Los carriles de **backend** |
| `apps/*/src/tokens/**` | Ola F0. Jamás durante un carril |
| `apps/movil/src/{navegacion,proveedores}/` | Carril M (F2), luego congelado |
| `apps/backoffice/src/{layout,proveedores}/` | Carril B (F6), luego congelado |
| `apps/web/src/seo/<Meta>` | Carril W1 (F10) |
| `package.json`, `yarn.lock`, configs, `docker/`, `.github/` | Micro-PR |
| **`.claude/skills/**`** | **Micro-PR.** Las 65 skills son de todos ([[19 Contrato de carril · conflicto cero, skills y calidad verificada]] §1) |

---

## 5 · Los seis puntos de conflicto, y cómo se eliminan

Se implementan en la **Ola F0**.

| # | Conflicto | Solución |
| :-: | --- | --- |
| 1 | Un registro central de rutas | **Enrutamiento por archivos** en los tres productos. El registro no existe |
| 2 | Barril `packages/ui/index.ts` con 80 exports | Sin barril: `@aportaya/ui/Boton`. Un componente nuevo no edita nada compartido |
| 3 | Un archivo global de traducciones o textos | **Un archivo por dominio de pantallas**, en el directorio del carril |
| 4 | Storybook con un registro central | El catálogo es `/catalogo` en Astro, **por descubrimiento de archivos** |
| 5 | `yarn.lock`: dos carriles agregan dependencias | Todas se instalan en la Ola F0. Ninguna en rama de carril |
| 6 | W1 (SEO) y W2 (GEO) editando el `<head>` | El componente `<Meta>` es de **W1**; W2 le pasa lo suyo por props |

---

## 6 · Micro-PR al troncal

Igual que en el backend ([[07 Carriles de trabajo concurrente]] §6). El caso más común
acá: **un carril necesita un átomo que `packages/ui` no tiene.**

```
1  ¿Existe algo parecido? Reusar antes que crear
2  ¿Lo van a usar dos productos o dos pantallas? → sube a packages/ui por micro-PR
3  ¿Es de un solo dominio? → vive en el directorio del carril, sin micro-PR
4  rama troncal/<carril>-<pieza> · con su prueba y su entrada en /catalogo
5  PR [MICRO] → revisión prioritaria · todos rebasan
```

> **Regla del tercer uso.** No se sube a `packages/ui` al segundo uso: al tercero. Y
> **nunca se duplica** un átomo — dos `Monto` con formatos distintos es cómo dos
> pantallas empiezan a mostrar el mismo saldo diferente.

---

## 7 · Puntos de sincronización entre olas

- [ ] Todos los carriles del tramo fusionaron a `dev` (`git-flujo`: los PR apuntan a
      `dev`; `dev → main` solo cuando el tramo cierra entero y en verde)
- [ ] `dev` pasa el CI completo, incluidos `test:a11y` y Lighthouse CI
- [ ] Cada carril ejecutó su gate y lo registró en su informe
- [ ] Micro-PR pendientes, fusionados
- [ ] Cada máquina hace `git pull`; **nadie regenera nada**: `packages/ui` está congelado
- [ ] **Revisión visual conjunta**: una máquina abre `/catalogo` y las pantallas nuevas
      en claro y oscuro. Es la única forma de detectar que dos carriles resolvieron lo
      mismo de dos maneras

---

## 8 · Montar una máquina nueva

```bash
git clone <repo> && cd Pasanaku
git checkout -b <usuario>/feature/carril-<id> origin/dev    # git-flujo · delta 3

yarn install --immutable          # sin yarn add

# las skills llegaron completas y la sesión las ve  (19 §1)
ls .claude/skills | grep -v README | wc -l        # 65
python3 scripts/verificar_boveda.py               # "índice de skills completo"

yarn dev:mock                     # MSW: no necesita el backend levantado

# según el carril:
yarn dev:movil                    # Expo — requiere simulador o Expo Go
yarn dev:backoffice
yarn dev:web

yarn lint && yarn typecheck && yarn test:front && yarn test:a11y
```

**Los carriles de frontend no necesitan el backend corriendo.** Trabajan contra MSW.
La API real aparece en los puntos de sincronización y en la Fase F12.

Los carriles móviles necesitan además: Android Studio o un dispositivo físico —
**preferentemente de gama baja**, que es el parque real en Bolivia.

---

## 9 · Prompt de arranque de un carril

```text
Sos el carril <ID> de la ola F<N> del frontend de AportaYa.

ANTES DE ESCRIBIR NADA, leé en este orden:
  planes/00b Estándar de ejecución · código limpio, pruebas y calidad.md
  planes/10b Estándar de ejecución del frontend.md          ← cómo se escribe la UI
  planes/10 Plan maestro del frontend.md                    ← invariantes y stack
  planes/16 Carriles de frontend.md                         ← qué archivos podés tocar
  planes/<documento de tu fase>.md
  docs/CasosDeUso/CU-<NN> *.md  (todos los de tu carril — la sección INTERFAZ manda)
  .claude/skills/disenar-frontend/SKILL.md  +  docs/Views/Sistema-Diseno/

TU ALCANCE
  Fase:        F<N>
  Producto:    <movil | backoffice | web>
  Casos de uso: <lista>
  Rama:        <usuario>/feature/carril-<id>          (PR hacia dev)

POSEÉS EN EXCLUSIVA
  <su directorio de pantallas o rutas>   (incluido su dominio/ por CU)
  pruebas/mocks/<servicio>/              (el handler de un CU lo crea el primer carril; el segundo lo importa)
  planes/informes/carril-<id>.md

NO TOCÁS (solo lectura)
  packages/ui/   clientes/typescript/   apps/*/src/tokens/
  los shells (navegacion/, proveedores/, layout/)   apps/web/src/seo/
  package.json  yarn.lock  docker/  .github/
  ¿Necesitás un átomo nuevo? Micro-PR. NO lo crees en tu rama.

REGLAS QUE NO SE NEGOCIAN
  - Regla cero: la pantalla sale de la sección Interfaz del CU. No se inventa.
    Si falta algo crítico, PARÁS Y PREGUNTÁS. Si no, declarás el supuesto.
  - Los cuatro estados en toda pantalla con datos: cargando, vacío, error, éxito.
  - Cero literales de diseño. Todo desde tokens.
  - Ningún fetch en un componente. Ningún tipo reescrito a mano.
  - Ningún importe formateado fuera del átomo Monto.
  - Doble envío bloqueado en toda operación de dinero, con la misma clave.
  - Un solo botón naranja por pantalla.
  - Accesibilidad bloqueante: teclado, foco, contraste AA, semántica.
  - No declarás nada terminado sin haber ejecutado el comando.

TERMINÁS CUANDO
  El gate de salida de tu fase está ejecutado, con evidencia en
  planes/informes/carril-<id>.md, y tu PR pasa el CI.

Empezá listando los componentes que vas a crear, por nivel, y esperá mi visto bueno.
```

---

## 10 · Cuando dos carriles se pisan igual

| Síntoma | Causa | Qué se hace |
| --- | --- | --- |
| Dos componentes casi iguales con nombres distintos | Ninguno abrió micro-PR | Se unifica en `packages/ui` y se borran los dos. **Prioridad alta** |
| El mismo saldo se ve distinto en dos pantallas | Alguien formateó fuera de `Monto` | Se revierte. Es rechazo sin discusión |
| Un carril necesita un endpoint que no existe | El contrato no está escrito | Se pide al carril de backend. **No se inventa el contrato** |
| Conflicto en `<head>` entre W1 y W2 | W2 editó `<Meta>` | Se revierte: W2 pasa props |
| Dos pantallas resuelven el mismo estado vacío distinto | Falta revisión visual conjunta | Se unifica en el punto de sincronización |

---

## 11 · Lo que **no** se paraleliza

- **La Ola F0.** Es el sistema de diseño; si se parte, cada carril inventa su propia
  paleta y el producto deja de verse como un producto.
- **Un cambio de tokens.** Para todo, se hace en troncal, se revisa el catálogo
  completo en claro y oscuro, y recién ahí los carriles rebasan.
- **La Ola F4.** Accesibilidad, rendimiento y publicación se miden sobre el producto
  entero.

## Ver también

[[19 Contrato de carril · conflicto cero, skills y calidad verificada]] · [[18 Fichas de carril · las 38 unidades de trabajo]] · [[17 Plan de acción secuencial · coordinación de cinco máquinas]] · [[10 Plan maestro del frontend]] · [[10b Estándar de ejecución del frontend]] · [[07 Carriles de trabajo concurrente]] · [[informe]] · [[disenar-frontend]]
