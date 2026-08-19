---
tags:
  - moc
  - stack
titulo: "Stack — backend y frontend"
fecha_revision: 2026-08-16
---

# Stack — backend y frontend

> **Qué es este documento.** Las opciones de tecnología para implementar el sistema
> que la bóveda ya especifica, evaluadas contra las exigencias que el propio modelo
> impone. No es una lista de tecnologías de moda: cada opción se juzga por si
> sostiene o no las reglas de [[Restricciones]], [[Cumplimiento]] y [[_CasosDeUso]].

> [!important] Este documento cambió de decisión el 2026-08-16
> La versión anterior elegía **TypeScript de punta a punta** y dejaba escrito qué la
> revertiría: *«que el objetivo real a doce meses sea operar con licencia ASFI e
> integrarse con un core bancario»*. Eso se cumplió, y además apareció un segundo
> motivo que aquel documento no había previsto: **la forma de trabajo**. Cinco
> máquinas en paralelo necesitan que la propiedad del código sea un desplegable
> entero, no un directorio por acuerdo.
>
> Se conserva el razonamiento viejo en los ADR superados. No se borra: es expediente.

## Lo que el modelo le exige al stack

Antes de elegir nada: la bóveda ya cerró siete decisiones. El stack no las discute,
las tiene que soportar.

| Exigencia | De dónde viene | Qué le pide al stack |
| --- | --- | --- |
| **La base es la fuente de verdad, y su DDL es generado** | `scripts/generar_ddl.py` → 306 tablas, 633 FK | Un ORM que *administre* el esquema está descartado. El acceso a datos se genera **desde** la base (introspección/codegen), nunca al revés |
| **Una transacción por caso de uso** | `implementar-desde-boveda` | Control explícito de la frontera transaccional en la capa de aplicación. Nada de repositorios con autocommit implícito |
| **Contexto de sesión para RLS** | `app.usuario_id`, `app.rol` | Poder ejecutar `SET LOCAL` en la **misma conexión** que la transacción. Esto elimina varios ORM y todo pooling mal configurado |
| **Dinero exacto en `DECIMAL(14,2)`** | modelo relacional, partida doble | Tipo decimal real de punta a punta. Un `float` en cualquier capa es un defecto de cumplimiento, no de estilo |
| **Append-only, corrección por reverso** | `35_append_only/`, [[asiento_contable]] | El acceso a datos no puede depender de `UPDATE` para nada del libro; el código ni debería intentarlo |
| **Outbox, no llamadas dentro de la transacción** | `evento_dominio` | Encolar tiene que ser parte del `COMMIT` |
| **Plazos legales que vencen solos** | CU-43, CU-51, CU-52, CU-56 | Planificador confiable con ejecución exactamente-una-vez entre réplicas |

Y una octava, que no viene del modelo sino de cómo se construye:

| Exigencia | De dónde viene | Qué le pide al stack |
| --- | --- | --- |
| **Cinco máquinas trabajando a la vez sin conflicto de merge** | `planes/07 Carriles de trabajo concurrente` | Que la unidad de propiedad sea grande y esté aislada: build propio, configuración propia, contrato propio, despliegue propio |

Además: dos productos de frontend, no uno. La **app del participante** (billetera,
QR, aportes) y el **backoffice** (oficial de cumplimiento, soporte, contabilidad,
reportes ASFI/UIF) tienen usuarios, ritmos y requisitos distintos.

---

## La decisión

> **Java 21 + Spring Boot 3 en catorce servicios, uno por módulo de la bóveda, con
> jOOQ sobre un único PostgreSQL 16 con un esquema por servicio; outbox en la base y
> Kafka como transporte; Expo para la app de AportaYa y React + Vite para el
> backoffice.**

El detalle y el motivo de cada pieza está en [[_Arquitectura]], una decisión por
documento.

| Capa | Elección | En una línea |
| --- | --- | --- |
| Arquitectura | **Catorce servicios**, uno por módulo | Un carril posee un desplegable entero · [[ADR-014 Arquitectura de servicios]] |
| Runtime y API | Java 21 LTS · **Spring Boot 3** (MVC con hilos virtuales) | Transacción explícita por caso de uso · [[ADR-015 Lenguaje, runtime y framework]] |
| Acceso a datos | **jOOQ** generado desde la base viva · **JPA prohibido** | Query builder, no ORM: el esquema lo sigue mandando `sql/` · [[ADR-016 Acceso a datos con jOOQ]] |
| Datos | **Un clúster, un esquema y un rol por servicio** | Las 633 FK y la partida doble se conservan · [[ADR-017 Propiedad de datos por servicio]] |
| Eventos y trabajos | **Outbox en PostgreSQL** → relevo → **Kafka**; **ShedLock** para el cron | Encolar sigue siendo parte del `COMMIT` · [[ADR-018 Outbox transaccional y mensajería]] |
| Dinero | **`BigDecimal`** dentro de `Dinero`; cadena decimal en JSON | Ningún importe pasa por punto flotante · [[ADR-019 Dinero con BigDecimal]] |
| Contratos | **OpenAPI 3.1 escrito primero**; servidor y clientes generados | El contrato existe antes que la implementación · [[ADR-020 Contratos OpenAPI primero]] |
| Sesión y RLS | `SET LOCAL` en la transacción; el token del usuario cruza la red | Sin contexto no hay política de fila · [[ADR-021 Sesión, RLS y pooling]] |
| Entre servicios | Gateway sin lógica · **Resilience4j** · **saga orquestada** | El fallo parcial es explícito · [[ADR-022 Comunicación entre servicios]] |
| App del participante | **Expo / React Native** | QR, biometría, dispositivo de confianza y correcciones OTA · [[ADR-004 Frontend]] |
| Backoffice | **React + Vite**, TanStack Query/Router | Pantallas densas de cumplimiento · [[ADR-004 Frontend]] |
| Pruebas | **JUnit 5 + Testcontainers** con PostgreSQL 16 real | Los criterios de aceptación, uno a uno · [[ADR-026 Pruebas de un sistema distribuido]] |

### Por qué esta y no otra

Hay **dos** argumentos, y conviene no mezclarlos porque responden a preguntas
distintas.

**Por qué Java y no TypeScript — la exactitud del dinero.** JavaScript no tiene
decimal nativo, y en partida doble eso no es un detalle de estilo. La decisión
anterior lo contenía con tres reglas de disciplina: parser explícito para leer
`numeric` como texto, un tipo `Dinero` sobre `decimal.js`, y una regla de lint que
prohibía `number`. Funcionaba. Con `BigDecimal`, dos de las tres desaparecen porque
las resuelve el lenguaje. Y en una auditoría de sistemas o una integración con banco
pesa el stack que el auditor ya sabe leer.

**Por qué catorce servicios y no uno — la forma de trabajo.** Este es el argumento
nuevo, y es el que decidió el cambio. Todo el diseño de carriles existe para que dos
máquinas no editen el mismo archivo, y con un despliegue único eso se persigue por
convención: siete puntos de conflicto que hay que neutralizar a mano y recordar.
Con un servicio por módulo, **cinco de esos siete dejan de existir** — no se
neutralizan, no tienen dónde ocurrir. El detalle está en
[[ADR-014 Arquitectura de servicios]].

### Lo que se perdió, dicho sin adorno

El desempate que había elegido TypeScript era **escribir el contrato una vez** y que
lo consumieran la API, la app y el backoffice. Eso se pierde: el backend está en Java
y los clientes en TypeScript.

Se recupera **generando** el cliente desde la especificación OpenAPI en vez de
compartir el tipo escrito a mano. Es menos cómodo y más fiable: el contrato deja de
ser un archivo que alguien puede editar de un lado y olvidar del otro, y pasa a ser
algo que si diverge, **no compila** ([[ADR-020 Contratos OpenAPI primero]]).

### El precio, y cómo se paga

Los microservicios no son gratis. Los tres costos reales:

| Costo | Cómo se paga |
| --- | --- |
| **Quince procesos que operar** | Manifiestos generados, un `Dockerfile` plantilla, y un carril que levanta **su** servicio y no los quince ([[ADR-025 Empaquetado y despliegue de los servicios]]) |
| **Toda operación que cruza servicios necesita saga** | Se acota en el diseño: el libro contable **no se parte**, así que la operación más delicada del sistema sigue siendo una sola transacción ACID ([[ADR-017 Propiedad de datos por servicio]]) |
| **Kafka en producción** | Gestionado si se puede. El outbox sigue en PostgreSQL: si Kafka se cae, no se pierde ningún evento, se atrasa |

Y uno que no se paga: **la integridad referencial**. Al mantener un solo clúster con
un esquema por servicio, las 633 claves foráneas, las `EXCLUDE` con `btree_gist` y
las 138 restricciones siguen existiendo y las sigue haciendo cumplir el motor. Es la
diferencia entre partir el despliegue y partir el modelo, y acá solo se parte lo
primero.

### Lo único que revertiría la decisión

Que el sistema deje de construirse en paralelo —un solo equipo, una sola máquina— y
que la licencia ASFI se caiga del horizonte. Ahí los catorce servicios pasan a ser
costo sin beneficio, y la salida es replegarlos a un monolito modular en el mismo
Spring Boot: el código de dominio no cambia, cambian el empaquetado y el despliegue.

**Nada revierte `BigDecimal`.**

---

## Las alternativas evaluadas

### Opción A — Java/Kotlin + Spring Boot + jOOQ · **elegida**

| Pieza | Elección |
| --- | --- |
| Runtime / framework | JDK 21 + **Spring Boot 3** (MVC con hilos virtuales) |
| Acceso a datos | **jOOQ**, generado desde la base viva. Nada de JPA/Hibernate |
| Dinero | `BigDecimal` nativo |
| Migraciones | **`psql -f sql/aplicar.sql`** como `Job` de despliegue; sin herramienta de migración |
| Cola / outbox / cron | Outbox en PostgreSQL + Kafka; **ShedLock** para el cron |
| Contratos | **OpenAPI 3.1** escrito primero, servidor y clientes generados |
| Pruebas | JUnit 5 + Testcontainers + Spring Cloud Contract |
| Monorepo | **Gradle multiproyecto**, catálogo de versiones compartido |

**Por qué encaja.** Es el stack que un supervisor financiero, un auditor externo y un
banco corresponsal esperan encontrar, y el que domina la banca boliviana.
`BigDecimal`, `@Transactional` con propagación explícita y jOOQ generado desde el DDL
son exactamente lo que pide el modelo.

**Riesgos.** Más ceremonia y arranque más lento; equipo más caro; el frontend queda en
otro lenguaje sí o sí. Y hay que ser tajante: **JPA no**, porque el modelo append-only
con partida doble se pelea con el *dirty checking* de Hibernate. Está prohibido por
escrito en [[ADR-016 Acceso a datos con jOOQ]], no solo desaconsejado.

### Opción B — TypeScript (NestJS + Kysely)

| Pieza | Elección |
| --- | --- |
| Runtime / framework | Node 22 LTS + NestJS |
| Acceso a datos | Kysely con tipos generados por introspección |
| Dinero | `decimal.js` en dominio; nunca `number` |
| Cola / outbox / cron | Graphile Worker (cola en la misma PostgreSQL) |
| Validación | Zod en el borde, compartido con el frontend |

**Por qué encajaba.** Un solo lenguaje para backend, app y backoffice: los contratos
se escriben una vez. Fue la elección vigente hasta el 2026-08-16 y el razonamiento
completo está en [[ADR-001 Lenguaje y runtime]].

**Por qué ya no.** El decimal no nativo se multiplica por catorce procesos, y el
beneficio que la desempataba —el contrato compartido— lo disuelve la topología de
servicios: con catorce especificaciones, el cliente se genera igual.

### Opción C — Python (FastAPI + SQLAlchemy Core)

| Pieza | Elección |
| --- | --- |
| Runtime / framework | Python 3.12 + FastAPI |
| Acceso a datos | SQLAlchemy Core con tablas reflejadas |
| Driver | psycopg 3 (`Decimal` nativo) |
| Validación | Pydantic v2 |

**Por qué encaja.** Es el lenguaje de los generadores del repo (`scripts/*.py`) y
`Decimal` es nativo. Es el camino más corto a un primer flujo funcionando.

**Riesgos.** El tipado es opcional y aquí el tipado es control interno. Menor
credibilidad ante auditoría bancaria que Java, que es justo el criterio que decidió.

> **Mención aparte — Go (sqlc + pgx).** Conceptualmente sigue siendo una gran pareja
> del repo: `sqlc` genera código *desde SQL escrito a mano*, que es literalmente cómo
> está organizado `sql/`, y binarios de 20 MB con catorce servicios es una ventaja
> real. Se queda fuera por mercado laboral local y porque los decimales requieren
> biblioteca externa — que es exactamente el problema del que se está saliendo.

### Comparación

| Criterio | A · Java/Spring | B · TypeScript | C · Python |
| --- | :-: | :-: | :-: |
| Convivencia con DDL generado | Alta | Alta | Alta |
| Exactitud de dinero por diseño | **Alta** | Media | Alta |
| Control de transacción y RLS | **Alta** | Alta | Alta |
| Aislamiento entre carriles concurrentes | **Alta** | Media | Media |
| Compartir tipos con el frontend | Baja | **Alta** | Baja |
| Credibilidad ante auditoría/banca | **Alta** | Media | Media |
| Velocidad al primer caso de uso | Media | Alta | **Alta** |
| Costo y disponibilidad de equipo | Media | **Alta** | Alta |

**Elegida: la Opción A.** La **B** fue la decisión vigente hasta este cambio y sigue
siendo un stack correcto; perdió por exactitud de dinero y por aislamiento de
carriles, no por calidad. La **C** habría sido la correcta si el objetivo fuera solo
demostrar los flujos de la bóveda funcionando, sin producto.

---

## Frontend — dos productos, y por qué no cambia

El backend cambió de lenguaje; el frontend **no**. Sigue siendo **Expo para la app y
React + Vite para el backoffice** ([[ADR-004 Frontend]]).

Lo único que cambia es de dónde salen los tipos: antes se importaban del paquete de
contratos compartido; ahora **se generan desde la especificación OpenAPI** de cada
servicio, y el CI falla si el cliente generado no está al día.

Cambiar también el frontend —backoffice en Thymeleaf o Vaadin, app en Kotlin nativo—
se evaluó y se descartó: obligaría a tirar el sistema de diseño y los doce documentos
de plan de frontend ya escritos, a cambio de una uniformidad de lenguaje que no
resuelve ningún problema real del proyecto.

---

## Piezas transversales

| Área | Elección | Por qué |
| --- | --- | --- |
| Base de datos | **PostgreSQL 16** gestionada, con réplica y PITR | Ya verificado; el modelo usa `btree_gist`, `EXCLUDE`, RLS |
| Pooling | HikariCP por servicio + **PgBouncer** en modo *transaction*, y **solo `SET LOCAL`** | `SET` plano filtra el contexto RLS entre peticiones |
| Migraciones | **`psql -f sql/aplicar.sql`** como `Job`; ni Flyway ni migraciones de ORM ([[ADR-032 Aplicación del esquema]]) | La fuente de verdad son los `.puml` + el catálogo, y `sql/` se regenera: un checksum inmutable no aplica |
| Mensajería | **Kafka**, alimentado por el outbox | Retención: una auditoría puede pedir reproducir eventos de un período cerrado |
| Cron | **ShedLock** sobre PostgreSQL | El cierre diario no puede correr dos veces |
| Idempotencia | Clave del cliente/proveedor validada antes de escribir | Regla del borde, no del framework |
| Resiliencia | **Resilience4j**: timeout, reintento, cortacircuitos, mamparo | Toda llamada de red, sin excepción |
| Evidencia y archivos | Object storage con *object lock* + hash en base | Reportes UIF, respaldos de reclamo, extractos |
| Observabilidad | **Micrometer + OpenTelemetry**, logs estructurados con `usuario_id`, `CU-NN` y `x-request-id` | Con catorce servicios, la traza correlacionada deja de ser un lujo |
| Integraciones | WhatsApp Business Cloud API · pasarela QR bancaria · SIAT del SIN · proveedor KYC | Cada una detrás de una interfaz, con idempotencia en el borde |
| Entornos | **Kubernetes** con manifiestos generados; `docker compose` con perfiles en local | [[ADR-025 Empaquetado y despliegue de los servicios]] |

## Qué no usar, y por qué

- **JPA / Hibernate**, en cualquier servicio. Compite con `sql/` por la propiedad del
  esquema y su *dirty checking* es incompatible con append-only. Prohibido por
  escrito, no desaconsejado.
- **Cualquier ORM que administre el esquema** (Prisma Migrate, Django ORM, Alembic
  autogenerado): mismo conflicto, misma divergencia silenciosa.
- **Punto flotante para importes**, en cualquier capa, incluida la de presentación.
- **Publicar a Kafka dentro de la transacción.** El evento tiene que confirmar con la
  fila, no antes. Para eso está el outbox.
- **Una base por servicio.** Costaría las 633 FK, las `EXCLUDE` y la atomicidad de la
  partida doble ([[ADR-017 Propiedad de datos por servicio]]).
- **Transacciones distribuidas (XA / dos fases).** Bloqueos que sobreviven a la caída
  de un participante y un coordinador que es fallo único.
- **Un gateway con lógica de negocio.** Es el monolito volviendo por la puerta de
  atrás, y además compartido por catorce carriles.
- **Serverless con conexiones efímeras** para los flujos de dinero: RLS por sesión y
  transacciones con contexto no conviven con pools sin estado.
- **Una base documental** para el libro contable.

## Cómo validar la elección en una semana

Implementa **CU-31 (devengar y cobrar la comisión)** de punta a punta en la opción
candidata: toca dinero, tarifario congelado, partida doble, outbox e impuestos. Si el
stack sostiene ese caso con sus criterios de aceptación como pruebas —incluida la de
rechazo de cada restricción citada— sostiene el resto del sistema.

Con la arquitectura de servicios se agrega una segunda prueba, y es la que de verdad
valida **esta** decisión: **CU-21 (cobrar el aporte)**, que cruza `aportes`,
`nucleo-financiero` y `tarifas`. Si la saga compensa correctamente al forzar el fallo
de cada paso y el sistema queda cuadrado, la partición es viable. Si no, la frontera
entre servicios está mal puesta y se revisa **antes** de escribir los otros trece.

## Ver también

[[Index]] · [[_Arquitectura]] · [[ADR-014 Arquitectura de servicios]] · [[Restricciones]] · [[Cumplimiento]] · [[_CasosDeUso]] · [[Estructura del repositorio]]
