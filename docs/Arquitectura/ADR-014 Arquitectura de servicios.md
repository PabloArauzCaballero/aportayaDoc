---
tags:
  - arquitectura
  - adr
titulo: "ADR-014 — Arquitectura de servicios: catorce servicios, uno por módulo"
estado: aceptada
fecha: 2026-08-16
---

# ADR-014 — Arquitectura de servicios

## Contexto

El sistema se construye con **cinco máquinas trabajando a la vez**, cada una en su
clon, su rama y su chat (`planes/07 Carriles de trabajo concurrente`). Todo el diseño de
carriles existe para una sola cosa: que dos personas no editen el mismo archivo.

Con un despliegue único ese objetivo se persigue **por convención**. El plan lo
reconoce y enumera siete puntos de conflicto que hay que neutralizar a mano: el
archivo que lista los módulos, el `openapi.json` versionado, el barril de contratos,
el archivo de dependencias, el `.env.example`, el `docker-compose.yml` y el informe
compartido. Cada uno se resuelve con una regla que alguien tiene que recordar.

Una regla que alguien tiene que recordar, con cinco carriles y un solo revisor, es
una regla que se rompe. La pregunta que abre este ADR no es «¿monolito o
microservicios?» sino: **¿se puede hacer que esos siete archivos compartidos no
existan?**

## Decisión

**Catorce servicios Spring Boot independientes, uno por módulo de la bóveda, en un
único repositorio Gradle multiproyecto.**

| # | Servicio | Módulo de la bóveda | Prefijo de ruta |
| :-: | --- | --- | --- |
| 1 | `identidad` | 01 identidad y usuarios | `/identidad` `/usuarios` `/sesion` `/roles` |
| 2 | `grupos` | 02 grupos y turnos | `/grupos` `/turnos` `/acuerdos` |
| 3 | **`nucleo-financiero`** | 10 billetera y custodia **+** 03 contabilidad | `/billetera` `/custodia` `/puntos-atencion` `/contabilidad` |
| 4 | `aportes` | 03 aportes, pagos y QR | `/aportes` `/pagos` `/qr` `/conciliacion` |
| 5 | `entregas` | 04 entregas del fondo | `/entregas` `/desembolsos` `/cuentas-bancarias` |
| 6 | `notificaciones` | 05 notificaciones | `/notificaciones` |
| 7 | `transparencia` | 06 transparencia y reputación | `/reputacion` `/publico` `/verificar` |
| 8 | `organizador` | 07 organizador y automatización | `/organizadores` `/automatizacion` |
| 9 | `garantia` | 08 garantía e incumplimiento | `/garantia` `/incumplimientos` `/cobranza` |
| 10 | `auditoria` | 09 auditoría y reportes | `/auditoria` `/reportes` `/indicadores` |
| 11 | `tarifas` | 11 tarifas y comisiones | `/tarifas` `/comisiones` `/facturas` |
| 12 | `cumplimiento` | 12 cumplimiento ASFI y UIF | `/cumplimiento` `/uif` `/reclamos` `/licencia` |
| 13 | `erp` | 13 contabilidad ERP | `/erp` |
| 14 | `publicidad` | 14 publicidad y campañas | `/publicidad` `/campanas` `/anunciantes` |

Cada servicio es un **desplegable completo y de un solo dueño**: su
`build.gradle.kts`, su `Dockerfile`, su `application.yml`, su especificación
OpenAPI, su esquema de base ([[ADR-017 Propiedad de datos por servicio]]), sus
pruebas, su manifiesto de despliegue y su trabajo de CI. Nada de eso se comparte
con otro servicio.

Delante de los catorce hay un **gateway** (Spring Cloud Gateway) que es la única
entrada pública, y detrás de ellos una **plataforma** de bibliotecas compartidas
que ningún carril de dominio modifica.

### La excepción, y por qué existe

**`nucleo-financiero` es el único servicio que fusiona dos módulos.** La partida
doble exige que el débito, el crédito y el asiento contable ocurran en **una sola
transacción ACID**: si el libro de billetera y el asiento viven en servicios
distintos, el cuadre pasa a depender de una saga y deja de ser una garantía para
volverse una esperanza. El módulo 10 y el módulo 03-contable se despliegan juntos
porque separarlos rompería la única propiedad que este sistema no puede perder.

Todos los demás servicios **no escriben dinero**: le piden a `nucleo-financiero`
que lo escriba, con clave de idempotencia, y él responde con el asiento producido.

## Motivo

**El límite del servicio es el límite de la propiedad.** Un carril ya no «posee un
directorio por acuerdo»: posee un proceso, con su ciclo de vida entero. Los siete
puntos de conflicto del plan de carriles no se neutralizan — **cinco de ellos dejan de
existir**:

| Conflicto del plan de carriles | Qué pasa ahora |
| --- | --- |
| El archivo que registra cada módulo lo editan todos | **No existe.** Son procesos separados; nadie los registra en ninguna lista |
| `openapi.json` único, conflicto en cada PR | **No existe.** Una especificación por servicio, dentro del servicio |
| Barril de contratos compartido | **No existe.** Cada servicio publica su cliente generado |
| Archivo de dependencias único | **Se reduce a un catálogo de versiones.** El `build.gradle.kts` de cada servicio es propio |
| `.env.example` que todos amplían | **No existe.** La configuración es `application.yml` del servicio |
| `docker-compose.yml` base | Sigue compartido, pero solo la Ola 0 y la Ola 5 lo tocan |
| Un átomo compartido nuevo | Sigue siendo micro-PR a `plataforma/` |

Quedan **dos** archivos verdaderamente compartidos —el catálogo de versiones y las
bibliotecas de plataforma— contra los siete de antes, y ambos ya tenían un
mecanismo probado.

**Java y Spring, y no Node, por lo que este sistema es.** La bóveda ya había
escrito la condición que revierte la elección de TypeScript: operar con licencia
ASFI e integrarse con un core bancario. `BigDecimal` nativo elimina de raíz el
riesgo que [[ADR-005 Dinero y decimales]] tenía que contener con tres reglas de
disciplina, y es el stack que un auditor de sistemas y un banco corresponsal
bolivianos ya saben leer.

**El despliegue independiente importa acá más de lo habitual.** Un cambio de
umbral UIF toca `cumplimiento`; hoy obliga a redesplegar el sistema entero,
incluido el que mueve dinero. Con catorce desplegables, el radio de un cambio
regulatorio es un contenedor.

**Se puede porque el esquema ya está completo.** Las 307 tablas existen desde el
primer día con sus claves y sus restricciones. Un servicio no espera a otro para
tener datos con los que trabajar: siembra sus fixtures y desarrolla contra ellos.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Monolito modular en Spring Boot** | Cambia el lenguaje y no la geometría: vuelven el archivo de registro de módulos, la especificación única y el despliegue acoplado. Se gana `BigDecimal` y no se gana nada en concurrencia de trabajo, que es el motivo del cambio. |
| **Seis contextos agrupados** (identidad, dinero, pasanaku, cumplimiento, plataforma, ERP) | Menos procesos que operar, pero dos o tres carriles vuelven a compartir servicio y con ellos vuelve el conflicto de archivos. Se paga el costo de los microservicios sin cobrar el beneficio. |
| **Un servicio por caso de uso** (99 servicios) | La granularidad no la fija el caso de uso sino la **transacción**: casos de uso del mismo módulo comparten agregado y tienen que poder escribirse juntos. Partirlos convierte cada operación en una saga. |
| **Servicios en repositorios separados** | Elimina hasta el último conflicto de archivo, y a cambio rompe lo único que sostiene la coherencia: la bóveda, `sql/` y los generadores tendrían que versionarse catorce veces o publicarse como artefacto. El monorepo Gradle da el 95 % del aislamiento sin ese precio. |
| **Seguir en NestJS y solo partir el despliegue** | Mantiene el problema del decimal, que en partida doble no es estilo, y no hay ganancia de aislamiento que lo compense. |

## Consecuencias

**A favor**

- Un carril = un servicio = un proceso = una rama. El conflicto de merge deja de
  evitarse por convención y pasa a ser **estructuralmente imposible** en todo lo
  que no sea plataforma.
- `BigDecimal` nativo, `@Transactional` con propagación explícita y jOOQ generado
  desde el DDL: los invariantes 2, 4 y 5 del plan maestro dejan de depender de
  reglas de lint y pasan a estar en el lenguaje.
- El radio de un despliegue es un módulo.
- Escalado por servicio: `aportes` en día de cobro no obliga a escalar `erp`.

**En contra, y hay que asumirlo**

- **Catorce procesos que operar.** Se paga con `docker compose` para el desarrollo
  local, manifiestos generados y un gateway único
  ([[ADR-025 Empaquetado y despliegue de los servicios]]).
- **Una máquina de carril no levanta el sistema entero.** Levanta su servicio, el
  gateway y PostgreSQL; contra los demás programa por el contrato
  ([[ADR-020 Contratos OpenAPI primero]]) y prueba con dobles.
- **Toda operación que cruza servicios necesita saga y compensación**, salvo la
  escritura del libro, que queda dentro de `nucleo-financiero`. Es trabajo nuevo y
  real ([[ADR-018 Outbox transaccional y mensajería]]).
- **Se pierde el tipo compartido a mano con el frontend**, que era el desempate
  original de TypeScript. Se recupera generando el cliente desde OpenAPI, que es
  más fiable y menos cómodo.
- Latencia adicional en las llamadas entre servicios, y con ella la obligación de
  timeout y cortacircuitos en todas.

## Cómo se verifica

- [ ] `ls servicios/` devuelve exactamente los catorce nombres de la tabla.
- [ ] Ningún archivo fuera de `servicios/<nombre>/` menciona el nombre de un
      servicio, salvo el gateway y los manifiestos de despliegue.
- [ ] Ninguna clase de un servicio importa un paquete `bo.aportaya.<otro>`; solo
      `bo.aportaya.plataforma.*` y el cliente generado del otro.
- [ ] `nucleo-financiero` es el **único** que tiene permiso de escritura sobre las
      tablas del libro; los demás roles reciben `REVOKE`
      ([[ADR-017 Propiedad de datos por servicio]]).
- [ ] Toda ruta expuesta cae bajo el prefijo reservado de su servicio.

## Ver también

[[ADR-015 Lenguaje, runtime y framework]] · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-018 Outbox transaccional y mensajería]] · [[ADR-020 Contratos OpenAPI primero]] · [[ADR-025 Empaquetado y despliegue de los servicios]] · [[Estructura del repositorio]] · [[Stack]] · [[_Arquitectura]]
