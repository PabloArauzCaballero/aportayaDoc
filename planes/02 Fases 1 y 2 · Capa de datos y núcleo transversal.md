---
tags:
  - plan
  - fase
titulo: "Fases 1 y 2 — Capa de datos y núcleo transversal"
fases: [1, 2]
depende_de: [0]
habilita: [3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17]
---

# Fases 1 y 2 — Capa de datos y núcleo transversal

> **Por qué van juntas en un documento.** La Fase 1 construye las piezas que los
> catorce servicios comparten (dinero, tiempo, esquemas, acceso a datos, contexto de
> sesión); la Fase 2 las ensambla en un pipeline HTTP, un outbox y un gateway que
> funcionan. Son dos gates distintos, pero un solo cuerpo de decisiones: si algo sale
> mal acá, sale mal en las 15 fases siguientes multiplicado por 99 casos de uso **y
> por catorce servicios**.

> **Se ejecuta en:** Ola 0 · carril T (troncal, máquina única). **Ningún otro carril
> trabaja hasta que su gate esté ejecutado.** Ver [[07 Carriles de trabajo concurrente]].

> [!important] Antes de escribir la primera línea
> [[00b Estándar de ejecución · código limpio, pruebas y calidad]] aplica en esta fase
> entera: regla cero de no inventar, composición atómica, KISS, nombres del dominio,
> las siete pruebas obligatorias por caso de uso y el checklist de PR. **Se declara
> cada pieza por nivel antes de crearla.**

> **Receta exacta:** [[00c Recetario · implementar un caso de uso]] fija el orden de
> lectura, el orden de construcción, las firmas canónicas y los nombres de las piezas
> de `plataforma/`. **Se copian, no se reinventan.**

**Estas dos fases son las más importantes del plan.** Ningún servicio de negocio
compensa un `SET LOCAL` mal puesto, un `numeric` leído como `double` o un rol con
permiso de más.

---

# FASE 1 — Esquemas, roles y capa de datos

> **Objetivo.** Que existan `plataforma/comun-dominio` con `Dinero` exacto y
> `plataforma/comun-datos` con el acceso a las 305 tablas **generado** desde la base
> viva, repartidas en catorce esquemas con un rol por servicio, de modo que un
> servicio no pueda leer los datos de otro **aunque quiera**.

## Gate de entrada

- [ ] Gate de salida de la Fase 0, ejecutado
- [ ] `docker compose --profile base up -d --wait` en verde

## Leer antes de empezar

| Archivo | Qué se saca de ahí |
| --- | --- |
| `docs/Arquitectura/ADR-016 Acceso a datos con jOOQ.md` | Qué protegía el query builder, y por qué JPA está prohibido |
| `docs/Arquitectura/ADR-017 Propiedad de datos por servicio.md` | Esquemas, roles, FK cruzadas y la excepción del libro |
| `docs/Arquitectura/ADR-019 Dinero con BigDecimal.md` | Las seis reglas del dinero |
| `docs/Arquitectura/ADR-021 Sesión, RLS y pooling.md` | `SET LOCAL`, contexto y dimensionamiento de pools |
| `docs/Restricciones.md` | Qué rechaza la base, y con qué nombre |

---

## 1.1 · `plataforma/comun-dominio` — los átomos compartidos

Los diez átomos que usan los catorce servicios. Sin Spring, sin jOOQ, sin IO. Cada uno
con su prueba unitaria y cobertura 95 %.

| Átomo | Qué garantiza |
| --- | --- |
| `Dinero` | `BigDecimal` con escala 2 y `HALF_UP`; moneda inseparable del importe |
| `Moneda` | Enumeración cerrada; sumar BOB con USD falla con error del dominio |
| `Periodo` | Rango de fechas con inicio y fin, sin solapes |
| `PlazoHabil` | Suma días hábiles contra el calendario **inyectado**, nunca `LocalDate.now()` |
| `ContextoSesion` | Usuario, rol, traza y dispositivo. Sin él no hay consulta |
| `ClaveIdempotencia` | Derivada del hecho, no del reintento |
| `CodigoError` | `AP-CU<NN>-<nn>`, validado en construcción |
| `Prorrateo` | Reparte un importe sin perder el residuo |
| `Reloj` | Interfaz inyectable; nada en el dominio llama al reloj del sistema |
| `Traza` | Identificador que atraviesa los catorce servicios |

> **`Dinero` es el átomo que justifica el cambio de lenguaje.** Con `BigDecimal`, dos
> de las tres reglas de disciplina del stack anterior las resuelve el tipo. Queda la
> tercera —serializar como cadena en JSON— porque el cliente es JavaScript.

**Entregable 1.1:** los diez átomos, `./gradlew test` en verde, cobertura ≥ 95 %, y
**ninguna dependencia de Spring** en el proyecto (lo verifica ArchUnit).

---

## 1.2 · Esquemas, roles y permisos

Esta sección no existía en el plan anterior. Es la que convierte la partición de
servicios en una frontera real.

### Paso a paso, en este orden

```bash
python3 scripts/generar_ddl.py     # 1. gana la asignación de esquema por módulo
./gradlew bd:reset                 # 2. crea esquemas, roles, tablas, GRANT y semillas
./gradlew generateJooq             # 3. genera las clases, POR ESQUEMA
./gradlew compileJava              # 4. compila ← el gate del invariante 1
```

### La asignación de esquema

El nombre sale del `.puml` del módulo, sin decisión humana:
`docs/entidades/01_identidad_usuarios.puml` → esquema `identidad`.

**Con una excepción, enumerada:** `cuenta_contable`, `asiento_contable`,
`movimiento_contable` y `cierre_diario` viven en `nucleo_financiero`, no en `aportes`.
Es lo que mantiene la partida doble en una sola transacción ACID
([[ADR-017 Propiedad de datos por servicio]]).

Más un esquema `catalogo` para lo sembrado que muchos leen y nadie escribe en
caliente: tipo de cambio, calendario de días no hábiles, tarifario vigente, umbrales.

### Los permisos son la frontera

```sql
CREATE ROLE svc_identidad LOGIN;
GRANT USAGE ON SCHEMA identidad TO svc_identidad;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA identidad TO svc_identidad;
GRANT USAGE ON SCHEMA catalogo TO svc_identidad;
GRANT SELECT ON ALL TABLES IN SCHEMA catalogo TO svc_identidad;
-- y nada más. Sin GRANT sobre los otros trece esquemas.
```

Más `REVOKE UPDATE, DELETE` sobre las tablas de `sql/35_append_only/` para el rol
dueño: **la base rechaza**, el análisis estático solo adelanta el fallo.

### Las claves foráneas cruzadas se conservan

PostgreSQL soporta FK entre esquemas de la misma base. **Las 633 relaciones siguen
existiendo y las sigue verificando el motor.** Es la línea entera del argumento a
favor de un clúster: se compra aislamiento de despliegue sin vender integridad
referencial.

**Entregable 1.2:** catorce esquemas, catorce roles, y **una prueba por par de
servicios** que comprueba que el `SELECT` cruzado devuelve permiso denegado.

---

## 1.3 · `plataforma/comun-datos` — jOOQ y el contexto

### El código generado no se versiona

- Salida a `build/generated/jooq/`, con `includes` restringido al esquema del servicio.
- **El gate no es un diff: es la compilación.** Si el esquema cambió y el código no,
  el build falla. Con catorce servicios, versionar el generado serían catorce diffs
  ilegibles y catorce puntos de conflicto entre carriles.
- Un servicio **no tiene ni siquiera las clases** de las tablas ajenas: no puede
  consultarlas por accidente.

### `conContexto` — la pieza más delicada del proyecto

```java
@Transactional
public <T> T conContexto(ContextoSesion ctx, Function<DSLContext, T> fn) {
    if (ctx.usuarioId() == null || ctx.rol() == null) throw new SinContextoDeSesion();
    dsl.execute("select set_config('app.usuario_id', ?, true)", ctx.usuarioId());
    dsl.execute("select set_config('app.rol',        ?, true)", ctx.rol());
    dsl.execute("select set_config('app.traza',      ?, true)", ctx.traza());
    return fn.apply(dsl);
}
```

| Regla | Por qué |
| --- | --- |
| `set_config(…, true)` es `SET LOCAL` | Muere en el `COMMIT`. Un `SET` plano sobrevive a la petición y contamina la siguiente |
| El `DSLContext` es el de la transacción en curso | Tomar otra conexión pierde el contexto **en silencio**. Es el error más caro de esta sección |
| Sin contexto no hay consulta | La ausencia es un defecto, no un caso. No se «asume el usuario del sistema» |
| Trabajos y consumidores usan `app.rol = 'sistema'` | Es un rol con sus propias políticas, no una excepción a las políticas |

### Fábrica de conexiones

| Proceso | Rol | Conexión | Nota |
| --- | --- | --- | --- |
| Cada servicio | `svc_<esquema>` | por **PgBouncer** en modo transacción | `prepareThreshold=0` en el driver |
| `auditoria` (lecturas) | `rol_auditor` | **réplica**, solo lectura | Segundo `DataSource` |
| Migrador | `rol_migracion` | directa | Solo el `Job` de despliegue |

**El pool se declara en un solo documento.** La suma de los catorce más los relevos no
supera `max_connections`; el arranque advierte si se pasó. Un servicio que sube su
pool sin mirar la suma le roba conexiones a los otros trece.

**Entregable 1.3:** las clases de jOOQ generan y compilan para los catorce esquemas;
`conContexto` con su prueba negativa.

---

## 1.4 · Las cuatro pruebas que cierran la Fase 1

| Prueba | Qué hace | Invariante |
| --- | --- | :-: |
| `EsquemaAlDiaTest` | Cambia una columna en la base de Testcontainers y comprueba que **la compilación falla** | 1 |
| `AppendOnlyTest` | Intenta `update` sobre `asiento_contable`, `movimiento_billetera`, `evento_dominio` y todas las selladas | 5: la base rechaza por `REVOKE`, **no** la aplicación |
| `AislamientoEsquemaTest` | Por cada par de servicios, `SELECT` cruzado ⇒ permiso denegado | 11 |
| `DineroCuadreTest` | Las seis de `dinero-decimal`: cuadre `0.00` · asiento equilibrado en SQL · propiedad con mil operaciones · prorrateo con residuo asignado · operar monedas distintas **lanza** · la API devuelve cadena de dos decimales | 4 |

## Gate de salida de la Fase 1

```bash
./gradlew bd:reset generateJooq compileJava
./gradlew spotlessCheck check
./gradlew test integrationTest
python3 scripts/verificar_boveda.py
```

- [ ] Los catorce esquemas y catorce roles existen
- [ ] `AislamientoEsquemaTest` pasa para **todos** los pares ← invariante 11
- [ ] Solo `svc_nucleo_financiero` escribe `asiento_contable` ← invariante 12
- [ ] Cambiar el esquema sin regenerar **rompe la compilación** ← invariante 1
- [ ] Ninguna dependencia de JPA en ningún `build.gradle.kts`
- [ ] `comun-dominio` no depende de Spring ni de jOOQ (ArchUnit)
- [ ] La prueba negativa de RLS devuelve **cero filas**, no error ← invariante 3

---

# FASE 2 — Plataforma común, outbox y gateway

> **Objetivo.** Que exista todo lo que un servicio de negocio va a dar por sentado:
> el manejador de errores, la idempotencia, la guardia de permisos, la traza, el
> outbox con su relevo a Kafka, el consumidor idempotente, los trabajos con ShedLock y
> el gateway. Y que un caso de uso de prueba lo recorra entero.

## Gate de entrada

- [ ] Gate de salida de la Fase 1, ejecutado

## Leer antes de empezar

| Archivo | Qué se saca de ahí |
| --- | --- |
| `docs/Arquitectura/ADR-018 Outbox transaccional y mensajería.md` | Outbox, relevo, Kafka, ShedLock |
| `docs/Arquitectura/ADR-020 Contratos OpenAPI primero.md` | Contrato primero, servidor generado |
| `docs/Arquitectura/ADR-022 Comunicación entre servicios.md` | Gateway, Resilience4j, saga |
| `docs/Arquitectura/ADR-024 Autenticación y sesión distribuida.md` | JWKS, validación local, lista de rechazo |
| `docs/Arquitectura/Flujo de una transacción.md` | El orden de los trece pasos |

---

## 2.1 · `plataforma/comun-web` — el flujo hecho código

Los trece pasos de [[Flujo de una transacción]], como componentes reutilizables.

> **No hay un archivo que registre módulos.** Cada servicio es un proceso; nadie lo
> anota en ninguna lista. Es el conflicto #1 del plan de carriles, eliminado por
> construcción.

```
plataforma/comun-web/
├── configuracion/     validación al arrancar: si falta una clave, no levanta
├── seguridad/         validación de JWT contra JWKS · guardia por permiso · lista de rechazo
├── idempotencia/      valida la clave ANTES de escribir; devuelve la respuesta original
├── errores/           manejador global: constraint_name → R-XXX-nn → mensaje
├── traza/             x-request-id: lo asigna, lo propaga y lo registra
├── dinero/            serializador: BigDecimal → cadena decimal, nunca número
└── salud/             readiness (base + Kafka) y liveness (solo el proceso), separados
```

### Los cinco puntos donde se equivoca todo el mundo

| # | El error | Qué lo evita |
| :-: | --- | --- |
| 1 | Poner el contexto de RLS fuera de la transacción | `conContexto` es el único camino; ArchUnit lo verifica |
| 2 | Validar la idempotencia después de escribir | El componente corre antes del `BEGIN` |
| 3 | Devolver el mensaje crudo de PostgreSQL | El manejador global traduce; **una restricción sin entrada en el catálogo ⇒ `500` y alerta**, nunca un mensaje improvisado |
| 4 | Serializar un importe como número JSON | El serializador es global y hay un barrido que revisa todas las respuestas |
| 5 | **Confiar en una cabecera del gateway** para saber quién es el usuario | Cada servicio valida la firma del JWT él mismo. La red interna no es perímetro de confianza |

> **El punto 5 es nuevo y es el más grave de los cinco.** Si un servicio cree lo que
> le dice una cabecera, cualquiera que alcance la red interna suplanta a cualquiera.

---

## 2.2 · `plataforma/comun-mensajeria` — outbox, relevo y consumo

### La escritura no se negocia

```java
@Transactional
public ResultadoAporte ejecutar(...) {
    var asiento = libro.registrar(...);
    outbox.emitir("aportes.aporte_confirmado", carga);   // MISMA transacción
    return ...;                                          // COMMIT: los dos o ninguno
}
```

Cada esquema tiene su tabla `evento_dominio`. Si la transacción revierte, el evento no
existió. **Nunca se publica a Kafka desde dentro de la transacción**: eso es el fallo
que el outbox existe para impedir, y hay una regla de análisis estático que lo prohíbe.

### El relevo y el consumo

| Pieza | Regla |
| --- | --- |
| Relevo | `SELECT … FOR UPDATE SKIP LOCKED`, publica y marca. Es **al menos una vez** |
| Tema | `aportaya.<modulo>.<evento>` — prefijo obligatorio, único por construcción |
| Clave de partición | El identificador del agregado, para que lo de una billetera llegue en orden |
| Consumo | Tabla `evento_consumido (id_evento, consumidor)` con clave única. **Idempotente, sin excepción** |
| Descartados | `aportaya.<modulo>.<evento>.descartados` + **evento de riesgo operativo**. Un mensaje perdido en silencio es peor que uno que falla ruidosamente |

### Trabajos programados

`@Scheduled` + `@SchedulerLock` con nombre `<modulo>.<trabajo>`. ShedLock toma el
bloqueo **en la misma PostgreSQL**: la garantía de exactamente-una-vez entre réplicas
no depende de Kafka.

**No hay servicio `worker`.** Cada servicio corre sus trabajos y su relevo; un worker
central sería un archivo compartido por catorce carriles y un despliegue acoplado.

**Entregable 2.2:** el mismo evento entregado dos veces produce **un** efecto; con dos
réplicas levantadas, un trabajo con bloqueo corre **una** vez.

---

## 2.3 · El gateway

Spring Cloud Gateway delante de los catorce. Enruta por prefijo reservado, termina
TLS, limita tasa por identidad y por IP, y asigna `x-request-id` si no viene.

**No contiene reglas de negocio, no compone respuestas y no traduce errores.** Un
gateway con lógica es el monolito volviendo por la puerta de atrás, y además
compartido por catorce carriles.

**Entregable 2.3:** una ruta fuera de un prefijo reservado **no arranca**; ningún
servicio publica puerto.

---

## 2.4 · `plataforma/comun-pruebas` y las doce pruebas de barrido

Las quince de [[19 Contrato de carril · conflicto cero, skills y calidad verificada]] §5.
Se escriben **acá, una vez**, y cubren para siempre todo servicio que venga después:
no enumeran casos, enumeran el registro vivo de rutas, temas, trabajos y métricas.

Ningún carril las escribe, ninguno las modifica, ninguno puede desactivarlas: el CI
falla si el número de pruebas activas baja.

---

## 2.5 · El caso de uso de prueba (`CU-00`)

Un caso de uso ficticio que recorre el pipeline entero sin tocar dominio real:
contrato OpenAPI → controlador generado → organismo con `@Transactional` →
`conContexto` → escritura → outbox → `COMMIT` → relevo → Kafka → consumidor
idempotente.

**Es el que demuestra que la Fase 2 está terminada.** Si `CU-00` no atraviesa los
trece pasos con su traza correlacionada de punta a punta, ningún servicio de negocio
va a hacerlo.

---

## 2.6 · Servicio `auditoria` parcial — la infraestructura de bitácora

Lo mínimo para que los demás servicios puedan dejar rastro desde el día uno: la
bitácora de eventos con su cadena de hashes calculada **por la base** (`R-AUD-09`), y
el registro de intentos de autenticación.

---

## Gate de salida de la Fase 2

```bash
./gradlew spotlessCheck check
./gradlew generateJooq compileJava generateOpenApiClients
./gradlew test integrationTest contractTest
./gradlew testBarrido
docker compose --profile todo up -d --wait && ./gradlew e2eTest
python3 scripts/verificar_boveda.py
```

- [ ] Los trece puntos del gate común (§9 del [[00 Plan maestro]])
- [ ] `CU-00` recorre los trece pasos del flujo, con `x-request-id` correlacionado
- [ ] Las quince pruebas de barrido existen y pasan
- [ ] El mismo evento dos veces ⇒ un efecto
- [ ] Dos réplicas ⇒ el trabajo programado corre una vez
- [ ] Ninguna llamada de red dentro de un método `@Transactional` (análisis estático)
- [ ] Un JWT firmado con otra clave es rechazado, **aunque venga de la red interna**
- [ ] Un endpoint sin permiso declarado **impide el arranque**
- [ ] Todo campo monetario se serializa como cadena decimal (barrido 6)
- [ ] Con Kafka caído, el sistema **no pierde eventos**: se acumulan en el outbox y la
      métrica de edad del evento más viejo lo muestra

## Ver también

[[00 Plan maestro]] · [[01 Fase 0 · Cimientos del repositorio]] · [[03 Fases 3 a 7 · Identidad, habilitación y núcleo de dinero]] · [[00c Recetario · implementar un caso de uso]] · [[19 Contrato de carril · conflicto cero, skills y calidad verificada]] · [[Flujo de una transacción]] · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-018 Outbox transaccional y mensajería]]
