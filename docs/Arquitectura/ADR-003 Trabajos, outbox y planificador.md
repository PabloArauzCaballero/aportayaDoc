---
tags:
  - arquitectura
  - adr
titulo: "ADR-003 — Trabajos, outbox y planificador"
estado: superada por ADR-018
fecha: 2026-08-12
---

# ADR-003 — Trabajos, outbox y planificador

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-018 Outbox transaccional y mensajería]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

El sistema tiene tres clases de trabajo que no ocurren dentro del request:

1. **Efectos externos** de un caso de uso: WhatsApp de cobro, webhook a la pasarela,
   emisión de factura en el SIAT, envío de reportes a la UIF.
2. **Trabajos con fecha**: cierre diario (CU-51), conciliación de custodia (CU-50),
   remisión mensual de reportes (CU-43), vencimiento de plazos de reclamo (CU-52).
3. **Reintentos** de todo lo anterior, con evidencia de cada intento.

La regla de la bóveda es explícita: *outbox, no llamadas dentro de la transacción*.
El caso de uso escribe en `evento_dominio` y el efecto externo se dispara después.

## Decisión

**Graphile Worker sobre la misma PostgreSQL**, con el patrón outbox.

- El caso de uso inserta el evento y encola el trabajo **en su misma transacción**.
- Un proceso worker separado consume la cola con `FOR UPDATE SKIP LOCKED`.
- Los trabajos con fecha se declaran como *cron* del worker, con bloqueo por
  identificador para que **no corran dos veces** aunque haya varias réplicas.
- Cada intento se registra; el reintento es con retroceso exponencial y tope.

## Motivo

**Porque encolar tiene que ser atómico con el `COMMIT`.** Si la cola vive fuera de
Postgres (SQS, RabbitMQ, Redis), existen dos resultados imposibles de descartar:
transacción confirmada sin notificación, o notificación de algo que se revirtió. El
segundo es peor: un WhatsApp diciendo "recibimos tu aporte" de un pago que no
cuadró. Con la cola en la misma base, el trabajo existe **si y solo si** la
transacción confirmó.

**Porque el cierre diario no puede correr dos veces.** CU-51 sella un día
contable; una segunda ejecución en otra réplica generaría asientos duplicados. El
bloqueo por identificador de trabajo en la base resuelve esto sin coordinador
externo ni suposiciones sobre cuántas instancias hay.

**Porque el intento es evidencia.** Un reclamo por notificación no recibida o una
inspección sobre el envío de un reporte se responden con la bitácora de intentos, no
con logs de un servicio de terceros que caducan a los 30 días.

**Porque no agrega infraestructura.** Un componente menos que operar, respaldar y
monitorear; el mismo `pg_dump` respalda datos y cola.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **SQS / RabbitMQ / Redis (BullMQ)** | Rompe la atomicidad con el `COMMIT`; obliga a un patrón de confirmación en dos fases que reintroduce el problema. |
| **`cron` del sistema operativo** | Sin bloqueo entre réplicas, sin reintentos, sin evidencia. |
| **Llamada directa al proveedor dentro de la transacción** | Alarga el `COMMIT` con latencia de red; una caída del proveedor bloquea el dinero. Prohibido por la bóveda. |
| **pg-boss** | Equivalente y aceptable; se elige uno para no tener dos mecanismos. |

## Consecuencias

**A favor**

- Entrega *al menos una vez* garantizada, con la transacción como frontera.
- Un solo lugar donde mirar cuando algo no llegó.

**En contra**

- *Al menos una vez* significa que **el consumidor debe ser idempotente**: cada
  adaptador valida su clave antes de producir efecto. Es la misma regla del borde
  que ya exige la bóveda, aplicada a la salida.
- La cola compite por conexiones con la API: el worker usa su propio pool y su
  propio rol de base de datos.

## Reglas de uso

| Regla | Cómo se ve |
| --- | --- |
| El caso de uso **nunca** llama a un proveedor | Inserta en `evento_dominio` y encola |
| Un trabajo = un efecto | Nada de trabajos que hacen tres cosas y fallan en la segunda |
| Todo trabajo lleva clave de idempotencia | Derivada del evento, no generada en el worker |
| Los trabajos con plazo legal alertan antes de vencer | El vencimiento se persiste al crear, no se calcula al consultar |
| El worker corre con rol propio | Sin permisos de escritura sobre tablas append-only |

## Cómo se verifica

- [ ] Prueba: transacción revertida ⇒ el trabajo no existe en la cola.
- [ ] Prueba: el mismo evento procesado dos veces produce **un** efecto.
- [ ] Prueba: dos réplicas del worker ⇒ el cierre diario se ejecuta una sola vez.
- [ ] Ningún `await proveedor.*` dentro de un bloque transaccional (regla de lint).

## Ver también

[[ADR-001 Lenguaje y runtime]] · [[Flujo de una transacción]] · [[ADR-008 Pruebas]] · [[_CasosDeUso]]
