---
tags:
  - plan
  - saneamiento
  - microservicios
titulo: "Saneamiento del plan — huecos de la migración a microservicios"
fecha: 2026-08-18
alcance: docs/Arquitectura/* · planes/* · scripts/modelo.py · el orden de los tramos
---

# Saneamiento del plan · huecos de la migración a microservicios

> **Qué es este documento.** El resultado de la auditoría del 2026-08-18 sobre toda
> la documentación posterior al cambio de rumbo del 2026-08-16 (monolito TypeScript →
> 14 servicios Spring Boot). Cuatro pasadas: los ADR vigentes, el plan de backend
> (00–07), el plan de frontend y coordinación (10–19), y el repositorio real.
>
> **Jerarquía.** Sigue la convención de [[17 Plan de acción secuencial · coordinación de cinco máquinas]]:
> nada se aplica en silencio. Donde este documento corrige a otro, **manda este**
> hasta que el delta se aplique en el documento de origen. Los deltas mecánicos están
> en §7; las decisiones que necesitan ADR, en §1 y §8.

> [!important] Por qué ahora es barato
> El repo no tiene una sola línea de Java: `servicios/`, `plataforma/`, `apps/` y
> `despliegue/` no existen; lo que existe es la especificación (99 CU, 307 tablas,
> `sql/` aplicable, 16 esquemas ya creados en `sql/00_base/02_esquemas.sql`). Todos
> los huecos de abajo son **de papel**. Cada uno corregido antes de T0 cuesta una
> edición; después de T2 cuesta una reescritura por servicio.

## 0 · Los cinco bloqueantes

Si solo se corrige algo antes de abrir T0, que sea esto:

1. **El outbox no puede funcionar como está modelado.** ADR-018 exige `evento_dominio`
   por esquema; el modelo la pone una sola vez en el esquema `comun`, append-only y
   con `SELECT` solo para `rol_auditor`. El relevo (`SELECT … FOR UPDATE SKIP LOCKED`
   + `UPDATE`) es imposible con esos permisos. → §1.1
2. **Las sagas no existen en el plan.** La palabra "saga" no aparece en los documentos
   de fase 03, 04 ni 05; las ocho operaciones que cruzan servicios están escritas como
   transacciones ACID locales (herencia del monolito), incluida la frontera
   transaccional de CU-21 — el mismo CU que el plan usa como hito de validación de la
   partición. → §1.2 y §2
3. **Todo servicio necesita leer límites, tarifario y licencia, y su rol no puede.**
   El esquema `catalogo` implementado solo tiene `tipo_cambio` y `dia_no_habil`;
   ADR-016 además prohíbe generar clases jOOQ fuera del esquema propio. → §1.3
4. **El mapa de olas/tramos contradice once gates de entrada declarados** (las fases
   12–16 declaran `depende_de: [11]` y corren dos o tres tramos antes que la 11).
   Los gates están escritos en lenguaje de monolito ("fase cerrada"); hay que
   reescribirlos en artefactos (contrato publicado, evento definido). → §5
5. **Los servicios `erp` y `publicidad` y cuatro documentos de fase no existen**
   (18, 19, F13, F14), y el plan de `docs/implementation/` numera esas mismas fases
   con otro sistema (fase 5 de 12). Dos numeraciones paralelas para el mismo
   trabajo. → §7.9 y §9

---

## 1 · Bloqueantes de arquitectura → ADR-027 a ADR-032

Cada uno necesita un ADR nuevo **antes de T0** (son decisiones caras de revertir:
cambian la forma del código en los catorce servicios). La numeración se reserva acá
para que no vuelva a pasar lo de §6.5. La decisión recomendada está escrita con el
detalle suficiente para que redactar el ADR sea mecánico; si el dueño decide otra
cosa, el ADR lo registra igual.

### 1.1 · ADR-027 · Infraestructura de mensajería en el modelo de datos

**El hueco (contradicción C-1, la más grave del conjunto).**
- ADR-018:55 — "cada esquema tiene su tabla `evento_dominio`". Realidad:
  `docs/entidades/09_auditoria_reportes.puml` la define **una vez** y
  `scripts/modelo.py` la manda al esquema `comun` (compartido, append-only,
  `INSERT` para todos, `SELECT` solo `rol_auditor`).
- `evento_consumido` (idempotencia de consumo, ADR-018:65-67), la tabla de **estado
  de saga** (ADR-022:94) y la tabla de **ShedLock** (ADR-018:76-88) **no existen en
  ningún `.puml`** — y `Estructura del repositorio.md:170-171` prohíbe crear tablas a
  mano.
- El esquema `comun` no aparece en ningún ADR; la verificación de ADR-017:151
  ("catorce esquemas más `catalogo`") falla hoy: son dieciséis.

**Decisión recomendada.** Separar dos cosas que hoy están mezcladas:
- **Mensajería = infraestructura por esquema.** Cada esquema de servicio recibe
  cuatro tablas de infraestructura: `evento_dominio` (outbox propio, con `SELECT`,
  `INSERT` y `UPDATE` de estado para su `svc_*`), `evento_consumido (id_evento,
  consumidor)`, `estado_saga` (solo en los esquemas que orquestan, §2) y `shedlock`.
  Se agregan en `scripts/modelo.py` como **plantilla por módulo** (mismo mecanismo
  que `APPEND_ONLY`), no a mano en cada `.puml` — skill `boveda-modelo`.
- **Auditoría = lo que ya hace `comun`.** `comun.bitacora_evento` y
  `comun.registro_acceso_datos` quedan como están; el ADR documenta por fin el
  esquema `comun` y su regla de permisos.
- El `id_evento` lo genera el productor (PK del outbox) y viaja en el evento; es la
  clave de `evento_consumido`. Retención/purga de `evento_consumido`: igual al TTL de
  retención del tema correspondiente.

### 1.2 · ADR-028 · Mecánica de saga

**El hueco (C-5 + ausencia total en los planes).** ADR-022 elige saga orquestada y
promete "el estado se persiste antes de cada paso… si el proceso muere, otro la
retoma" — sin tabla, sin proceso que barra sagas atascadas, sin timeout de paso, sin
credencial para el paso disparado por evento (el relevo y los consumidores no llevan
JWT de nadie: ADR-021/022/024 dejan ese flujo sin identidad HTTP). Y ninguna fase
nombra un solo `*SagaTest` mientras el gate común los exige "por cada operación que
cruza servicios" sin enumerar cuáles son.

**Decisión recomendada.**
- El **orquestador es el servicio donde nace el hecho** (tabla de §2). Persiste en su
  `estado_saga` el paso actual **en la misma transacción** que el efecto local; los
  pasos remotos van por HTTP idempotente (clave derivada del id de saga + número de
  paso — esta es la función de derivación que ADR-021:66 invoca y nadie definió).
- **Recuperación**: un `@Scheduled` + ShedLock por servicio orquestador barre
  `estado_saga` con `edad > timeout_de_paso` y reintenta o compensa. Timeout de paso
  por omisión: 30 s; una saga que agota compensación abre **incidente operativo**
  (skill `observabilidad`) — eso instancia la alerta que ADR-022:97 promete.
- **Credencial de sistema**: para pasos sin usuario, token de cliente emitido por
  `identidad` (client credentials, `app.rol='sistema'`), con las políticas RLS del
  rol `sistema` escritas en `sql/40_reglas/` (hoy ADR-021:74-76 las promete y no
  existen).
- **Compensación = reverso, nunca `UPDATE`** (ya decidido en ADR-022:86); el ADR
  acota por escrito el alcance real de append-only: el **estado de una obligación o
  de una saga avanza por `UPDATE`**; lo que nunca se edita es el libro, los eventos y
  la bitácora. Hoy `Flujo de una transacción.md:52` dice "nada se edita" como regla
  general y la propia saga del mismo documento hace `UPDATE` — esa ambigüedad se
  cierra acá.

### 1.3 · ADR-029 · Catálogo legible por todos los servicios

**El hueco (C-9, tres puntas).** `Método de arquitectura.md:75` y `Flujo:54` exigen
que todo caso de uso de dinero verifique límite, licencia, tarifario y política
vigentes. ADR-017 promete eso en `catalogo`; `modelo.py:80-82` solo puso
`tipo_cambio` y `dia_no_habil`. Con la implementación actual, cada operación de
dinero necesitaría una llamada HTTP a `tarifas`/`cumplimiento` **dentro** de la
transacción — prohibida por ADR-022:65-67. Y ADR-016 (codegen limitado al esquema
propio) impide hasta generar las clases de `catalogo`.

**Decisión recomendada.**
- Mover a `catalogo` las tablas que son **parámetro leído por todos y escrito por
  procesos administrativos**: umbrales UIF, límites por nivel, calendario hábil,
  licencia vigente, tarifario vigente (la lista exacta sale de los seeders de
  `seeders/minimos/` — son justamente los 20 catálogos).
- `svc_*` recibe `SELECT` sobre `catalogo`; la escritura queda en el servicio dueño
  del ciclo administrativo (`cumplimiento`, `tarifas`) vía su esquema o vía rol
  aparte — el ADR lo fija.
- ADR-016 se ajusta: el `includes` de generación de cada servicio es
  **su esquema + `catalogo` (solo lectura) + sus tablas de §1.1**.

### 1.4 · ADR-030 · Revocación de sesión y validación de respaldo

**El hueco (C-6 + fallback de ADR-024).** ADR-024:138 exige que un `jti` revocado
deje de aceptarse "en menos de un segundo"; la única vía de publicación permitida es
el outbox (ADR-018:50-53), cuya latencia es "del orden del intervalo del relevo" y
explícitamente **no se acelera**. Además el arranque en frío "pide validación a
`identidad`" contra un endpoint que no existe en ninguna spec.

**Decisión recomendada.** Declarar la **excepción única y nombrada**: la lista de
revocación se publica **directo a Kafka en un tema compactado** (fuera del outbox,
porque no es un hecho de negocio sino seguridad), con TTL igual al del token; el
endpoint interno `GET /sesion/validez/{jti}` se agrega al contrato de `identidad`
para el arranque en frío. El criterio de verificación pasa a "≤ 5 s de propagación" —
medible y honesto con un token de acceso de 15 minutos.

### 1.5 · ADR-031 · Lecturas, réplica y `rol_auditor` — supera a ADR-011

**El hueco (C-3 + C-11).** ADR-017 declara a `auditoria` como **única** excepción de
lectura cruzada; ADR-021:85 abre el `DataSource` con `rol_auditor` a "listados
pesados" de cualquier servicio, y ADR-011 (vigente pero pre-migración: habla del
worker, cuenta 274 tablas) se la da también a `cumplimiento`. Nadie decidió si
`rol_auditor` tiene `BYPASSRLS`: si lo tiene, la RLS no protege la mitad de las
lecturas; si no, los reportes regulatorios salen incompletos.

**Decisión recomendada.** ADR-031 supera a ADR-011 y fija: (a) excepciones de lectura
cruzada **enumeradas** — `auditoria` y `cumplimiento`, nadie más; un "listado pesado"
de otro servicio se resuelve con proyección propia o pidiendo el reporte a
`auditoria`; (b) `rol_auditor` **con** `BYPASSRLS`, compensado porque solo dos
servicios lo reciben, toda consulta pasa por `registro_acceso_datos` con
justificación (los CHECK de la auditoría de robustez), y solo contra la réplica;
(c) rezago máximo de réplica como número con alerta.

### 1.6 · ADR-032 · Aplicación del esquema — un solo mecanismo

**El hueco (C-10).** Tres documentos vigentes describen tres mecanismos: Flyway
aplica `sql/` (ADR-016:42), `Job` de Flyway (ADR-025:94), `psql -f sql/aplicar.sql`
(`Entornos:68-72`). Flyway asume archivos inmutables con checksum; `sql/` **se
regenera entero** desde los `.puml`, así que los checksums se rompen por diseño.

**Decisión recomendada.** `psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql` es el único
mecanismo, empaquetado como imagen de migración (el `Job` de ADR-025 la ejecuta con
`rol_migracion`); Flyway queda descartado por incompatible con el esquema generado.
`aplicar.sql` ya es idempotente-por-reconstrucción en entornos efímeros; para
producción, el ADR define el procedimiento de **diferencia**: el cambio de modelo
genera, junto al `sql/` nuevo, el script de migración del estado N al N+1 (tarea del
troncal, `07 §12`: cambio de modelo = para todo el parque).

---

## 2 · El inventario de sagas que ningún documento tenía

`00 §9` exige `sagaTest` "por cada operación que cruza servicios" y ADR-026:130 pide
probar "solo los pares que se llaman" — y ninguna lista existía. Esta es. La columna
"patrón" distingue lo que es **saga orquestada** (respuesta necesaria, dinero en
vuelo) de lo que es **evento** (el otro servicio reacciona después) y de lo que es
**llamada sincrónica previa** (verificación antes de abrir la transacción local).

| # | Operación | CU | Servicios | Patrón | Orquestador | Compensación | Prueba |
| :-: | --- | :-: | --- | --- | --- | --- | --- |
| S1 | Cobrar aporte | 21 | `aportes` → `nucleo-financiero` (crédito + asiento) → `tarifas` (devengo) | **saga** | `aportes` | reverso del crédito; obligación vuelve a PENDIENTE | `CU21CobrarAporteSagaTest` |
| S2 | Liquidar entrega | 22, 28 | `entregas` → `nucleo-financiero` (débito + asiento) → `tarifas` (devengo) | **saga** | `entregas` | reverso del débito; entrega vuelve a APROBADA | `CU22LiquidarEntregaSagaTest` |
| S3 | Ejecutar cobertura de garantía | 23, 25 | `garantia` → `nucleo-financiero` (movimiento + asiento) | **saga** | `garantia` | reverso; cobertura queda FALLIDA con incidencia | `CU23CoberturaSagaTest` |
| S4 | Alta de usuario abre billetera | 01 | `identidad` ⇒ `nucleo-financiero` | **evento** (`identidad.usuario_creado`) | — | no hay: la cuenta se abre al consumir; reintento idempotente | consumo duplicado/fuera de orden |
| S5 | Grupo abre su cuenta | 20 | `grupos` ⇒ `nucleo-financiero` | **evento** (`grupos.grupo_activado`) | — | ídem S4; el grupo no opera dinero hasta el evento de vuelta | ídem |
| S6 | Asiento del hecho económico | 24 | cualquiera ⇒ `nucleo-financiero` | **dentro de S1–S3** para dinero; **evento** para hechos sin movimiento | — | la del paso que lo contiene | cuadre: todo hecho tiene asiento |
| S7 | Devengar comisión | 31 | `tarifas` ⇐ evento del hecho (`aporte_pagado`, `entrega_liquidada`) | **evento**; el **cargo** posterior es saga corta `tarifas` → `nucleo-financiero` | `tarifas` | reverso del cargo + nota de crédito | `CU31DevengoSagaTest` |
| S8 | Verificar límite/licencia | 40, 46 | dinero → `catalogo` | **lectura local de catálogo** (§1.3) — deja de ser llamada | — | no aplica: si no hay dato vigente, se **rechaza** | tope `NULL` ⇒ rechazo |
| S9 | Registrar PCC-01 / ROG | 41, 42 | dinero ⇒ `cumplimiento` | **evento** post-commit (el umbral se evalúa contra `catalogo` local; el registro lo escribe `cumplimiento` al consumir) | — | no aplica (append-only) | acumulado con eventos duplicados |

**Nota sobre lo que la fusión de módulos ya resolvió.** ADR-014 metió billetera y
libro contable en `nucleo-financiero`; por eso movimiento + asiento van juntos en una
transacción local dentro de S1–S3 y **no** son un salto más. Las ocho "transacciones
imposibles" detectadas en 03/04/05 se reducen a estas nueve filas.

**Regla de reescritura (delta a 03, 04 y 05).** Las tablas de frontera transaccional
de CU-01, CU-20, CU-21, CU-22, CU-24, CU-31, CU-40/46 y CU-41/42, y los textos "se
ejecuta dentro de la transacción de la operación que lo invoca", se reescriben según
esta tabla. La **sexta pregunta** de frontera transaccional ("¿esto cruza a otro
servicio y qué pasa si el otro falla?") vuelve a los cinco documentos operativos que
la perdieron (`00c §2`, `00b §12`, y las cabeceras de 03/04/05) — hoy solo vive en
`00 §12`.

**FK cruzadas y orden de pasos (C-8).** Una FK de `aportes` hacia `nucleo_financiero`
obliga a que la fila remota exista **antes** del `COMMIT` local: el orden de pasos de
cada saga de arriba está elegido para respetarlo (primero el efecto en el servicio
referenciado, después el local). El trabajo pendiente de modelo: enumerar cuántas de
las 633 FK cruzan esquema (`scripts/` puede derivarlo del `.puml`) y verificar cada
saga contra esa lista — entra en el gate de S4 (§10).

---

## 3 · Piezas compartidas: dueño y destino

Diez piezas aparecen pedidas en dos o tres servicios sin dueño — incluida
`calcularPlazoHabil`, que `07 §6` usa como ejemplo de lo que no debe pasar, y
`compilarExpresion`, que `05 §16.B` ordena no duplicar. Resolución:

| Pieza | Pedida por | Destino y dueño | Cuándo |
| --- | --- | --- | --- |
| `PlazoHabil` / `sumarDiasHabiles` | grupos, garantía, organizador, cumplimiento | **ya está en `comun-dominio`** (átomo de F1, `02 §1.1`) — los documentos de fase deben referenciarlo, no re-crearlo | T1 |
| `barajarDeterminista` · `verificarCompromiso` · `serializarCanonico` · `hashDeBloque` | grupos (F8), transparencia (F13), sitio (F9), móvil (F5) | `plataforma/comun-dominio` (Java) **+ puerto TypeScript en `packages/dominio-cliente`** con **vectores dorados**: JSON de casos generado por la prueba Java, versionado, consumido por la prueba TS. Sin esto, "el cliente recomputa y coincide" (gate F9) no es verificable | Java: T1 (P1) · TS: T1 (P3, F0-M) |
| `compilarExpresion` + `SimuladorDeReglas` (hoy con dos nombres: `compilarCondicion` en 14) | organizador (F14), cumplimiento (F16) | módulo nuevo `plataforma/comun-reglas`; nombre único `compilarExpresion`; micro-PR lo abre **2E al inicio de T4**, 3C lo consume en T5 | T4 |
| `RegistroDeSalud` + `elegirProveedor` | notificaciones (F12), aportes (F9), fase 17 | módulo `plataforma/comun-proveedores` (patrón de la skill `proveedores-externos`); micro-PR lo abre **1D en T2** (primer usuario), 3A lo consume en T4 | T2 |
| `AlmacenArchivos` (puerto) | nucleo-financiero, garantía, auditoría | `comun-dominio` (puerto) + adaptador local en `comun-datos`; **entra al gate de salida de F2**, que hoy no lo entrega. Las referencias a "Multer" (03, 05, 06) se borran: no existe en la bóveda | T1 |
| `CuentaBilleteraRepositorio` | identidad, grupos, nucleo-financiero | **desaparece de identidad y grupos**: S4/S5 lo vuelven innecesario. Solo `nucleo-financiero` lo tiene | — |
| `SaldoDiarioRepositorio` | nucleo-financiero, aportes | solo `nucleo-financiero`; el sellado por hash del cierre (F9 lo pedía) es del cierre diario de `nucleo-financiero`, `aportes` consume el evento | T3 |
| `ObligacionRepositorio` en notificaciones | notificaciones (CU-81) | **desaparece**: `aportes` emite `aportes.obligacion_por_vencer` (lo programa su propio ShedLock); `notificaciones` solo renderiza y envía. CU-81 se implementa en F12 contra el **contrato del evento** con dobles; la integración real es deuda declarada que se paga en T4 | T2 (contrato) |
| `esParteInteresada` / `computarVotacion` | grupos (CU-63), cumplimiento (CU-94) | `comun-dominio` por micro-PR de **2C (T3)**, primer usuario | T3 |
| Opciones del generador OpenAPI / jOOQ | los catorce | `plataforma/` (convención Gradle), se fijan en T0 y son micro-PR después — se agregan al mapa de propiedad de `Estructura del repositorio.md §` | T0 |

### 3.1 · El gateway: la regla que faltaba

El registro de rutas es `app.module.ts` reaparecido: `07 §0` lo da por eliminado y es
falso — el gateway necesita conocer los 14 prefijos y vive en `plataforma/**`,
congelado y de solo lectura para los carriles. Resolución:

- El gateway **compone su tabla de rutas desde un archivo por servicio**:
  `plataforma/gateway/src/main/resources/rutas/<servicio>.yml`.
- Ese archivo es **propiedad del carril dueño del servicio** — se agrega como
  excepción a la tabla de propiedad de `07 §4`, igual que el fragmento de compose.
- Una **prueba de barrido** (se suma a las quince de `19 §5`) verifica que ningún
  prefijo esté repetido y que todo prefijo esté en la lista reservada de `19 §3.1`.
- Crear la ruta es **paso del generador `nuevoServicio`**, así no depende de memoria.
- Lo mismo aplica al fragmento `despliegue/compose/<servicio>.yml`: lo crea
  `nuevoServicio`, lo posee el carril. Con esto, el gateway se entrega **una** vez
  (F0 §0.7 — la mención duplicada en F2 §2.3 se marca como referencia, delta §7.2).

---

## 4 · Contratos: el mapa de pares y la obligación por tramo

El gate común exige `contractTest` "por cada par que se llama" sin enumerar los
pares. Del inventario de §2 más las lecturas conocidas, los pares HTTP sincrónicos
son estos (dirección = quién llama a quién):

| Consumidor → Productor | Motivo | Contrato debe estar en `dev` al abrir |
| --- | --- | :-: |
| todos → `identidad` | JWKS, validez de sesión (§1.4) | T1 (delta 1 ya lo adelanta) |
| `aportes` → `nucleo-financiero` | S1: crédito + asiento | T3 |
| `aportes` → `tarifas` | S1: devengo/cotización | T3 |
| `entregas` → `nucleo-financiero` | S2 | T4 |
| `entregas` → `tarifas` | S2 | T4 |
| `garantia` → `nucleo-financiero` | S3 | T4 |
| `tarifas` → `nucleo-financiero` | S7: cargo | T2 |
| `garantia` → `grupos` | cupos, turnos, estado del grupo | T4 |
| `transparencia` → `grupos` | verificación de sorteo | T3 |
| backoffice/app → todos | por el gateway | según fase frontend |

Todo lo demás es **evento**, y los eventos también son contrato: el esquema de carga
de cada tema (`identidad.usuario_creado`, `grupos.grupo_activado`,
`aportes.aporte_pagado`, `aportes.obligacion_por_vencer`,
`entregas.entrega_liquidada`, revocación de sesión) se declara en el ADR-033 (§8) y
se versiona con el servicio productor.

**Casilla de gate nueva (delta a `00 §9`):** "El contrato OpenAPI de mi servicio y el
esquema de mis eventos están publicados en `dev` **antes de abrir el tramo** en que
otro carril los consume (tabla §4 de 20)". Hoy la obligación existe solo en `07 §7` y
se verifica después, no antes.

---

## 5 · El orden corregido: gates en artefactos, no en "fase cerrada"

**El defecto.** Once gates de entrada dicen "Fase N cerrada" y el mapa de tramos los
viola (F4 corre con F3 en T2; F7 con F6 en T3; las fases 12–16 declaran
`depende_de: [11]` y corren en T2–T5 mientras la 11 corre en T5). Esos gates son
lenguaje de monolito: cuando todo vivía en un proceso, "fase cerrada" era la única
forma de dependencia. Con servicios separados, la dependencia real es **el contrato,
el evento o el dato — no el código del otro**.

**La corrección.** Los tramos de `17 §5` **quedan como están** (el reparto por
máquina es correcto); lo que se reescribe son los gates de entrada de 03/04/05:

| Fase | Gate de entrada viejo | Gate de entrada corregido |
| :-: | --- | --- |
| 4 (`cumplimiento` parcial) | "Fase 3 cerrada" | Contrato de `identidad` en `dev` (T1) · semillas de límites cargadas |
| 5 (libro) | "Fase 4 cerrada" | Fases 1–2 cerradas; nada más — el libro no depende de habilitación |
| 6 (billetera) | "Fase 5 cerrada" *(ya era así)* | Igual, **más CU-40/46 disponibles** (F4 cerró en T2 ✓) |
| 7 (`tarifas`) | "Fases 5 y 6 cerradas" | Fase 5 cerrada · contrato de billetera publicado (el cargo real de S7 se prueba al cerrar T3) |
| 8 (`grupos`) | "Fase 7 cerrada + hito CU-31" | Contratos de billetera y tarifas publicados. **Riesgo aceptado**: F8 corre antes del hito CU-31; si el hito falla, F8 retrabaja — mitigado por el hito nuevo de T2 (abajo) |
| 9 (`aportes`) | "Fases 3–7 cerradas" | Fase 8 cerrada (obligaciones y períodos) · infraestructura de §1.1 en `dev` |
| 10a (`entregas` CU-18) | "Fase 9 cerrada" | Contrato de `aportes` + evento `aporte_pagado` definidos (día 1 de T4, `07 §7`). El recorrido con fondo real es parte del gate de **salida** de 10b, no del de entrada de 10a |
| 11 (`garantia`) | "Fase 10 cerrada" | Fase 9 cerrada · contrato de entregas publicado |
| 12–16 | "Fase 11 cerrada" (`depende_de: [11]`) | **Se elimina la dependencia con 11** — era del orden del monolito. Cada una declara sus dependencias reales: F12 → eventos notificables definidos; F13 → F8 cerrada; F14 → nada del backend de dinero; F15 → bitácora de F2; F16 → F4 cerrada + `comun-reglas` (§3) |
| 17 | "Fases 12–16 cerradas" | Igual — esta sí es convergencia real |

**Hito nuevo (delta a `00 §8`).** Al cerrar **T2**: `CU-24` punta a punta en
`nucleo-financiero` — valida el stack (Spring + jOOQ + RLS + outbox) **antes** de que
T3 abra tres servicios nuevos sobre él. El hito CU-31 (cierre de T3) valida la
partición con la primera saga real de dinero; el de CU-21 (T4) valida la saga
completa de tres servicios. Tres hitos escalonados en lugar de dos tardíos.

---

## 6 · Frontend: las correcciones de desalineación

1. **Base URL y sesión — la decisión implícita se escribe.** El cliente (app,
   backoffice, sitio) apunta a **una sola base URL: el gateway**; el prefijo enruta
   (`19 §3.1`). El refresh va a `identidad` vía gateway; un `401` en cualquier
   servicio dispara **un** intento de refresh y reintento — si falla, sesión cerrada
   global. Se agrega como sección al `10 Plan maestro del frontend §3` y como
   responsabilidad del `ProveedorSesion` (F2). **CORS se configura en el gateway**
   para los orígenes del backoffice y el sitio — hoy ninguna pieza del corpus lo
   menciona y hay dos clientes de navegador.
2. **Un solo nombre para el artefacto de contratos:** `clientes/typescript/`,
   generado, no se edita, **sin dueño de carril** (lo regenera quien corre
   `generateOpenApiClients`; los conflictos no existen porque no se versiona a mano).
   `packages/contratos` (17 §4 delta 1, 18 §T0, 18 §F0-M, 11 §gate F0 "0.6b") y
   `packages/cliente-api` con dueño por CU (16 §4) **se corrigen a ese nombre**. El
   delta 1 de 17 queda: "los tres contratos OpenAPI base se adelantan al cierre de
   Fase 0 **y su cliente TS generado se publica en `dev`**".
3. **El solape de CU deja de ser conflicto** con el punto 2 (nadie posee archivos
   generados). Lo que sí se reparte: **`pruebas/mocks/` se organiza por servicio, no
   por dominio de carril** (`mocks/identidad/CU01.ts`), y el handler de un CU lo crea
   el **primer carril que lo necesita**; el segundo lo importa. Prueba de barrido
   nueva: un CU no tiene dos handlers.
4. **Átomos criptográficos en TS**: resuelto en §3 (vectores dorados). La frase de
   `14 §F9.3` "se importan de `plataforma/comun-dominio` — los mismos que usa el
   backend" es **imposible** (es Java) y se reescribe.
5. **Renumeración de ADR**: los "ADR-018 Sitio público" y "ADR-019 Rastreadores de
   IA" que exige el gate de F0 (`10 §1`, `11 §F0.5`) colisionan con ADR aceptados del
   backend y harían fallar `verificar_boveda.py`. Pasan a ser **ADR-037 y ADR-038**
   (la serie 027–036 queda reservada por este documento).
6. **El aparato de verificación de frontend se escribe** (hoy `19` es 100 % Gradle):
   checklist de cierre de carril frontend (`yarn lint && yarn typecheck && yarn
   test:front && yarn test:a11y`, presupuestos), pasos de CI de frontend en el
   pipeline formal (`test:front`, `test:a11y`, Lighthouse, `seo:validar`), skills
   propias para F2 y F6 en la tabla de `19 §2`, y dónde corre Lighthouse/medición
   (P5 la corre; P2 solo publica el número en el informe — `19 §6` decía P2, que no
   toca frontend en ningún tramo).
7. **Presupuesto de JS del sitio**: manda `19 §6` (≤150 KB, bloqueante en CI); los
   50 KB de `14 §F10.4` quedan como **objetivo** de páginas de contenido, no gate.
8. **E2E contra API real (F12)**: el "entorno de ensayo" que el gate de entrada de
   F12 exige queda **definido**: es el despliegue que P2 hace en T8 (17 §5), con
   nombre `ensayo`, URL interna del parque, y su creación es entregable de la fase 17
   §17.7. Maestro corre en Legion **contra ese entorno remoto**, no contra un compose
   local — eso resuelve que ni Mac ni Legion puedan levantar el stack completo.

---

## 7 · Deltas mecánicos por documento

Correcciones sin decisión nueva: restos del monolito y conteos. Se aplican en la
pasada S2 (§10); hasta entonces, **si un texto de abajo contradice a este documento,
gana este**.

### 7.1 · Restos del stack viejo a purgar

| Doc | Qué corregir |
| --- | --- |
| `00b` | `utils.ts`→`Utils.java` (§3) · `const` JS (§4) · bloque ```` ```ts ```` (§5) · `USER node`→`USER app` (§8) · rango `^` npm (§7) · `.env` en gitignore vs "no existe `.env.example`" (§11 vs `00 §10`) · `console.log`/`any`/`eslint-disable` (§12–13) · `it()`→`@Test`+`@DisplayName` (§9) · enlace a ADR-009 superado → ADR-023 |
| `00c` | **§2: el formato obligatorio de descomposición usa `.ts` — pasa a `.java`** · "worker" (§2, §5, §8, §10) → consumidor/ShedLock del servicio · **§9: el ejemplo canónico de cron es sintaxis de Graphile Worker** → `@Scheduled`+`@SchedulerLock` · tabla de §11 con columna vacía · `packages/dominio` → `plataforma/comun-dominio` (§14) · `await`/`any` (§15) · **agregar la sección que falta: la receta de saga** (firma del orquestador, paso compensable, `estado_saga`) — es el doc que promete que implementar sea mecánico y no tiene la pieza nueva más difícil |
| `03` | capa `http/` → `web/` (3 veces — ArchUnit no reconocería `http/`) · `@Publico()`/`SesionGuard` NestJS → equivalente Spring Security del `00c` · `redact` de Pino → Logback (gate F3) · "Trabajos del worker" · leer ADR-010 → ADR-024 · **§F5: el contrato de consumo del libro está en TypeScript (`await`, `tx`, `fast-check`, `Decimal`)** → Java (`DSLContext`, jqwik, `BigDecimal`) · "Multer local, ADR-017" → puerto `AlmacenArchivos` (§3) · fronteras transaccionales según §2 |
| `04` | "dos réplicas del worker" → dos réplicas del servicio `aportes` · `it()` → JUnit · **§9.2: la frontera de CU-21 se reescribe como saga S1** · §8.2 cuenta del grupo → S5 · §10.2 devengo → S2/S7 · §11.B asiento → S3 |
| `05` | `@Publico()` · "UUID del worker" · `BD_URL_LECTURA` → `aportaya.datasource.lectura.url` · Multer · §16.A PCC/ROG → S9 · nombre único `compilarExpresion` (§3) |
| `06` | **El doc menos migrado: se reescribe la §17.7 entera** — el "orden del despliegue no negociable" es el del monolito (migración → semillas → api → worker); pasa a: migración (ADR-032) → semillas (**20** catálogos, no 15) → **los 14 servicios por orden de dependencia (identidad → nucleo-financiero → resto) → gateway** → verificación. Además: autocannon→k6 · Pino→Logback · `X-Powered-By` (Express) · `/salud` → `/actuator/health/*` · "cinco suites"→seis corredores · `eslint-disable`/`number` → Java · ADR 012/003/002/007 citados → 025/018/016/021 · "packages/contratos ya los habilita" → `clientes/typescript` · "generateOpenApiClients sin diff vacío" → **con** diff vacío (y solo aplicable si se versiona el generado — no se versiona: la casilla se reformula como "regenerar no cambia nada que esté versionado") |
| `10`, `11` | "valida contra el Zod real" (3 veces) → "valida contra el esquema del contrato OpenAPI" · gate F0 cita "Fase 0 §0.6b"/`packages/contratos` → `01 §0.8`/`clientes/typescript` · ADR-006/005/009/012 citados → vigentes · ADR-018/019 propios → **ADR-037/038** (§6.5) |
| `14` | §F9.3 átomos "compartidos con el backend" → vectores dorados (§3) · 50 KB → objetivo, gate en `19 §6` (§6.7) |
| `16` | aplicar deltas 2 y 3 de 17 en el cuerpo (hoy solo en la cabecera) · `packages/cliente-api` por CU → `clientes/typescript` generado (§6.2–6.3) · 12 carriles → 17 |
| `18` | §T0: "corredores de Jest" → los cinco de JUnit · `apps/api`/`apps/worker` → no existen · §T2 "glob de `modulos/**/*.module.ts`" → descubrimiento Gradle por barrido · §1B `modulos/03_*` → `servicios/nucleo-financiero` y `servicios/aportes` · §F0-W `apps/sitio` → `apps/web` · compose de T0 sin Kafka → con Kafka · §F0-M posee `packages/cliente-api` → nadie posee generado |
| `19` | §7.2 Pino → Logback estructurado · §2: skills para F2 y F6 · §5: +2 barridos (rutas de gateway §3.1, mocks sin duplicar §6.3) · §8 y §10: variante frontend (§6.6) |
| `informes/_plantilla.md` | rama `carril/<ola>-…` → `<usuario>/feature/carril-<id>` (delta 3) · gate mezclado yarn/Java → dos variantes, una por tipo de carril |

### 7.2 · Conteos y coherencia interna

| Incoherencia | Valor correcto |
| --- | --- |
| "21 fases" / "20 fases" / tabla de 18 | **21**: 0–17 backend (18) + fases 18 y 19 = 20 de backend… la cifra real es **20 de backend + su tabla actualizada**; `18 §Índice` ya cuenta bien 21 fichas de backend (T0 se parte). Se corrige `00 §8` agregando las filas 18 y 19 |
| "las seis pruebas obligatorias" vs "siete" | **7** — la séptima es la compensación de saga, justo la nueva |
| "las cinco preguntas de frontera" vs "seis" | **6** — la sexta es la de cruzar servicios (§2) |
| "doce pruebas de barrido" vs "quince" | **15** (+2 de este doc = **17**) |
| "87 casos de uso" | **99** (ya en `17 §11.5`; falta aplicarlo en `05 §gate F16`, `06 §37`, `19 §4`) |
| "15 catálogos" en 06 §17.7 | **20** |
| gateway entregado en F0 §0.7 **y** F2 §2.3 | se entrega en **F0**; F2 §2.3 pasa a referencia |
| `cumplimiento` y `auditoria` con dos tramos sin la protección de `nucleo-financiero` | se agregan a la lista de `07 §12`: "los dos tramos de un mismo servicio nunca corren a la vez" — vale para los tres |
| tres docs mandan escribir en `planes/informe.md` compartido (03 §F7, 06 §17.2, §17.8) | → `planes/informes/carril-P<N>.md`; `informe.md` lo escribe solo el guardián |
| casilla "grep en CI" para `cuenta_billetera.saldo` | se reemplaza por **regla de ArchUnit**: nadie salvo el módulo de saldo de `nucleo-financiero` referencia la columna generada |
| casilla "las 307 tablas tienen código que las escribe" | verificable recién con fases 18/19; el verificador es un script sobre las clases jOOQ usadas (se encarga a `verificar_criterios.py`) |
| `README.md` (119 reglas, 69 humo, 12 módulos) · `docs/Index.md` (36 CU en el árbol) · `Auditoria-Robustez` (cifras pre-M13/M14) | 138 · 151 · 14 · 99; y la auditoría de robustez se anota como **pendiente de extender a las 33 tablas de M13/M14** (sus políticas RLS no fueron contadas ni escritas) |
| `informe.md` frontmatter `actualizado: 2026-08-13` | fecha real de última edición |

---

## 8 · Temas sin decisión — la cola de ADR después de T0

No bloquean T0, pero cada uno tiene un tramo límite: pasada esa fecha, decidirlo
cuesta retrabajo. Ordenados por fecha límite:

| ADR | Tema | Qué falta exactamente | Límite |
| :-: | --- | --- | :-: |
| 033 | **Contrato de eventos** | Formato de carga (JSON con `version` explícita), regla de compatibilidad (solo campos nuevos opcionales), dónde vive el esquema (junto al OpenAPI del productor), prueba de compatibilidad en CI del productor. Hoy un productor puede romper a sus consumidores en silencio — el contrato duro existe para HTTP y no para Kafka | **T1** (antes del primer consumidor) |
| 034 | **Observabilidad distribuida** | Elegir: `traceparent` W3C (lo que OpenTelemetry propaga solo) con `x-request-id` como alias en el borde; backend de trazas y logs con nombre (en local: Grafana/Tempo/Loki en el perfil `todo`); correlación a través de Kafka como cabecera del mensaje, no en la carga; umbrales de las métricas que `Entornos §132` lista sin número | **T1** |
| 035 | **Gestión de secretos** | Gestor con nombre, custodia y rotación de la clave RS256 (su pérdida invalida todas las sesiones y hoy nadie la respalda), credenciales de base por servicio, qué ve el CI | **T2** |
| 036 | **Continuidad de Kafka y restauración parcial** | Kafka quedó fuera de ADR-013 aunque la reproducibilidad de eventos fue el argumento para elegirlo; retención por tema con número legal; y el efecto no dicho del clúster único: **un PITR restaura los catorce servicios a la vez** — procedimiento para recuperar el daño de uno solo (reproceso de eventos desde el outbox, no PITR global) | **T5** (antes de fase 17) |
| — (delta a 022/025) | **Presupuesto de respuesta end-to-end** | El número que tres documentos invocan y nadie definió: p. ej. 2 s de presupuesto total, timeout por salto = presupuesto restante propagado en cabecera, reintentos descuentan. Sin esto "máximo dos saltos + 2 s por llamada + reintentos" multiplica al peor caso muy por encima de cualquier presupuesto | **T2** |
| — (delta a 022) | **Rate limit con estado** | El límite "por identidad y por IP" necesita almacén compartido que el stack no tiene: o Redis entra al stack (ADR), o el límite es por instancia y se declara así | **T5** |
| — (delta a 025) | **Despliegue y rollback** | Orden Job→14 imágenes con qué pasa si una falla; verificación post-despliegue; `NetworkPolicy` (hoy "no publica puerto" no impide tráfico este-oeste); registro de imágenes | **T5** |
| — (delta a 018) | **Kafka operativo** | Particiones/replicación/retención por tema, `consumer group` por servicio, **ACL** (hoy cualquier servicio puede consumir cualquier tema: la frontera de datos existe en la base y no en el bus), quién crea temas (declarativo en `nuevoServicio`), reintentos/retroceso antes de DLQ y procedimiento con autorización para reprocesar | **T2** |
| — (delta a 026) | **Prueba de carga como nivel** | La fase 17 mide contra presupuestos que ningún corredor produce; k6 con umbrales entra como séptimo corredor en P2 | **T5** |
| — | **Backpressure** | Concurrencia máxima por consumidor, cola acotada, shedding en gateway | T5 |
| — | **Datos personales en eventos y copias** | Borrado del titular vs topics retenidos y copias locales por evento (ADR-017:92 lo menciona en una celda y no lo resuelve: esquema donde vive la copia, backfill, rezago, reconciliación) | T5 |
| — | **Config distribuida** | Hoy cambiar un timeout = redesplegar. Aceptarlo por escrito (es defendible con 14 servicios y un operador) o elegir refresco. Decisión chica; escribirla evita que cada carril invente | T3 |

---

## 9 · Lo que decide el dueño, no la técnica

Tres decisiones abiertas que ningún ADR puede cerrar solo:

1. **¿La fase 18 (ERP contable) se adelanta a T5?** Si llevar libros formales
   (CU-100, Ley 393 + Código de Comercio) es condición de la **licencia** y no
   obligación que empieza con la operación, 18 desplaza a 3C en T5 (`17 §1` y §11.4).
   Es investigación de la skill `norma-nueva`, no de código — y es **la única** de
   las tres con fecha dura: hay que responderla antes de T5.
2. **Retención legal de eventos** (alimenta el ADR-036): cuántos años de
   reproducibilidad exige la conservación de libros y la UIF.
3. **La ventana de revocación de 15 minutos** ante el supervisor (ADR-024:106 dice
   "vuelve la introspección si resulta inaceptable para el supervisor" — nadie tiene
   la tarea de averiguarlo; queda asignada acá, sin fecha dura, antes de la solicitud
   de licencia).

Además, la **colisión de planes para M13/M14**: `docs/implementation/` lleva "fase 5
de 12" para el portal administrativo + ERP + publicidad con su propia numeración,
mientras este plan los pone en fases 18/19/F13/F14 (tramos T8–T9). Resolución
recomendada: `docs/implementation/plan-de-implementacion.md` se **subordina** — sus
fases 5–12 se convierten en el contenido de los cuatro documentos de fase que faltan
y el archivo lo dice en su cabecera (además de purgar sus "contrato Zod" y el
`apps/backoffice` "existente" que no existe).

---

## 10 · Orden de ejecución del saneamiento

Extiende el §11 de [[17 Plan de acción secuencial · coordinación de cinco máquinas]].
Todo esto es **anterior a T0** y es trabajo de documentación en el asiento — una
máquina, con las skills `decisiones-adr`, `boveda-modelo` y `norma-nueva`. Estimación
honesta: dos a tres días de atención; se paga una vez y evita retrabajo en 38
carriles.

| # | Paso | Produce | Gate |
| :-: | --- | --- | --- |
| S1 | Escribir **ADR-027 a 032** (§1) — 031 supera a 011 | 6 ADR en `docs/Arquitectura/` | `verificar_boveda.py` pasa; `_Arquitectura.md` actualizado |
| S2 | Aplicar los **deltas mecánicos** (§7) a 00b, 00c, 03–06, 10, 11, 14, 16, 18, 19, plantilla, README, Index | docs sin restos del monolito | `grep -riE 'worker|pino|multer|zod|\.ts\b|eslint|it\(' planes/ docs/Arquitectura/` limpio (salvo expediente marcado) |
| S3 | Reescribir las **fronteras transaccionales** de los 9 CU de §2 y restaurar la sexta pregunta | tablas de saga en 03/04/05 · receta de saga en 00c | cada saga de §2 tiene su `*SagaTest` nombrado en su fase |
| S4 | **Modelo**: tablas de §1.1 vía `modelo.py` + catálogo de §1.3 + enumerar FK cruzadas · regenerar bóveda y `sql/` | `sql/` regenerado, 16 esquemas documentados | `aplicar.sql` en limpio · humo pasa · FK cruzadas listadas y cruzadas contra §2 |
| S5 | Escribir los **4 documentos de fase** (18, 19, F13, F14) absorbiendo `docs/implementation/` (§9) | 4 docs con el formato de los demás | fichas 5A/5B/F13/F14 de `18` dejan de decir "no existe todavía" |
| S6 | **Gates corregidos** (§5) + hito CU-24 + casilla de contratos (§4) + propiedad del gateway y compose (§3.1) en `07 §4` | 03/04/05/00/07 coherentes con `17 §5` | ningún gate de entrada nombra una fase que corre en su mismo tramo |
| S7 | **Frontend** (§6): base URL/sesión/CORS, un solo nombre de contratos, mocks por servicio, checklist y CI de frontend, ADR-037/038 | 10/11/16/19 corregidos | los 17 carriles de frontend tienen checklist de cierre ejecutable |
| S8 | Los pasos ya listados en `17 §11`: topología, skills, informes por puesto, conteo 99 | — | — |
| S9 | **Commitear el estado**: hoy la migración entera (13 ADR, planes 17–19, `02_esquemas.sql`, skills renombradas) vive sin trackear en el working tree — 550 archivos. Un accidente de `git clean` borra la migración | commits por tema en `main` | `git status` limpio |
| S10 | Abrir **T0** | — | gate de entrada de Fase 0 |

Los ADR de la cola (§8) se escriben en el tramo que su límite indica, por el carril
que primero los necesita, como micro-PR de documentación.

## Ver también

[[17 Plan de acción secuencial · coordinación de cinco máquinas]] ·
[[00 Plan maestro]] · [[07 Carriles de trabajo concurrente]] ·
[[19 Contrato de carril · conflicto cero, skills y calidad verificada]] ·
[[_Arquitectura]] · `decisiones-adr` · `boveda-modelo` · `frontera-transaccional` ·
`servicios-y-sagas` · `plan-por-fases`
