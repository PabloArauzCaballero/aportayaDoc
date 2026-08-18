---
tags:
  - arquitectura
  - adr
titulo: "ADR-026 — Pruebas: JUnit 5, Testcontainers y contrato entre servicios"
estado: aceptada
fecha: 2026-08-16
---

# ADR-026 — Pruebas de un sistema distribuido

> Supera a [[ADR-008 Pruebas]], que fijaba cinco niveles sobre un único proceso.

## Contexto

La pregunta de [[ADR-008 Pruebas]] sigue siendo la correcta y no la reemplaza
ninguna métrica: **¿qué del dinero no está probado?** Lo que cambia es dónde puede
esconderse la respuesta.

Con un proceso, una prueba de caso de uso cubría el caso entero. Con catorce
([[ADR-014 Arquitectura de servicios]]), la misma prueba cubre **un tramo** y deja
fuera tres cosas nuevas: que el contrato que el servicio publica sea el que sus
consumidores esperan, que el consumidor de un evento tolere recibirlo dos veces y
fuera de orden, y que una saga que falla a la mitad deje el sistema cuadrado.

## Decisión

**JUnit 5 con Testcontainers sobre PostgreSQL 16 real, en seis niveles.** El nivel de
contrato deja de ser una validación de ejemplos y pasa a ser una prueba entre
servicios; se agrega el nivel de saga.

| Nivel | Herramienta | Contra qué corre | Nombre | Dura |
| --- | --- | --- | --- | --- |
| **Unitaria** | JUnit 5 + AssertJ | Nada. Funciones puras de `dominio/` | `<Atomo>Test` | ms |
| **Integración** | JUnit 5 + Testcontainers | PostgreSQL 16 real con `sql/aplicar.sql` y semillas mínimas | `<Repo>Test`, `CU<NN>Test` | s |
| **API** | `@SpringBootTest` + MockMvc | El servicio completo sobre la base de Testcontainers | `CU<NN>WebTest` | s |
| **Contrato** | Spring Cloud Contract | El **par** de servicios: productor y consumidor | `<Servicio>ContratoTest` | s |
| **Saga** | JUnit 5 + Testcontainers + dobles | Una operación que cruza servicios, con fallo forzado en cada paso | `<Saga>Test` | s |
| **E2E** | Testcontainers Compose + Playwright | El stack entero en Docker | `<Flujo>E2ETest` | min |

### Las pruebas de contrato son las que sostienen la concurrencia

El productor genera, desde su OpenAPI, un conjunto de contratos verificados; el
consumidor prueba contra un doble generado del **mismo** contrato. Si el productor
rompe la compatibilidad, **su** CI falla — no el del consumidor, semanas después.

Esto es lo que permite que un carril programe contra un servicio que todavía no
existe sin que la integración sea una sorpresa al final. Sin este nivel, la promesa
de [[ADR-020 Contratos OpenAPI primero]] es una intención.

### Las pruebas obligatorias por caso de uso

Las cinco de siempre, más dos que la distribución hace necesarias:

1. **Camino feliz**, con el criterio de aceptación de la bóveda y su mismo nombre.
2. **Rechazo de restricción**, una por cada `R-XXX-nn` que el caso de uso cita.
3. **Reintento**: misma clave de idempotencia dos veces ⇒ misma respuesta, un efecto.
4. **Concurrencia**: dos transacciones sobre el mismo agregado ⇒ una gana; nunca
   doble efecto.
5. **Cuadre**: la suma de débitos iguala la de créditos, al centavo.
6. **Evento duplicado y fuera de orden** ⇒ un solo efecto, en todo consumidor.
7. **Compensación**: en toda operación que cruza servicios, se fuerza el fallo de
   cada paso y el sistema queda cuadrado.

Y cuando aplique: **RLS negativa** (contexto ajeno ⇒ cero filas), **proveedor caído**
(responde dentro del presupuesto), **plazo** (cambiar el calendario no mueve un plazo
ya emitido).

### Qué NO se hace

| No | Por qué |
| --- | --- |
| **Base en memoria** (H2) | El modelo usa `EXCLUDE`, `btree_gist`, RLS y `numeric`. Una base que no los tiene prueba otro sistema |
| **Doblar el repositorio** en la prueba del caso de uso | Lo que hay que probar es que la restricción rechaza. Un doble siempre acepta |
| **Levantar los catorce servicios** para probar uno | Ahí está el contrato. El E2E es el único que levanta todo, y corre en `main` |
| **Contenedor por prueba** | Uno por clase, reutilizado, con la base restaurada entre pruebas |

### Cobertura como piso

| Ámbito | Piso |
| --- | :-: |
| Global | 80 % líneas · 75 % funciones · 70 % ramas |
| `dominio/` de los servicios de dinero y cumplimiento | 95 % líneas y ramas |
| `aplicacion/` de esos mismos | 90 % líneas |
| Criterios de aceptación de cada caso de uso | **100 %** — cada `gherkin` tiene su prueba con el mismo nombre |
| Restricciones citadas | **100 %** — cada `R-XXX-nn` tiene prueba de **rechazo** |

No se excluye código difícil para subir el número.

## Motivo

**Testcontainers con PostgreSQL real ya era la decisión correcta y lo sigue siendo**,
y ahora más: con catorce esquemas y catorce roles, los permisos y las políticas de
fila son parte de lo que hay que probar, y solo existen en PostgreSQL.

**El nivel de contrato existe porque la integración dejó de ser gratis.** En un
proceso, el compilador verificaba que el módulo A llamara bien al módulo B. Entre
procesos no verifica nadie, y descubrirlo en el E2E es descubrirlo tarde y con el
peor mensaje de error posible.

**El nivel de saga existe porque el fallo parcial dejó de ser hipotético.** Una
operación que cruza tres servicios tiene tres formas de quedar a medias, y ninguna se
prueba sola.

**JUnit 5 y no Spock ni TestNG**: es lo que Spring Boot asume, lo que Testcontainers
documenta primero y lo que cualquiera que llegue al proyecto ya sabe leer.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Base en memoria para ir rápido** | Prueba un sistema que no es este. Rechazado también en [[ADR-008 Pruebas]]. |
| **Pact** para contratos | Excelente y con un servidor de contratos que administrar. Spring Cloud Contract se integra con Gradle y con la especificación que ya existe, sin infraestructura nueva. Vuelve a la mesa si hay consumidores fuera del repositorio. |
| **Solo E2E, sin nivel de contrato** | Detecta lo mismo, semanas después, y en el peor lugar. |
| **Dobles de los otros servicios escritos a mano** | Divergen del contrato real en silencio, que es exactamente el fallo que el nivel de contrato existe para impedir. |
| **Un entorno compartido de integración** | Cinco carriles pisándose en un entorno común. Es lo contrario de todo el diseño de este proyecto. |

## Consecuencias

**A favor**

- La integración se verifica en el CI del que la rompe, no del que la sufre.
- El fallo parcial es una prueba y no una esperanza.
- La suite de un carril corre sin los otros trece servicios.

**En contra, y hay que asumirlo**

- **La suite es más lenta.** Testcontainers levanta PostgreSQL, y ahora también Kafka
  para los consumidores. Se mitiga reutilizando contenedores por clase y corriendo el
  E2E solo en `main`.
- **Escribir contratos verificados es trabajo por par de servicios.** Se acota:
  solo los pares que realmente se llaman, no los 91 posibles.
- **Las pruebas de saga son las más difíciles del proyecto.** Forzar el fallo del
  paso 2 de 4 y comprobar el cuadre exige diseñar la prueba, no solo escribirla.
- La cobertura de un caso de uso distribuido es la suma de varias pruebas, y hay que
  mirarla como suma para no engañarse con el número de una sola.

## Cómo se verifica

- [ ] Ninguna prueba usa base en memoria.
- [ ] Cada criterio de aceptación de la bóveda tiene su prueba, con el mismo nombre.
- [ ] Cada `R-XXX-nn` citado tiene una prueba de rechazo.
- [ ] Cada par de servicios que se llama tiene contrato verificado en el productor.
- [ ] Cada consumidor de evento tiene prueba de duplicado y de fuera de orden.
- [ ] Cada saga tiene prueba de compensación por cada paso que puede fallar.
- [ ] El número de pruebas activas nunca baja: ninguna se desactiva para que pase el
      build.

## Ver también

[[ADR-008 Pruebas]] · [[ADR-014 Arquitectura de servicios]] · [[ADR-020 Contratos OpenAPI primero]] · [[ADR-022 Comunicación entre servicios]] · [[Restricciones]] · [[_Arquitectura]]
