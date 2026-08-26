---
tags:
  - plan
  - informe
  - carril
titulo: "Carril M1 — móvil · identidad y cuenta"
ola: F2
fase: F3
modulo: apps/movil
rama: pablo/feature/carril-0T-cimientos
estado: bloqueado
---

# Carril M1 — móvil · identidad y cuenta

**Fase** F3 · **Tramo** T4 · **Máquina** Mac (P1) · **Pantallas** once, en
`apps/movil/src/pantallas/identidad/`

> Este informe se abre **sin haber escrito una línea de la app**, y explica por qué.
> El paso 0 sí está hecho: las once pantallas quedan declaradas contra su CU, su
> endpoint y su organismo, para que cuando el bloqueo se levante F3 sea ejecución y
> no diseño.

## 1 · Por qué el carril no arrancó

[[17 Plan de acción secuencial · coordinación de cinco máquinas]] §5 · T4 no deja esto
librado a interpretación. Dice, textual, por qué el Mac cambia de lado justo en T4:

> «Porque a partir de este punto existe todo lo que la app móvil necesita para no
> inventar nada: `packages/ui` congelado (T2), el shell (T3) y los contratos de
> identidad (T2).»

Son tres precondiciones. **Se cumple una.**

| # | Precondición | Dueño | Comprobación | Estado |
| :-: | --- | --- | --- | :-: |
| 1 | `packages/ui` congelado | Ola F1 · sistema de diseño | `ls packages` → no existe | ❌ |
| 2 | Shell móvil (`apps/movil/`) | Carril **M** · F2 · **P3 Legion** | `find apps -type f` → un solo archivo, `apps/backoffice/.gitignore` | ❌ |
| 3 | Contratos de identidad | Carriles de backend | `clientes/typescript/identidad` presente | ⚠️ parcial |

No existe el andamiaje móvil (**F0-M**, `apps/movil/**`, P3 según
[[16 Carriles de frontend]] §2), no existe `package.json` ni `yarn.lock` en el
repositorio, y no hay ningún proyecto Expo. La comprobación es directa:

```
$ find . -name package.json -not -path "*/node_modules/*" -not -path "./.git/*"
$ ls yarn.lock
   AUSENTE
```

### Por qué no lo construí igual

Porque los tres faltantes son, uno por uno, **rutas que [[16 Carriles de frontend]] §4
marca como «lo que ningún carril toca»**:

| Ruta que haría falta crear | Quién la cambia, según §4 |
| --- | --- |
| `packages/ui/**` | Ola F0. **Congelado al cerrar F1.** Un átomo nuevo = micro-PR |
| `apps/movil/src/{navegacion,proveedores}/` | Carril M (F2), luego congelado |
| `apps/*/src/tokens/**` | Ola F0. **Jamás durante un carril** |
| `package.json`, `yarn.lock`, configs | Micro-PR |

Y porque el reparto de trabajo lo dice sin rodeos en §7 del
[[Flujo de pantallas · app del participante]]: los organismos de identidad
«los **construye F1** en `packages/ui` […] y los carriles M1/M2/M3 solo los
**componen**». Un carril M1 que además construyera sus propios organismos estaría
inventando el sistema de diseño por su cuenta — que es exactamente lo que
[[16 Carriles de frontend]] §11 nombra como lo único que **no** se paraleliza:

> «La Ola F0. Es el sistema de diseño; si se parte, cada carril inventa su propia…»

Construirlo desde M1 no sería adelantar F3: sería hacer F0-M, F1 y F2 con nombre de
F3, en una rama de carril, sin el gate de ninguna de las tres.

## 2 · Paso 0 — las once pantallas, declaradas

Fuente: [[Flujo de pantallas · app del participante]] §2 (la especificación pantalla
por pantalla) y [[12 Fases F2 a F5 · App móvil]] § FASE F3. **Nada de esta tabla es
invención**; donde la bóveda no dice, la fila lo declara como hueco en §3.

| # | Pantalla | CU | Endpoint que nombra la bóveda | Organismo que compone | De dónde sale el organismo |
| :-: | --- | :-: | --- | --- | --- |
| 2.1 | `identidad/bienvenida` | — | — | `PanelBienvenida` | `packages/ui` (F1) |
| 2.2 | `identidad/registro` | CU-01 | `POST /usuarios` | `FormularioRegistro` | F1 |
| 2.3 | `identidad/verificacion-basica` | CU-01 | `POST /usuarios/{id}/verificacion` | `CapturaDocumento` | `apps/movil` (nativo: cámara) |
| 2.4 | `identidad/contrato` | CU-05 | `POST /usuarios/{id}/contrato` | `VisorContrato` | F1 |
| 2.5 | `identidad/sesion` | CU-04 | `POST /sesion` | `FormularioLogin` | F1 |
| 2.6 | `identidad/mfa` | CU-04 | `POST /sesion/mfa` | `CampoOTP` + `TecladoNumerico` | F1 |
| 2.7 | `identidad/dispositivos` | CU-04 | `POST·GET·PATCH·DELETE /sesion/dispositivos` | `RegistroDispositivo`, `ListaDispositivos` | `apps/movil` (nativo: biometría) |
| 2.8 | `identidad/verificacion-profunda` | CU-02, CU-03 | `POST /usuarios/{id}/nivel`, `POST /usuarios/{id}/pep` | `FormularioKYCReforzado`, `DeclaracionPEP` | F1 |
| 2.9a | `identidad/perfil` | CU-07 | `PUT /usuarios/{id}` | `FormularioPerfil` | F1 |
| 2.9b | `identidad/contrasena` | CU-09 | `POST /usuarios/{id}/contrasena` | `FormularioCambioContrasena` | F1 |
| 2.9c | `identidad/baja` | CU-09, CU-16 | `POST /usuarios/{id}/baja` | `AsistenteBaja` | F1 |

**Lo que suma [[20 Maqueta de referencia · deltas del frontend]]** y no está en la
tabla de arriba, porque son deltas sobre las mismas rutas:

- **D-1** · el alta de 2.2–2.4 es de **ocho pasos**, no de tres pantallas: datos ·
  confirmar celular · anverso · reverso · prueba de vida · **cotejo campo a campo** ·
  perfil del cliente con origen de fondos · contrato con los tres consentimientos
  separados. Y un resultado que dice los **límites concretos**.
- **D-8** · antes del formulario va un **tour de cuatro pantallas** saltable, que
  contesta las cuatro objeciones de siempre.
- **D-9** · al terminar, **bono de bienvenida** y estado de cuenta nueva.

## 3 · Huecos declarados

Regla cero: lo que la bóveda no dice, no se rellena con una suposición. Cinco
divergencias encontradas al preparar el paso 0, ninguna crítica para el bloqueo pero
todas resolubles antes de escribir código.

| # | Hueco | Dónde choca | Qué haría falta |
| :-: | --- | --- | --- |
| H-1 | `POST /sesion` vs `/sesiones` | El flujo de pantallas dice `/sesion`; `openapi/identidad.yaml` publica `/sesiones` | Manda el contrato (precedencia: `openapi/` sobre `docs/Flujo*`). Corregir el doc de pantallas |
| H-2 | El alcance de CU de F3 no coincide entre planes | `planes/12` lista CU-01, 02, 03, 04, 05, 06, 07, 09, 40, 46 — **sin 08**; `planes/17` T4 dice «CU-01–09», que lo incluye | Decidir si CU-08 (asignar rol) tiene pantalla en móvil. Backend ya está: carril 1A |
| H-3 | **CU-40 y CU-46 no tienen pantalla** en el flujo | `planes/12` les asigna una fila cada uno; `grep "CU-40\|CU-46"` en el flujo de pantallas → 0 resultados | Especificarlas, o declarar que son estados dentro de otras pantallas |
| H-4 | CU-05 lo sirve una pantalla de M1, pero el backend lo mudó a **1C** (`cumplimiento`) | Decisión del carril 0T: sus tablas viven en `cumplimiento` | Ninguna acción en M1; anotar la dependencia cruzada |
| H-5 | De los ~15 endpoints que piden las once pantallas, **hay 2 contratados** | `identidad.yaml` publica `registrarUsuario` y `autenticar` y nada más | Los demás llegan con sus carriles de backend (CU-02, 03, 06, 07 no están construidos) |

## 4 · Lo que sí quedó hecho y verificado

| Entregable | Evidencia | Estado |
| --- | --- | :-: |
| Cliente TypeScript regenerado desde los contratos vigentes | `./gradlew generateOpenApiClients` → BUILD SUCCESSFUL; `autenticar` aparece junto a `registrarUsuario` | ✅ |
| El cliente compila | `yarn global add typescript@5` + `tsc -p clientes/tsconfig.json` → sin errores | ✅ |
| Paso 0 del ciclo de carril | Este documento, §2 y §3 | ✅ |

> `clientes/typescript/` está en `.gitignore`: es **generado**, no versionado
> ([[16 Carriles de frontend]] §4, «el cliente de API no aparece acá porque no tiene
> dueño»). Por eso la regeneración no produce diff. El artefacto local estaba viejo,
> no el repositorio.

## 5 · Qué desbloquea este carril

En orden, y ninguno es del Mac según [[17 Plan de acción secuencial · coordinación de cinco máquinas]] §5:

1. **F0-M** — andamiaje móvil `apps/movil/**` + MSW · **P3 Legion** · Ola F0
2. **F1** — sistema de diseño, `packages/ui` congelado · Ola F1
3. **F2** — shell móvil: `ProveedorSesion`, Expo Router, `usarIdempotencia()`,
   `LimiteDeError`, biometría · carril **M** · **P3 Legion** · T3

Con los tres en `dev`, F3 es composición de once pantallas contra organismos que ya
existen, y el gate de salida de §"Gate de salida F3" de [[12 Fases F2 a F5 · App móvil]]
pasa a ser ejecutable.

## Ver también

[[12 Fases F2 a F5 · App móvil]] · [[16 Carriles de frontend]] ·
[[17 Plan de acción secuencial · coordinación de cinco máquinas]] ·
[[20 Maqueta de referencia · deltas del frontend]] ·
[[Flujo de pantallas · app del participante]]
