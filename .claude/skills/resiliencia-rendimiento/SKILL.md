---
name: resiliencia-rendimiento
description: "Hacer que AportaYa aguante fallos y carga: timeouts, reintentos con jitter, circuit breaker, pools de conexiones, apagado controlado, backpressure, N+1, paginación, streaming y pruebas de carga con medición. Úsala al integrar un proveedor, cuando algo esté lento, al dimensionar réplicas o pools, y antes de optimizar cualquier cosa."
---

# Resiliencia y rendimiento

Dos reglas de entrada:

> **Toda llamada de red tiene timeout.** Sin timeout, un proveedor lento no falla:
> agota el pool y tumba el sistema entero.

> **No se optimiza sin medir.** Primero baseline y evidencia; después el cambio; y
> después la comparación. Optimizar por intuición mueve el problema de lugar.

## Resiliencia

| Mecanismo | Cuándo | Cuidado |
| --- | --- | --- |
| **Timeout** | Siempre, en toda salida de red y en toda consulta pesada | Más corto que el timeout del cliente que te llama |
| **Reintento** | Solo fallos transitorios de operaciones idempotentes | Nunca sobre errores de validación o de negocio |
| **Retroceso exponencial + jitter** | En todo reintento | Sin jitter, mil clientes reintentan a la vez |
| **Circuit breaker** | Proveedor que puede degradar el sistema | Con estado semiabierto y métrica visible |
| **Backpressure** | Colas y cargas masivas | Rechazar es mejor que aceptar y morir |
| **Apagado controlado** | Siempre | Terminar el trabajo en curso, dejar de aceptar nuevos |
| **Límite de concurrencia** | Worker e integraciones | Evita que un pico agote la base |

**Nunca se reintenta una escritura sin idempotencia previa** (`idempotencia-reintentos`):
el reintento sin clave es la forma más común de duplicar dinero.

## Pools de conexiones

- El tamaño se calcula contra el **máximo de réplicas**, no contra una: `réplicas ×
  pool` no puede superar `max_connections` menos el margen del worker y las tareas.
- El worker tiene su propio pool y su propio rol; no compite con la API.
- Métrica obligatoria: conexiones en uso y **tiempo de espera por conexión**. Espera
  creciente significa que falta pool o sobran consultas lentas, y son problemas
  distintos.
- Con PgBouncer en modo transacción: sin sentencias preparadas globales ni
  `LISTEN/NOTIFY` a través del pooler ([[ADR-007 Sesión, RLS y pooling]]).

## Consultas

| Problema | Qué hacer |
| --- | --- |
| N+1 | Traer en una consulta con `join` o agregación; se detecta en las trazas |
| Listado sin límite | Paginación del servidor, siempre, incluso en el backoffice |
| Consulta que crece con los datos | Índice adecuado o proyección (`lecturas-proyecciones`) |
| Agregados en el proceso | Se hacen en SQL: exacto y sin traer diez mil filas |
| Exportes grandes | Streaming o generación asíncrona por trabajo, nunca en el request |
| Consulta pesada de cumplimiento | Contra la réplica de lectura |

Antes de dar por buena una consulta de un flujo caliente: `EXPLAIN (ANALYZE, BUFFERS)`
sobre un conjunto de datos representativo, no sobre diez filas de desarrollo.

## Escalabilidad

- La API es **sin estado**: nada en memoria local que otra réplica necesite.
- Bloqueos y contadores compartidos viven en la base, no en el proceso.
- El worker escala aparte de la API, y los trabajos con fecha se bloquean por
  identificador para no duplicarse.
- Los picos previsibles del dominio —día de cobro del período, cierre de mes— se
  prueban antes de que ocurran.

## Medición

Se mantiene, no se improvisa:

- **Baseline** por operación crítica (aporte, recarga, retiro, entrega, listado).
- **Conjunto de datos representativo**, no vacío: un grupo con 12 participantes y 24
  períodos no se parece a la producción del mes seis.
- **Prueba de carga** con k6 u equivalente: concurrencia, agotamiento de pool,
  tormenta de reintentos, caída del proveedor, apagado y recuperación.
- **Presupuesto de rendimiento** por operación, y comparación antes/después.

## Indicadores de servicio

Definir y vigilar: disponibilidad, latencia p50/p95/p99, tasa de error, frescura de
los trabajos y proyecciones, y RPO/RTO (`respaldos-restauracion`). Las alertas
tienen que ser accionables y tener runbook; alertar por cada error aislado entrena al
equipo a ignorar las alertas.

## Antipatrones

- Llamada externa sin timeout.
- Reintentar un error de negocio.
- Reintentos sin jitter ni tope.
- Cachear sin definir cómo se invalida.
- Subir el pool para tapar consultas lentas.
- "Optimizamos por las dudas" sin baseline.
- Probar rendimiento solo en la máquina de quien programa.

## Ver también

`observabilidad` · `idempotencia-reintentos` · `lecturas-proyecciones` ·
`proveedores-externos` · `trabajos-outbox` · `datos-jooq` · `despliegue-contenedores`
