---
tags:
  - moc
  - arquitectura
titulo: "Arquitectura — decisiones y su motivo"
fecha_revision: 2026-08-18
---

# Arquitectura de AportaYa

> **Qué es esta carpeta.** Las decisiones técnicas del sistema, una por documento,
> cada una con **el motivo por el que se tomó** y lo que la revertiría. No describe
> cómo se ve el código: describe por qué el código es así y qué se rompe si alguien
> lo cambia sin leer esto.

La cadena completa del proyecto es:

```
Norma → Caso de uso → Restricción → Modelo → Esquema → Arquitectura → Código
```

[[Cumplimiento]] · [[_CasosDeUso]] · [[Restricciones]] · [[_Entidades]] · `sql/` ·
**esta carpeta** · el repositorio de aplicación.

Las seis primeras capas ya existen y mandan. La arquitectura **no puede
contradecirlas**: solo elige con qué herramientas se sostienen.

## La decisión en una línea

> **Java 21 y Spring Boot 3 en catorce servicios, uno por módulo de la bóveda, sobre
> un único PostgreSQL 16 con un esquema por servicio; Expo para la app y React + Vite
> para el backoffice.**

El razonamiento completo —incluidas las alternativas evaluadas y por qué perdieron—
está en [[Stack]]. El motivo por el que son catorce y no uno está en
[[ADR-014 Arquitectura de servicios]].

> [!important] Cambio de rumbo del 2026-08-16
> Las trece primeras decisiones se tomaron para un backend **TypeScript en un solo
> despliegue**. El proyecto pasó a **Spring Boot con microservicios** para que los
> carriles de trabajo concurrente tengan propiedad exclusiva de un desplegable entero
> y el conflicto de merge deje de evitarse por convención.
>
> **Diez ADR quedaron superados y se conservan.** No se borran ni se editan: quien
> lea la decisión nueva tiene que poder ver qué se decidió antes y por qué cambió.

## Decisiones vigentes

| # | Decisión | Elección | Supera a |
| --- | --- | --- | :-: |
| [[ADR-014 Arquitectura de servicios\|014]] | Cuántos servicios y dónde está el límite de cada uno | Catorce servicios, uno por módulo; el libro contable entero en uno | — |
| [[ADR-015 Lenguaje, runtime y framework\|015]] | Lenguaje, runtime y framework de los servicios | Java 21 · Spring Boot 3 · MVC con hilos virtuales | 001 |
| [[ADR-016 Acceso a datos con jOOQ\|016]] | Cómo habla el código con las 306 tablas | jOOQ generado desde la base viva · **JPA prohibido** | 002 |
| [[ADR-017 Propiedad de datos por servicio\|017]] | Si se parte la base y cómo | Un clúster, un esquema y un rol por servicio; FK cruzadas conservadas | — |
| [[ADR-018 Outbox transaccional y mensajería\|018]] | Efectos externos, eventos entre servicios y cron | Outbox en PostgreSQL · relevo a Kafka · ShedLock | 003 |
| [[ADR-004 Frontend\|004]] | App del participante y backoffice | Expo + React/Vite | — |
| [[ADR-019 Dinero con BigDecimal\|019]] | Cómo viaja un importe por el sistema | `BigDecimal` dentro de `Dinero`; cadena decimal en JSON | 005 |
| [[ADR-020 Contratos OpenAPI primero\|020]] | Contrato entre servicios y con los clientes | OpenAPI escrito primero; servidor y clientes generados | 006 |
| [[ADR-021 Sesión, RLS y pooling\|021]] | Identidad de la sesión hasta la base, con catorce pools | `SET LOCAL` en la transacción · el token del usuario cruza la red | 007 |
| [[ADR-022 Comunicación entre servicios\|022]] | Cómo se llaman entre sí y qué pasa si algo queda a medias | Gateway sin lógica · Resilience4j · saga orquestada | — |
| [[ADR-023 Composición atómica en Java\|023]] | Cómo se descompone el código, front y back | Átomos, moléculas y organismos · verificado con ArchUnit | 009 |
| [[ADR-024 Autenticación y sesión distribuida\|024]] | Cómo se autentica y cómo llega la identidad a la base | `identidad` emite · los trece validan la firma localmente | 010 |
| [[ADR-027 Infraestructura de mensajería en el modelo\|027]] | Dónde viven el outbox, la idempotencia de consumo, el estado de saga y ShedLock | Cuatro tablas por esquema, generadas por plantilla · `comun` documentado | — |
| [[ADR-028 Mecánica de saga\|028]] | Cómo se ejecuta, recupera y compensa una saga | Orquesta el servicio del hecho · barredor con ShedLock · compensación por reverso · intervención manual a cuatro ojos | — |
| [[ADR-029 Catálogo legible por todos los servicios\|029]] | Cómo lee un servicio los umbrales, límites y tarifas que no posee | `catalogo` con lectura universal · vigencias insertadas con doble control | — |
| [[ADR-030 Revocación de sesión y validación de respaldo\|030]] | Cómo se propaga una revocación y qué pasa en el arranque en frío | Tema compactado directo (excepción única al outbox) · `GET /sesion/validez` · propagación ≤ 5 s medida | — |
| [[ADR-031 Lecturas, réplica y rol auditor\|031]] | Qué se lee de la réplica, quién cruza esquemas y con qué huella | Dos excepciones enumeradas · `BYPASSRLS` compensado con registro obligatorio · rezago con umbral | 011 |
| [[ADR-032 Aplicación del esquema\|032]] | Cómo llega `sql/` a una base, en efímero y en producción | `psql aplicar.sql` único mecanismo · diferencia revisada a cuatro ojos · Flyway descartado | — |
| [[ADR-025 Empaquetado y despliegue de los servicios\|025]] | Cómo se empaqueta y se pone en producción | Una imagen por servicio · manifiestos generados · migración como trabajo aparte | 012 |
| [[ADR-026 Pruebas de un sistema distribuido\|026]] | Qué se considera probado | JUnit 5 + Testcontainers · contrato entre servicios · prueba de saga | 008 |
| [[ADR-013 Respaldo y continuidad\|013]] | Qué se respalda y cómo se prueba que sirve | Punto en el tiempo + ensayo de restauración obligatorio | — |

## Decisiones superadas

Se conservan como expediente. **Ninguna manda**; se leen para entender qué cambió.

| # | Decía | La supera |
| --- | --- | --- |
| [[ADR-001 Lenguaje y runtime\|001]] | TypeScript · Node 22 · NestJS/Fastify | [[ADR-015 Lenguaje, runtime y framework\|015]] |
| [[ADR-002 Acceso a datos\|002]] | Kysely con tipos introspectados | [[ADR-016 Acceso a datos con jOOQ\|016]] |
| [[ADR-003 Trabajos, outbox y planificador\|003]] | Graphile Worker en la misma base | [[ADR-018 Outbox transaccional y mensajería\|018]] |
| [[ADR-005 Dinero y decimales\|005]] | `numeric` como string + `decimal.js` | [[ADR-019 Dinero con BigDecimal\|019]] |
| [[ADR-006 Contratos y validación\|006]] | Zod compartido, OpenAPI derivado | [[ADR-020 Contratos OpenAPI primero\|020]] |
| [[ADR-007 Sesión, RLS y pooling\|007]] | `SET LOCAL` + PgBouncer, un solo proceso | [[ADR-021 Sesión, RLS y pooling\|021]] |
| [[ADR-008 Pruebas\|008]] | Vitest + Testcontainers, cinco niveles | [[ADR-026 Pruebas de un sistema distribuido\|026]] |
| [[ADR-009 Composición atómica\|009]] | Los cuatro niveles en carpetas de TypeScript | [[ADR-023 Composición atómica en Java\|023]] |
| [[ADR-010 Autenticación y sesión\|010]] | Autenticación dentro de un único proceso | [[ADR-024 Autenticación y sesión distribuida\|024]] |
| [[ADR-012 Empaquetado y despliegue\|012]] | Imágenes Node para API y worker | [[ADR-025 Empaquetado y despliegue de los servicios\|025]] |
| [[ADR-011 Lecturas y réplica\|011]] | Réplica repartida sin decidir permisos; refresco "desde el worker" | [[ADR-031 Lecturas, réplica y rol auditor\|031]] |

## Documentos de referencia

| Documento | Responde |
| --- | --- |
| [[Método de arquitectura]] | **Cómo se diseña siempre**: los ocho pasos, dónde vive cada garantía, señales de mal diseño |
| [[Estructura del repositorio]] | Dónde vive cada archivo y por qué el nombre lleva `CU-NN` |
| [[Flujo de una transacción]] | Qué pasa, en orden, entre la petición y el `COMMIT` — y qué pasa cuando cruza servicios |
| [[Entornos y despliegue]] | Cómo se aplica el esquema, se siembra y se opera |
| [[Prompts/_Prompts\|Prompts generalistas]] | Los tres prompts —general, backend, frontend— que imponen la composición atómica |
| [[Lineamientos adoptados y descartados]] | Qué se tomó de los lineamientos externos y qué se eliminó por contradecir la bóveda |

## Cómo se usa esta carpeta al programar

1. Lee el caso de uso y sus restricciones (skill `implementar-desde-boveda`).
2. Sigue el [[Método de arquitectura]], sin saltar pasos.
3. Lee el ADR de la capa que vas a tocar. Si tu diseño lo contradice, **el ADR gana**
   hasta que alguien escriba uno nuevo que lo supere. **Si el ADR está marcado como
   superado, no manda: manda el que lo superó.**
4. Usa la skill de la tecnología correspondiente: `back-spring`, `datos-jooq`,
   `trabajos-outbox`, `movil-expo`, `web-backoffice`.
5. Las reglas que valen para todas: `arquitectura-atomica`, `codigo-limpio`,
   `contratos-api`, `dinero-decimal`, `pruebas-cu`, `entorno-monorepo`, y
   `revision-codigo` antes de fusionar.

## Cuándo se escribe un ADR nuevo

Cuando la decisión sea **cara de revertir**: cambia la forma del código en muchos
lugares, ata a un proveedor, o afecta cómo se garantiza una restricción. Un ADR no
se edita para cambiar de opinión: se escribe uno nuevo que **supera** al anterior y
el viejo queda marcado como superado, con la fecha. La historia de por qué se
decidió algo es evidencia, y en este proyecto la evidencia no se borra.

> El cambio del 2026-08-16 es el ejemplo grande de esta regla: diez decisiones
> superadas de una vez, ninguna borrada.

## Ver también

[[Stack]] · [[Index]] · [[Restricciones]] · [[_CasosDeUso]]
