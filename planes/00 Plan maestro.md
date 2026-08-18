---
tags:
  - moc
  - plan
titulo: "Plan maestro de desarrollo del backend — AportaYa"
fecha: 2026-08-16
alcance: servicios/* · plataforma/* · despliegue/*
---

# Plan maestro de desarrollo del backend

> **Para quién es este documento.** Para la IA (o la persona) que va a escribir el
> backend. Cada fase dice qué leer antes, qué archivos crear, en qué capa, con qué
> pruebas y qué comando tiene que pasar en verde para poder avanzar. Nada queda
> librado a criterio salvo lo que este plan marca explícitamente como decisión
> abierta.

> [!important] Los otros tres documentos que mandan
> [[00b Estándar de ejecución · código limpio, pruebas y calidad]] dice **cómo se
> escribe** · [[00c Recetario · implementar un caso de uso]] dice **el orden exacto,
> las firmas y los nombres canónicos** · [[07 Carriles de trabajo concurrente]] dice
> **quién hace qué, en qué máquina y qué archivos puede tocar**. Los cuatro se leen
> antes de empezar. **Si un documento de fase los contradice, gana el estándar.**

> [!warning] Cambio de rumbo del 2026-08-16 — este plan se reescribió entero
> El backend pasó de **TypeScript/NestJS en un despliegue** a **Java 21 + Spring Boot
> en catorce servicios**. El motivo tiene dos mitades: exactitud del dinero
> (`BigDecimal` nativo) y forma de trabajo (un carril posee un desplegable entero, no
> un directorio por acuerdo). Está en `docs/Stack.md` y en `ADR-014`.
>
> **Diez ADR quedaron superados.** Si algún documento de este directorio todavía
> menciona Kysely, MikroORM, Zod, Jest o Graphile Worker, está desactualizado y gana
> este plan.

> [!warning] Saneamiento del 2026-08-18 — leer antes de abrir T0
> La auditoría posterior a la migración detectó huecos que este plan y los documentos
> de fase todavía arrastran del monolito: sagas sin inventario, outbox sin sustrato en
> el modelo, gates de entrada escritos como "fase cerrada", y restos de TypeScript en
> los documentos operativos. **[[20 Saneamiento del plan · huecos de la migración a microservicios]]
> manda sobre este documento y sobre 00b–19 en todo lo que corrige**, hasta que sus
> deltas (§7) se apliquen en el documento de origen. Su §10 define los pasos S1–S9,
> anteriores al tramo T0.

## Estado de partida (verificado el 2026-08-16)

| Artefacto | Estado |
| --- | --- |
| `docs/` — bóveda | **Completa**: 99 casos de uso, 306 entidades, 633 relaciones, 138 restricciones, 32 ADR |
| `sql/` — esquema | **Generado y aplicable**: `psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql` |
| `seeders/` — catálogos | **Listos**: **20** catálogos mínimos (van a producción) + **14** de prueba |
| `scripts/` — generadores | **Listos**: DDL, bóveda, semillas, verificador. **Falta**: asignación de esquema por servicio (Fase 1) |
| `servicios/` · `plataforma/` | **No existen.** Este plan los crea |

El backend arranca de cero, pero **no de la nada**: la especificación ya está escrita
y es ejecutable. El trabajo es traducirla, no inventarla.

---

## 1 · Los diez invariantes

Ninguna fase, ningún servicio y ninguna urgencia los suspende. Si un requerimiento
choca con uno de estos, gana el invariante y se escribe un ADR explicando la tensión.

| # | Invariante | De dónde sale | Cómo se verifica |
| :-: | --- | --- | --- |
| 1 | **El código no administra el esquema.** `sql/` es dueño; las clases de acceso se *generan* desde la base viva | [[ADR-016 Acceso a datos con jOOQ]] | El esquema cambia y el código no ⇒ **no compila** |
| 2 | **Una transacción por caso de uso**, abierta y cerrada en el organismo | [[Flujo de una transacción]] | ArchUnit: ningún `@Transactional` fuera de `aplicacion/` |
| 3 | **`SET LOCAL` dentro de la transacción**, nunca fuera y nunca `SET` plano | [[ADR-021 Sesión, RLS y pooling]] | Prueba negativa: contexto ajeno ⇒ **cero filas**, no error |
| 4 | **Ningún importe pasa por punto flotante.** Todo importe vive como `Dinero` sobre `BigDecimal` | [[ADR-019 Dinero con BigDecimal]] | Análisis estático + prueba de cuadre al centavo |
| 5 | **Append-only: nada se edita.** Corrección = movimiento inverso | `sql/35_append_only/` | `REVOKE` en la base + prueba de rechazo del `UPDATE` |
| 6 | **Ninguna llamada de red dentro de la transacción.** Ni a un proveedor ni a otro servicio | [[ADR-018 Outbox transaccional y mensajería]] | Análisis estático sobre métodos `@Transactional` |
| 7 | **La clave de idempotencia se valida antes de escribir**, no después | [[_CasosDeUso]] | Prueba: misma petición dos veces ⇒ misma respuesta, cero efectos nuevos |
| 8 | **Los plazos se persisten al crear**, jamás se recalculan al consultar | skill `plazos-habiles` | Prueba: cambiar el calendario no mueve un plazo ya emitido |
| 9 | **Denegar por omisión.** Sin límite, licencia, tarifario o política vigente ⇒ rechazo | `R-LIM-01`, `R-LIC-01` | Prueba: base sin semillas ⇒ toda operación de dinero falla |
| 10 | **Umbrales, límites y tarifas son catálogo, no constantes** | skill `norma-nueva` | Análisis estático: sin literales monetarios fuera de `seeders/` |

### Y dos que agrega la arquitectura de servicios

| # | Invariante | De dónde sale | Cómo se verifica |
| :-: | --- | --- | --- |
| 11 | **Un servicio no lee la base de otro.** Ni por `JOIN`, ni por vista, ni por permiso prestado | [[ADR-017 Propiedad de datos por servicio]] | `SELECT` cruzado ⇒ **permiso denegado**. Una prueba por par |
| 12 | **El libro contable no se parte.** Débito, crédito y asiento confirman en una sola transacción | [[ADR-014 Arquitectura de servicios]] | Solo `svc_nucleo_financiero` escribe `asiento_contable` y `movimiento_billetera` |

---

## 2 · Stack fijado

| Capa | Elección | ADR | Nota operativa |
| --- | --- | :-: | --- |
| Arquitectura | **14 servicios**, uno por módulo; `nucleo-financiero` fusiona billetera y libro | 014 | Un carril = un servicio |
| Runtime | **Java 21 LTS**, hilos virtuales | 015 | `spring.threads.virtual.enabled=true` |
| Framework | **Spring Boot 3.3**, Spring MVC | 015 | WebFlux no: la carga es de E/S, no de concurrencia reactiva |
| Construcción | **Gradle** con Kotlin DSL, catálogo de versiones | 015 | `settings.gradle.kts` descubre por barrido |
| Acceso a datos | **jOOQ** generado desde la base viva | 016 | **JPA prohibido**, no desaconsejado |
| Migraciones | **Flyway** aplicando `sql/`, como `Job` de despliegue | 025 | Ningún servicio migra al arrancar |
| Datos | Un clúster · **un esquema y un rol por servicio** | 017 | Las 633 FK se conservan |
| Dinero | **`BigDecimal`** dentro de `Dinero`; cadena decimal en JSON | 019 | Invariante 4 |
| Contratos | **OpenAPI 3.1 escrito primero**; servidor y clientes generados | 020 | El controlador implementa la interfaz generada |
| Validación | **Bean Validation** sobre los tipos generados + `additionalProperties: false` | 020 | Campo desconocido = error |
| Sesión y RLS | `SET LOCAL` en la transacción; el JWT del usuario cruza la red | 021 | Cada servicio revalida la firma |
| Autenticación | `identidad` emite RS256; los trece validan contra JWKS | 024 | Nadie confía en una cabecera |
| Entre servicios | Gateway sin lógica · **Resilience4j** · **saga orquestada** | 022 | Máximo dos saltos |
| Eventos | **Outbox en PostgreSQL** → relevo → **Kafka** | 018 | Encolar sigue siendo parte del `COMMIT` |
| Cron | **ShedLock** sobre PostgreSQL | 018 | El cierre diario no corre dos veces |
| Registro | **Logback estructurado** con `cu`, `usuario_id`, `traza`, `servicio` | — | Redacción de PII obligatoria |
| Métricas | **Micrometer** + OpenTelemetry | — | Prefijo de módulo obligatorio |
| Pruebas | **JUnit 5 + Testcontainers** · Spring Cloud Contract · ArchUnit | 026 | PostgreSQL 16 real, nunca en memoria |
| Empaquetado | **Docker** multietapa sin root; Kubernetes con manifiestos generados | 025 | Solo el gateway publica puerto |

### Lo que está prohibido

- **JPA / Hibernate** en cualquier servicio, y `spring-boot-starter-data-jpa` en
  cualquier `build.gradle.kts`.
- Punto flotante para importes, en cualquier capa.
- **Publicar a Kafka dentro de una transacción.** Para eso está el outbox.
- `UPDATE`/`DELETE` sobre cualquier tabla de `sql/35_append_only/`.
- Migraciones escritas a mano fuera de `sql/`, y migrar al arrancar.
- **Consultar el esquema de otro servicio**, con cualquier excusa.
- Transacciones distribuidas (XA / dos fases).
- Un gateway con lógica de negocio.
- Código generado versionado: ni jOOQ, ni los clientes, ni los manifiestos.

---

## 3 · Las cuatro capas, y qué puede hacer cada una

Es [[ADR-023 Composición atómica en Java]] convertido en paquetes. **La dirección de
dependencia no se invierte nunca.**

```
web/             PÁGINA      implementa la interfaz generada del OpenAPI
  ↓
aplicacion/      ORGANISMO   un caso de uso = una clase = una transacción
  ↓         ↘
dominio/     infraestructura/
ÁTOMO        MOLÉCULA        repositorios, clientes de pares, adaptadores
                 ↓
              dominio/
```

| Capa | Puede depender de | Nunca hace | Prueba que le corresponde |
| --- | --- | --- | --- |
| `dominio/` — **átomo** | Nada. Ni Spring, ni jOOQ | IO, SQL, red, reloj o azar sin inyectar | Unitaria pura. `<Atomo>Test.java` |
| `infraestructura/` — **molécula** | `dominio/`, `plataforma/comun-datos` | Abrir transacción, orquestar otro caso, contener un `if` de negocio | Integración contra PostgreSQL real. `<Repo>Test.java` |
| `aplicacion/` — **organismo** | `dominio/`, `infraestructura/` | SQL directo, llamar a un proveedor por su cuenta | Criterios de aceptación. `CU<NN>Test.java` |
| `web/` — **página** | `aplicacion/` y los tipos generados | Cualquier regla de negocio o cálculo | De API. `CU<NN>WebTest.java` |
| `trabajos/` | `aplicacion/` | Reimplementar el caso de uso: lo invoca | Prueba de consumidor idempotente |

### Anatomía obligatoria de un servicio

```
servicios/<nombre>/
├── build.gradle.kts
├── descriptor.yml                        réplicas, recursos, sondas → genera el k8s
├── README.md                             qué resuelve, sus CU, eventos, trabajos
├── src/main/resources/
│   ├── application.yml
│   └── openapi/<nombre>.yaml             SU contrato, escrito primero
├── src/main/java/bo/aportaya/<nombre>/
│   ├── dominio/  infraestructura/  aplicacion/  web/  trabajos/
└── src/test/java/bo/aportaya/<nombre>/
```

### Convención de nombres

| Cosa | Forma | Ejemplo |
| --- | --- | --- |
| Servicio | `kebab-case`, el módulo sin número | `nucleo-financiero` |
| Paquete raíz | `bo.aportaya.<servicio>` | `bo.aportaya.tarifas` |
| Caso de uso | `CU<NN><VerboObjeto>.java` | `CU21CobrarAporte.java` |
| Prueba del caso de uso | `CU<NN>Test.java` | `CU21Test.java` |
| Prueba de API | `CU<NN>WebTest.java` | `CU21WebTest.java` |
| Repositorio | `<Sustantivo>Repositorio.java` | `ObligacionRepositorio.java` |
| Cliente de otro servicio | `<Servicio>Cliente.java` | `NucleoFinancieroCliente.java` |
| Adaptador externo | `<Proveedor>Adapter.java` | `PasarelaQrAdapter.java` |
| Tema de Kafka | `aportaya.<modulo>.<evento>` | `aportaya.aportes.aporte_confirmado` |
| Bloqueo de ShedLock | `<modulo>.<trabajo>` | `nucleo_financiero.cierre_diario` |
| Esquema · rol | `<modulo>` · `svc_<modulo>` | `nucleo_financiero` · `svc_nucleo_financiero` |
| Tabla | `snake_case` tal cual el modelo | `obligacion_aporte` |
| Código de error | `AP-CU<NN>-<nn>` | `AP-CU21-03` |

---

## 4 · jOOQ y los esquemas sin romper la bóveda — las seis reglas

Esta sección existe porque los invariantes 1, 5 y 11 son exactamente lo que un
acceso a datos mal elegido viola. **Se implementa completa en la Fase 1 y se
verifica en CI desde entonces.**

### R1 · Las clases se generan, no se escriben

```bash
./gradlew :servicios:tarifas:generateJooq   # contra base efímera con sql/aplicar.sql
```

- Salida a `build/generated/jooq/` — **artefacto de compilación, no versionado**.
- `includes` restringido al **esquema del servicio**: un servicio no tiene ni
  siquiera las clases de las tablas ajenas.
- El gate no es un diff: **es la compilación**. Si el esquema cambió y el código no,
  el build falla. Con catorce servicios, versionar el generado sería catorce diffs
  ilegibles y catorce puntos de conflicto.

### R2 · Nada administra el esquema

- **No** se declara `spring-boot-starter-data-jpa` en ningún `build.gradle.kts`.
- **No** existe la clave `spring.jpa` en ningún `application.yml`.
- Flyway corre como `Job` de despliegue con `rol_migracion`, nunca al arrancar.
- Regla de análisis estático: cualquier importe de `jakarta.persistence` es error.

### R3 · Append-only sin escritura accidental

- Las tablas selladas en `sql/35_append_only/append_only.sql` tienen `REVOKE
  UPDATE, DELETE` para el rol del servicio dueño: **la base rechaza**, el análisis
  estático solo adelanta el fallo.
- Escritura por `insert into`; corrección por movimiento inverso.

### R4 · Contexto de RLS en la misma conexión

```java
// plataforma/comun-datos
@Transactional
public <T> T conContexto(ContextoSesion ctx, Function<DSLContext, T> fn) {
    if (ctx.usuarioId() == null || ctx.rol() == null) throw new SinContextoDeSesion();
    dsl.execute("select set_config('app.usuario_id', ?, true)", ctx.usuarioId());
    dsl.execute("select set_config('app.rol',        ?, true)", ctx.rol());
    dsl.execute("select set_config('app.traza',      ?, true)", ctx.traza());
    return fn.apply(dsl);
}
```

- `set_config(…, true)` es `SET LOCAL`: muere en el `COMMIT`. **Nunca** `SET` plano.
- El `DSLContext` tiene que ser el de la transacción en curso; tomar otra conexión
  pierde el contexto **en silencio** — es el error más caro de esta sección.
- **Ninguna** consulta a tabla con RLS fuera de `conContexto`.

### R5 · Dinero como `BigDecimal`

jOOQ mapea `numeric(14,2)` a `BigDecimal` de forma nativa: no hace falta conversor.
Lo que sí hace falta es el objeto de valor y el serializador:

```java
public record Dinero(BigDecimal importe, Moneda moneda) { … }   // escala 2, HALF_UP
```

Y en JSON, **cadena decimal, nunca número**: el cliente es JavaScript, y un `number`
de JSON es un doble del otro lado. Una columna monetaria tipada como `double` es un
fallo de CI.

### R6 · Un esquema y un rol por servicio

- El esquema sale del `.puml` del módulo, calculado por `generar_ddl.py`.
- **Excepción única y enumerada**: `cuenta_contable`, `asiento_contable`,
  `movimiento_contable` y `cierre_diario` viven en `nucleo_financiero`, no en
  `aportes`. Es lo que mantiene la partida doble en una sola transacción.
- `GRANT` solo sobre el esquema propio más `SELECT` sobre `catalogo`.
- Una prueba por par de servicios: el `SELECT` cruzado devuelve permiso denegado.

---

## 5 · Errores, respuestas y observabilidad

### Forma única de la respuesta

```jsonc
// éxito
{ "datos": { … }, "trazaId": "01J8X…" }
// error  ← la forma exacta de la skill `errores-api`
{ "codigo": "AP-CU21-03",
  "mensaje": "No tenés saldo suficiente para este aporte.",
  "detalle": { "faltante": "45.00", "moneda": "BOB" },
  "trazaId": "01J8X…" }
```

| Campo | Para quién | Regla |
| --- | --- | --- |
| `codigo` | Soporte y auditoría | `AP-CU<NN>-<nn>`, declarado en el OpenAPI del servicio |
| `mensaje` | Usuario | Español, sin jerga, **dice qué hacer** cuando hay algo que hacer |
| `detalle` | App | Datos para armar un mensaje mejor; opcional |
| `trazaId` | Soporte | Es el `x-request-id`, y correlaciona **los catorce servicios** |

### Mapeo obligatorio a HTTP

| Situación | HTTP | Cuerpo | Qué queda registrado |
| --- | :-: | --- | --- |
| Entrada inválida por el contrato | `400` | Lista de campos con mensaje | Nada escrito |
| **Regla de negocio de la aplicación** | **`422`** | `{ codigo: 'AP-CU21-02', … }` | Intento en bitácora |
| Sin autenticar | `401` | `AP-SEG-01` | `intento_autenticacion` |
| Sin permiso / fuera de RLS | `403` **o resultado vacío** | Sin detalles internos | `bitacora_evento` |
| Restricción de la base rechaza | `409` | `{ codigo: 'R-XXX-nn', … }` traducido | El rechazo con la restricción que actuó |
| Clave de idempotencia repetida | `200` | **La respuesta original, íntegra** | Nada nuevo |
| Proveedor externo indisponible | `202` | Aceptado; se completa por la cola | El trabajo con sus intentos |
| **Otro servicio indisponible** | `503` si la respuesta era imprescindible; `202` si sigue por saga | Sin detalles internos | El cortacircuitos que actuó, con métrica |
| Falla no prevista | `500` | **Solo `trazaId`. Nada más** | Registro de error con la traza |

> **`422`, no `400`, para las reglas de negocio.** El `400` es del esquema; el `422`
> es de la regla. Confundirlos deja al cliente sin poder distinguir «mandaste mal el
> formulario» de «no tenés saldo».

**Nunca** sale un mensaje crudo de PostgreSQL. El manejador global mapea
`constraint_name` → `R-XXX-nn` → mensaje, desde un catálogo generado. **Una
restricción que dispara y no está en el catálogo ⇒ `500` y alerta**: es un caso que
nadie previó, se registra como incidente y no se improvisa un mensaje genérico.

**Los códigos no se reutilizan.** Un código retirado queda retirado. Y un código sin
prueba que lo dispare es decorativo.

### Registro

Toda línea lleva `{ cu, usuario_id, traza, servicio }`. Redacción obligatoria de:
`authorization`, `cookie`, `*.password`, `*.pin`, `*.numero_documento`,
`*.numero_cuenta`, `*.telefono`, `*.correo`, `*.token`, `*.clave_*`.

**La traza se propaga por toda la cadena**: a los otros servicios en la llamada
sincrónica, al outbox en la carga del evento, y de ahí a los consumidores. Con
catorce servicios esto no es comodidad: es la única forma de reconstruir qué pasó.

---

## 6 · Puertas de calidad

### Verificaciones propias del proyecto

Se implementan como reglas de análisis estático y pruebas de ArchUnit en
`plataforma/comun-pruebas`, y **ningún servicio puede desactivarlas**.

| Regla | Qué prohíbe | Invariante |
| --- | --- | :-: |
| `sin-punto-flotante-monetario` | `double`/`float` en cualquier tipo que represente dinero | 4 |
| `sin-equals-bigdecimal` | `equals` sobre `BigDecimal` — se compara con `compareTo` | 4 |
| `sin-jpa` | Cualquier importe de `jakarta.persistence` | 1 |
| `consulta-en-contexto` | Uso de `DSLContext` fuera de `conContexto` | 3 |
| `transaccion-solo-en-organismo` | `@Transactional` fuera de `aplicacion/` | 2 |
| `sin-red-en-transaccion` | Llamada a un `*Adapter`, `*Cliente` o productor de Kafka dentro de `@Transactional` | 6 |
| `sin-update-append-only` | `update`/`delete` sobre tabla sellada | 5 |
| `sin-umbral-literal` | Literal numérico monetario fuera de `seeders/` y pruebas | 10 |
| `capas` (ArchUnit) | `dominio/` importando Spring, jOOQ, `infraestructura/` o `web/` | — |
| `sin-import-cruzado` (ArchUnit) | Importar `bo.aportaya.<otro-servicio>` | 11 |
| `tamano-archivo` | ≥ 220 líneas advierte · ≥ 260 exige revisión · ≥ 300 **bloquea** | skill `ci-calidad` |
| `sin-system-out` | `System.out`/`err` en código de runtime | — |

### Orden del pipeline de CI (bloqueante en cada paso)

Es el pipeline de la skill `ci-calidad` **completo**, con los comandos de este stack.
Los pasos 5, 6, 11 y 12 son los que impiden que la bóveda, la base y el código se
desincronicen.

```
 1  ./gradlew --version                    toolchain Java 21
 2  ./gradlew spotlessCheck                formato
 3  ./gradlew check -x test                análisis estático + las 12 reglas propias
 4  ./gradlew compileJava                  ← invariante 1: si el esquema cambió, acá falla
 5  python3 scripts/generar_ddl.py         → diff vacío
 6  python3 scripts/verificar_boveda.py    → TODO OK (sale 1 si falla)
 7  base efímera: sql/aplicar.sql sobre base vacía + prueba de humo
 8  semillas mínimas DOS veces             → mismo estado, sin duplicados
 9  semillas de prueba DOS veces           → solo en entorno no productivo
10  rechazo: las semillas de prueba FALLAN si el entorno es producción
11  permisos: rol_auditor no escribe · cada svc_* solo ve su esquema   ← invariante 11
12  ./gradlew generateOpenApiClients       genera sin error + valida ejemplos del CU
13  ./gradlew test                         JUnit 5, unitarias
14  ./gradlew integrationTest              Testcontainers, PostgreSQL real
15  ./gradlew contractTest                 Spring Cloud Contract, por par de servicios
16  ./gradlew sagaTest                     compensación forzada paso a paso
17  ./gradlew bootJar && docker build      multietapa, sin root
18  ./gradlew e2eTest                      compose completo — solo en main
19  seguridad: dependencias, secretos, imagen
```

**El CI construye solo los servicios afectados** por el cambio, más los que dependen
de plataforma. Un cambio en `plataforma/` construye los catorce, y por eso plataforma
se toca por micro-PR.

### Cobertura como piso, no como meta

| Ámbito | Piso |
| --- | :-: |
| Global | 80 % líneas · 75 % funciones · 70 % ramas |
| `dominio/` de `nucleo-financiero`, `aportes`, `entregas`, `garantia`, `tarifas`, `cumplimiento` | 95 % líneas y ramas |
| `aplicacion/` de esos mismos | 90 % líneas |
| Criterios de aceptación de cada CU | **100 %** — cada `gherkin` de la bóveda tiene su prueba nombrada igual |
| Restricciones citadas en el CU | **100 %** — cada `R-XXX-nn` tiene una prueba de **rechazo** |

No se excluye código difícil para subir el número. La pregunta real de
[[ADR-026 Pruebas de un sistema distribuido]] sigue siendo: **¿qué del dinero no está
probado?**

---

## 7 · Estrategia de pruebas — seis niveles

| Nivel | Herramienta | Contra qué corre | Nombre | Cuánto tarda |
| --- | --- | --- | --- | --- |
| **Unitaria** | JUnit 5 + AssertJ | Nada. Funciones puras | `<Atomo>Test` | ms |
| **Integración** | JUnit 5 + Testcontainers | PostgreSQL 16 real con `sql/aplicar.sql` + semillas | `<Repo>Test`, `CU<NN>Test` | s |
| **API** | `@SpringBootTest` + MockMvc | El servicio completo | `CU<NN>WebTest` | s |
| **Contrato** | Spring Cloud Contract | El **par** productor/consumidor | `<Servicio>ContratoTest` | s |
| **Saga** | JUnit 5 + Testcontainers + dobles | Una operación que cruza servicios, con fallo forzado | `<Saga>Test` | s |
| **E2E** | Testcontainers Compose + Playwright | El stack entero | `<Flujo>E2ETest` | min |

**El nivel de contrato es el que sostiene la concurrencia.** Si el productor rompe la
compatibilidad, **su** CI falla — no el del consumidor, tres semanas después. Sin ese
nivel, «programá contra el contrato del otro carril» es una intención.

### Las siete pruebas que todo CU con dinero debe tener

1. **Camino feliz** con el criterio de aceptación de la bóveda, nombrado igual.
2. **Rechazo de restricción**: una por cada `R-XXX-nn` que el CU cita.
3. **Reintento**: misma clave de idempotencia dos veces ⇒ misma respuesta, un efecto.
4. **Concurrencia**: dos transacciones simultáneas sobre el mismo agregado ⇒ una
   gana; nunca doble efecto.
5. **Cuadre**: la suma de débitos iguala la de créditos, al centavo.
6. **Evento duplicado y fuera de orden** ⇒ un solo efecto, en todo consumidor.
7. **Compensación**: si la operación cruza servicios, se fuerza el fallo de cada paso
   y el sistema queda cuadrado.

Más, cuando aplique: **RLS negativa** (contexto ajeno ⇒ cero filas), **proveedor
caído** (`202` + trabajo con intentos), **plazo** (cambiar el calendario no mueve un
plazo ya emitido).

---

## 8 · Las 21 fases

El orden no es preferencia: cada fase **habilita** la siguiente. La columna
"Bloquea a" dice qué se cae si esta fase queda a medias.

| Fase | Nombre | Servicio | CU | Bloquea a | Documento |
| :-: | --- | --- | --- | --- | --- |
| **0** | Cimientos del repositorio | — | — | todas | [[01 Fase 0 · Cimientos del repositorio]] |
| **1** | Esquemas, roles y capa de datos | `plataforma` | — | 2–17 | [[02 Fases 1 y 2 · Capa de datos y núcleo transversal]] |
| **2** | Plataforma común y gateway | `plataforma` | — | 3–17 | ídem |
| **3** | Identidad, sesión y control de acceso | `identidad` | 01, 04, 05, 08, 09 | 4–17 | [[03 Fases 3 a 7 · Identidad, habilitación y núcleo de dinero]] |
| **4** | Habilitación: licencia, diligencia y límites | `cumplimiento` (parcial) | 02, 03, 06, 40, 46 | 5–17 | ídem |
| **5** | Contabilidad de partida doble | `nucleo-financiero` | 24 | 6–17 | ídem |
| **6** | Billetera, custodia y efectivo | `nucleo-financiero` | 10–17, 50, 57 | 7–17 | ídem |
| **7** | Tarifas, comisiones, impuestos y facturación | `tarifas` | 30–36 | 8–11 | ídem |
| **8** | Grupos, cupos, turnos y gobernanza | `grupos` | 20, 59, 60, 62–65, 68, 69 | 9–11 | [[04 Fases 8 a 11 · Circuito del pasanaku]] |
| **9** | Aportes, pagos QR, conciliación y cierre | `aportes` | 19, 21, 51, 99 | 10, 11 | ídem |
| **10** | Entregas de fondo y desembolsos | `entregas` | 18, 22, 28 | 11 | ídem |
| **11** | Garantía, incumplimiento, cobranza y sanciones | `garantia` | 23, 25–27, 29, 66, 67 | — | ídem |
| **12** | Notificaciones y comunicaciones | `notificaciones` | 80–83 | — | [[05 Fases 12 a 16 · Plataforma, reputación y cumplimiento]] |
| **13** | Transparencia y reputación | `transparencia` | 61, 70–76, 97 | — | ídem |
| **14** | Organizador y automatización | `organizador` | 90–93, 95, 96 | — | ídem |
| **15** | Auditoría, reportes, datos personales e indicadores | `auditoria` | 07, 54, 55, 58, 98 | — | ídem |
| **16** | Cumplimiento UIF/ASFI, reclamos y continuidad | `cumplimiento` | 41–45, 47–49, 52, 53, 56, 94 | — | ídem |
| **17** | Endurecimiento, rendimiento, E2E y despliegue | todos | — | — | [[06 Fase 17 · Endurecimiento, E2E y despliegue]] |

**Los 87 casos de uso del núcleo están asignados. Ninguno queda huérfano.** Los 12
restantes —contabilidad ERP (CU-100–106) y publicidad (CU-110–114)— se asignan en las
fases 18 y 19, definidas en [[17 Plan de acción secuencial · coordinación de cinco máquinas]].

### Camino crítico

```
0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 9
                              ↘ 8 ↗
```

Las fases **12 a 16** son paralelizables entre sí una vez cerrada la 11. La **17**
cierra todo.

> **Para ejecutar esto en paralelo**, las 21 fases se agrupan en **seis olas de
> carriles** en [[07 Carriles de trabajo concurrente]]: hasta cinco máquinas a la vez,
> cada una dueña de un servicio entero.

### Los dos hitos de validación temprana

| Hito | Qué valida | Cuándo |
| --- | --- | :-: |
| **CU-31** de punta a punta | Que el **stack** sostiene el dominio: dinero, tarifario congelado, partida doble, outbox e impuestos | Al cerrar la Fase 7 |
| **CU-21** con su saga | Que la **partición en servicios** es viable: cruza `aportes`, `nucleo-financiero` y `tarifas`, y tiene que compensar bien al forzar el fallo de cada paso | Al cerrar la Fase 9 |

Si CU-31 no pasa con sus criterios de aceptación como pruebas, se revisa `ADR-015`
antes de seguir. **Si CU-21 no compensa correctamente, la frontera entre servicios
está mal puesta y se revisa `ADR-014` antes de escribir los servicios restantes.**

---

## 9 · Gate de fase — el mismo para las 21

Ninguna fase se declara terminada sin las trece casillas. **No se marca una casilla
sin haber ejecutado el comando**; "debería pasar" no es evidencia (skill
`definicion-de-terminado`, y §15 del estándar de ejecución: está prohibido afirmar
"listo", "compila", "pasa las pruebas" o "es seguro" sin haberlo corrido).

- [ ] `./gradlew spotlessCheck check` en verde, sin supresiones nuevas sin comentario
      que cite el porqué
- [ ] `./gradlew compileJava` en verde tras regenerar jOOQ ← invariante 1
- [ ] `./gradlew generateOpenApiClients` genera sin error y valida contra los ejemplos
      del CU
- [ ] `./gradlew test integrationTest` en verde
- [ ] `./gradlew contractTest` en verde por cada par de servicios que se llama
- [ ] `./gradlew sagaTest` en verde por cada operación que cruza servicios
- [ ] Cada criterio de aceptación de cada CU de la fase tiene su prueba con su nombre
- [ ] Cada `R-XXX-nn` citado por esos CU tiene una prueba de **rechazo**
- [ ] La prueba de aislamiento pasa: el rol del servicio no lee ningún otro esquema
- [ ] Las piezas están declaradas por nivel y ninguna salta capas (lo verifica ArchUnit)
- [ ] `docs/` actualizado si la fase cambió el modelo, y
      `python3 scripts/verificar_boveda.py` en verde
- [ ] **El checklist de PR de §12 del estándar de ejecución, ejecutado en cada PR**
- [ ] **Los supuestos declarados** durante la fase, escritos en el informe del carril
      (regla cero: ninguno silencioso)

---

## 10 · Configuración

Cada servicio valida su configuración **al arrancar**: si falta una clave, el proceso
no levanta. Sin valores por defecto silenciosos para credenciales, umbrales ni
direcciones de proveedores.

**No hay un archivo de configuración compartido.** Cada servicio trae su
`application.yml`, y eso elimina por construcción el conflicto que un `.env.example`
común producía en cada PR.

| Clave | Quién la usa | Ejemplo | Nota |
| --- | --- | --- | --- |
| `spring.datasource.url` | todos | `jdbc:postgresql://pgbouncer:6432/aportaya` | **por PgBouncer** |
| `spring.datasource.username` | todos | `svc_tarifas` | **el rol del servicio** |
| `aportaya.datasource.lectura.url` | `auditoria` | `jdbc:postgresql://replica:5432/aportaya` | `rol_auditor`, solo lectura |
| `spring.datasource.hikari.maximum-pool-size` | todos | `5` · `20` en los de dinero | La **suma** no supera `max_connections` |
| `spring.kafka.bootstrap-servers` | todos | `kafka:9092` | |
| `aportaya.outbox.intervalo` | todos | `PT1S` | Intervalo del relevo |
| `aportaya.jwt.jwks-uri` | los trece | `http://identidad:8080/.well-known/jwks.json` | ADR-024 |
| `aportaya.jwt.emisor` / `ttl` | `identidad` | — | Solo el emisor tiene la clave privada |
| `aportaya.argon2.*` | `identidad` | — | memoria, iteraciones, paralelismo |
| `aportaya.zona-horaria` | todos | `America/La_Paz` | plazos hábiles |
| `aportaya.archivos.ruta` / `tamano-max-mb` | los que reciben evidencia | — | volumen persistente |
| `aportaya.proveedores.<x>.url` / `.clave` | el servicio que integra | — | sin valor por defecto |

---

## 10b · Las cinco restricciones nuevas (cerradas el 2026-08-13)

`docs/Restricciones.md` ganó cinco restricciones y **los casos de uso ya las citan**:
`python3 scripts/verificar_boveda.py` da **TODO OK**. Quedan acá anotadas porque
cambian el gate de tres fases:

| Restricción | Qué exige | La cita | Fase |
| --- | --- | --- | :-: |
| `R-SEG-09` | El refresco **se rota**; reusarlo revoca la familia y sus sesiones | CU-04 | 3 |
| `R-AUD-09` | Los hashes de la bitácora **los calcula la base**, no la aplicación | CU-04, CU-73 | 3 y 13 |
| `R-AUD-10` | Las cadenas se verifican en el **control diario**, no solo al auditar | CU-10, CU-73 | 6 y 13 |
| `R-BIL-19` | El reintento **devuelve la primera respuesta**, no un error | CU-10 | 6 |
| `R-BIL-20` | La partida doble cuadra **también en moneda** | CU-10 | 6 |

Total vigente: **138 restricciones definidas**, todas citadas por al menos un caso.

---

## 11 · Riesgos del plan y su mitigación

| # | Riesgo | Impacto | Mitigación | Se detecta en |
| :-: | --- | --- | --- | --- |
| 1 | Alguien mete JPA «solo para esta tabla» | Divergencia bóveda↔base y escritura por *dirty checking* sobre append-only | §4 R2 · regla de análisis estático · prohibición por ADR | Fase 1, y en cada CI |
| 2 | `SET LOCAL` en otra conexión que la transacción | **Fuga de datos entre usuarios**. El riesgo más grave del proyecto | §4 R4 · prueba negativa obligatoria por servicio | Fase 2 |
| 3 | Un servicio consulta el esquema de otro «para ir rápido» | Se pierde la frontera y la partición se vuelve decorativa | Invariante 11 · `GRANT` restringido · prueba por par | Fase 1 |
| 4 | Una saga queda a medias y nadie se entera | Descuadre contable silencioso | Prueba de compensación obligatoria · alerta de saga sin compensar | Fase 9 |
| 5 | Un importe pasa por `double` | Descuadre contable, incumplimiento | Invariante 4 · `BigDecimal` · análisis estático · prueba de cuadre | Fase 1 |
| 6 | El costo de operar quince procesos desborda al equipo | El proyecto se frena en la operación, no en el desarrollo | Manifiestos generados · `Dockerfile` plantilla · el carril levanta **su** servicio | Fase 0 y Fase 17 |
| 7 | Kafka se cae y alguien cree que se perdieron eventos | Reacción equivocada ante un incidente | El outbox está en PostgreSQL: **nada se pierde, se atrasa**. Métrica de edad del evento más viejo | Fase 2 |
| 8 | Los seeders `⚠ PROVISIONAL` se toman por definitivos | Incumplimiento regulatorio real | El campo `estado` se propaga: el arranque **advierte** por cada catálogo provisional | Fase 4 |
| 9 | La licencia sembrada `EN_TRAMITE` hace fallar todo flujo en local | Se "arregla" desactivando la validación | Semilla de **prueba** habilita la licencia; producción no. Prueba explícita de que `EN_TRAMITE` rechaza | Fase 4 |
| 10 | El cierre diario corre dos veces con dos réplicas | Asientos duplicados | ShedLock + prueba con dos réplicas levantadas | Fase 9 |
| 11 | Los servicios `garantia` y `cumplimiento` (33 y 47 tablas) desbordan su fase | Fases 11 y 16 se estiran sin control | Ambas se subdividen en sub-fases con gate propio | Fases 11 y 16 |
| 12 | La suma de los pools supera `max_connections` | El clúster rechaza conexiones y caen varios servicios a la vez | Se declara en un lugar; el arranque advierte | Fase 2 |

---

## 12 · Cómo se usa este plan

0. **Se leen [[00b Estándar de ejecución · código limpio, pruebas y calidad]] y
   [[00c Recetario · implementar un caso de uso]] una vez, completos, antes de la
   Fase 0** — y se vuelve a ellos en cada PR. Es el cómo; este documento es el qué.
1. Se ejecuta **una fase a la vez**, en orden.
2. Antes de escribir la primera línea de una fase se leen **los archivos de la bóveda
   que la fase lista**. No se implementa de memoria ni de este resumen: este plan dice
   *qué* y *dónde*; el *cómo exacto* está en el caso de uso. **Regla cero: no se
   inventa nada** — si falta algo crítico se para y se pregunta; si no es crítico, se
   declara el supuesto por escrito.
3. Por cada caso de uso, **antes** de implementar, se declaran las piezas por nivel y
   se responden por escrito las **seis** preguntas de frontera transaccional
   ([[Prompt de backend]]): qué va todo-junto-o-nada · qué queda fuera del commit ·
   cuál es la clave de idempotencia y de dónde viene · qué se bloquea y a qué
   granularidad · qué pasa si el proceso muere justo después del commit · **y qué
   pasa si esto cruza a otro servicio y el otro falla**.
4. Cada PR pasa el checklist de §12 del estándar y la revisión por riesgo de §13.
5. Se cierra la fase con el gate de §9 **ejecutado**, y se actualiza el informe del
   carril con avance, riesgos, decisiones, supuestos y desviaciones.

## Ver también

[[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00c Recetario · implementar un caso de uso]] · [[07 Carriles de trabajo concurrente]] · [[Index]] · [[_Arquitectura]] · [[_CasosDeUso]] · [[Restricciones]] · [[Cumplimiento]] · [[Stack]] · [[Estructura del repositorio]] · [[Flujo de una transacción]] · [[Prompt general de desarrollo]] · [[Prompt de backend]]
