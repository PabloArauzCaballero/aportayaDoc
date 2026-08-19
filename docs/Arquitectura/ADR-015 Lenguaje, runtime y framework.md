---
tags:
  - arquitectura
  - adr
titulo: "ADR-015 — Lenguaje, runtime y framework: Java 21 y Spring Boot 3"
estado: aceptada
fecha: 2026-08-16
---

# ADR-015 — Lenguaje, runtime y framework

> Supera a [[ADR-001 Lenguaje y runtime]], que eligió TypeScript sobre Node 22 con
> NestJS. Aquel ADR escribió su propia condición de reversión; esta es esa condición
> cumplida.

## Contexto

[[ADR-014 Arquitectura de servicios]] parte el backend en catorce desplegables. Esa
decisión no elige lenguaje por sí sola, pero cambia el peso de los criterios:

- **El argumento que ganó la vez anterior se debilitó.** TypeScript ganó por
  compartir el contrato con la app y el backoffice escribiéndolo **una vez**. Con
  catorce servicios, el contrato ya no es un archivo compartido en un paquete: es
  una especificación por servicio, de la que hay que generar clientes igual
  ([[ADR-020 Contratos OpenAPI primero]]). El tipo compartido a mano se pierde por
  la topología, no por el lenguaje.
- **El argumento que perdió se agravó.** JavaScript no tiene decimal nativo.
  [[ADR-005 Dinero y decimales]] lo contuvo con tres reglas de disciplina y una
  regla de lint. Multiplicar eso por catorce procesos multiplica por catorce las
  oportunidades de que una de las tres se olvide en uno.
- **El objetivo declarado cambió.** El destino del producto es operar con licencia
  ASFI e integrarse con un core bancario. [[ADR-001 Lenguaje y runtime]] nombró
  exactamente ese escenario como el que lo revierte.

## Decisión

**Java 21 (LTS) con Spring Boot 3.3, Spring MVC sobre hilos virtuales.**

- Un proyecto Gradle por servicio, empaquetado como jar ejecutable.
- Paquete raíz `bo.aportaya.<servicio>`, con las cuatro capas de
  [[ADR-023 Composición atómica en Java]] como subpaquetes.
- **Un archivo de aplicación por caso de uso**, con el código en el nombre:
  `CU31DevengarComision.java`. La convención `CU<NN><VerboObjeto>` se conserva
  intacta: es lo que hace que ir de la especificación al código no requiera
  herramienta.
- La transacción se abre y se cierra en ese archivo, con `@Transactional`
  declarado ahí y en ningún otro lado.
- Gradle con Kotlin DSL, versiones en un catálogo `gradle/libs.versions.toml`.

**Java y no Kotlin.** Kotlin es mejor lenguaje y peor decisión acá: el mercado
laboral boliviano de Java es varias veces más grande, un auditor de sistemas lee
Java sin fricción, y las herramientas del ecosistema —jOOQ, generadores de OpenAPI,
analizadores— documentan Java primero. La ganancia de Kotlin es de comodidad; el
costo es de contratación y de lectura externa.

**MVC con hilos virtuales y no WebFlux.** La carga de este sistema es de
entrada/salida contra PostgreSQL y proveedores externos, exactamente lo que los
hilos virtuales resuelven sin pedir programación reactiva. WebFlux impondría un
modelo asíncrono a todo el código de dominio a cambio de un beneficio que acá no
existe, y haría ilegible el flujo de una transacción.

## Motivo

**`BigDecimal` es del lenguaje.** El invariante «ningún importe pasa por punto
flotante» deja de sostenerse con una regla de lint y pasa a sostenerse con el tipo.
En un sistema de partida doble con 138 restricciones eso no es una preferencia:
es la diferencia entre una garantía y una costumbre.

**`@Transactional` con propagación explícita.** El invariante «una transacción por
caso de uso» se vuelve verificable con un analizador estático estándar en lugar de
con una regla escrita a medida.

**jOOQ genera desde la base viva.** El invariante «el código no administra el
esquema» se conserva exactamente como estaba, con la misma forma: introspección de
la base, no declaración en el código ([[ADR-016 Acceso a datos con jOOQ]]).

**Credibilidad ante quien va a auditar.** El supervisor financiero, el auditor
externo y el banco corresponsal esperan encontrar este stack. Reduce fricción en
el trámite que el proyecto tiene por delante.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Seguir en TypeScript/NestJS** | El desempate que lo eligió (contrato escrito una vez) lo disuelve la topología de catorce servicios; el costo que tenía (sin decimal nativo) se multiplica por catorce. Cambió el balance, no la calidad del stack. |
| **Kotlin + Spring Boot** | Mejor lenguaje, peor decisión de contratación y de lectura externa. Se reevalúa si el equipo pasa a ser estable y grande. |
| **Quarkus** | Arranque y memoria mejores, y una ventaja real con catorce procesos. Se descarta por ecosistema: menos gente que lo conoce acá, y la integración de jOOQ, ShedLock y Spring Cloud Gateway está más rodada en Spring. Es la alternativa que vuelve si el costo de memoria de catorce JVM molesta. |
| **Go** | La mejor pareja conceptual de `sql/`, y binarios de 20 MB. Mercado laboral local pequeño y decimales por biblioteca externa, que es justo lo que se está yendo a buscar a Java. |
| **Spring WebFlux** | Impone programación reactiva a todo el dominio para resolver un problema de concurrencia que los hilos virtuales ya resuelven. |

## Consecuencias

**A favor**

- Exactitud de dinero por tipo, no por disciplina.
- Frontera transaccional declarativa y verificable.
- Ecosistema maduro para lo que este sistema necesita: Testcontainers,
  Resilience4j, ShedLock, Micrometer.

**En contra, y hay que asumirlo**

- **Más ceremonia y arranque más lento.** El primer caso de uso tarda más que en
  Node. Se compensa con el generador de servicio y de caso de uso, que escribe la
  estructura completa (`plan de Fase 0`).
- **Catorce JVM.** Memoria y tiempo de arranque son un costo real; el presupuesto
  de arranque y de imagen está en [[ADR-025 Empaquetado y despliegue de los servicios]].
- **El frontend queda en otro lenguaje**, definitivamente. Los tipos se generan
  desde OpenAPI y no se escriben dos veces a mano; el CI verifica que el cliente
  generado esté al día.
- Los generadores de la bóveda siguen en Python (`scripts/*.py`) y eso está bien:
  hacen lo que hacen bien y no comparten runtime con nada.

## Cómo se verifica

- [ ] Existe una clase `CU<NN>*.java` por cada caso de uso implementado.
- [ ] Ningún repositorio abre transacciones; solo participa en la del organismo.
- [ ] Cada adaptador externo tiene una interfaz de dominio y un doble de prueba.
- [ ] `java --version` es 21 y coincide con el `toolchain` declarado en Gradle y
      con la imagen base del `Dockerfile`.
- [ ] `spring.threads.virtual.enabled=true` en todos los servicios.

## Ver también

[[ADR-001 Lenguaje y runtime]] · [[ADR-014 Arquitectura de servicios]] · [[ADR-016 Acceso a datos con jOOQ]] · [[ADR-019 Dinero con BigDecimal]] · [[ADR-023 Composición atómica en Java]] · [[Stack]] · [[_Arquitectura]]
