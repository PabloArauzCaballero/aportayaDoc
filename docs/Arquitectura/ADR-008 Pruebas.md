---
tags:
  - arquitectura
  - adr
titulo: "ADR-008 — Pruebas: qué se considera probado"
estado: superada por ADR-026
fecha: 2026-08-12
---

# ADR-008 — Pruebas: qué se considera probado

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-026 Pruebas de un sistema distribuido]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

Cada caso de uso de la bóveda termina en **criterios de aceptación**, y cada
restricción tiene una consulta de verificación. La definición de terminado de la
skill `implementar-desde-boveda` ya exige: criterios como pruebas, una prueba de
rechazo por restricción citada, prueba de reintento, prueba de vencimiento si hay
plazo legal y prueba de cuadre si hay dinero.

El riesgo específico de este sistema: **casi toda la garantía vive en la base**
—`CHECK`, `EXCLUDE`, `REVOKE`, triggers, RLS—. Una suite que corre contra dobles o
contra SQLite no prueba nada de lo que importa; prueba una simulación del sistema.

## Decisión

**Vitest + Testcontainers con PostgreSQL 16 real, con el esquema aplicado desde
`sql/aplicar.sql` y los catálogos sembrados.**

Tres niveles, alineados con la composición atómica ([[ADR-009 Composición atómica]]):

| Nivel | Qué prueba | Cómo |
| --- | --- | --- |
| **Átomo** | Funciones puras y objetos de valor (`Dinero`, cálculo de mora, fechas) | Unitaria, sin IO, rápida. Incluye pruebas de propiedad donde haya aritmética |
| **Molécula** | Repositorios, adaptadores, políticas | Contra Postgres real; verifica que la restricción **rechaza** |
| **Organismo** | El caso de uso completo, en su transacción | Un archivo `CU<NN>.spec.ts` con los criterios de aceptación, uno a uno |

Los proveedores externos (pasarela QR, WhatsApp, SIAT, KYC) se prueban contra dobles
que implementan la **interfaz de dominio** y reproducen sus fallos: timeout,
duplicado, respuesta fuera de orden.

## Motivo

**Porque probar contra dobles la parte que la base garantiza es probarse a uno
mismo.** El valor de este modelo está en que la base rechaza lo que debe rechazar; si
la suite no lo ejerce, el día que una migración pierda una restricción nadie se
entera hasta la conciliación.

**Porque el criterio de aceptación ya está escrito.** No hay que inventar qué
probar: el caso de uso lo dice. La prueba es la traducción literal, y el nombre del
archivo mantiene la trazabilidad especificación → código → prueba.

**Porque el camino feliz no es el riesgo.** Los incidentes de una billetera son
reintento duplicado, plazo vencido, redondeo, orden inverso de webhooks y saldo
insuficiente por concurrencia. La suite se escribe alrededor de eso.

**Porque el esquema es generado y debe verificarse como tal.** Levantar el
contenedor desde `sql/aplicar.sql` prueba, de paso, que el generador sigue
produciendo un esquema aplicable.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Base en memoria (SQLite/pg-mem)** | No tiene `EXCLUDE`, RLS, `btree_gist` ni los triggers del modelo: prueba otro sistema. |
| **Base compartida de desarrollo** | Estado sucio entre corridas, pruebas que fallan por lo que hizo otro. |
| **Solo pruebas unitarias con repositorios simulados** | Deja sin cubrir exactamente la capa que garantiza el dinero. |
| **Solo pruebas de extremo a extremo** | Lentas y malas para localizar la causa; se reservan para los flujos críticos completos. |

## Consecuencias

**A favor**

- Una restricción borrada por accidente hace fallar el CI.
- Las pruebas sirven de evidencia de control interno ante una auditoría.

**En contra**

- Suite más lenta: se paraleliza por esquema o por base dentro del mismo contenedor,
  y las de átomo corren aparte en cada guardado.
- El CI necesita Docker. Es un costo asumido.

## Reglas de uso

| Regla | Cómo se ve |
| --- | --- |
| Una prueba por criterio de aceptación | El nombre de la prueba **cita** el criterio |
| Toda restricción citada tiene prueba de rechazo | Se espera el error de la base, no un `if` de aplicación |
| Toda operación tiene prueba de reintento | Misma clave ⇒ misma respuesta, cero efectos nuevos |
| Todo flujo con dinero prueba el cuadre | Suma de movimientos = `0.00` y asiento equilibrado |
| Nada de `sleep` para esperar trabajos | Se drena la cola del worker de forma determinista |
| Las semillas de catálogo son parte del entorno | Sin catálogo, *denegar por omisión* hace fallar todo, y eso es correcto |

## Cómo se verifica

- [ ] `yarn test` levanta el contenedor, aplica `sql/aplicar.sql` y siembra.
- [ ] Cada `CU<NN>.spec.ts` cubre todos los criterios de su caso de uso.
- [ ] Las consultas de verificación de [[Restricciones]] devuelven cero filas al
      terminar la suite.
- [ ] Cobertura como señal, no como meta: se revisa qué **no** está probado del
      dinero, no el porcentaje.

## Ver también

[[ADR-003 Trabajos, outbox y planificador]] · [[ADR-005 Dinero y decimales]] · [[ADR-007 Sesión, RLS y pooling]] · [[Restricciones]]
