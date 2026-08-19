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

## Niveles de criticidad — cuánta redundancia lleva cada servicio

De [[ADR-037 Alta disponibilidad y balanceo]]. **El nivel no lo elige el dueño del
servicio: lo impone su peor dependiente sincrónico.**

| | **N1** crítico en línea | **N2** operación | **N3** diferible |
| --- | :-: | :-: | :-: |
| Servicios | `gateway` `identidad` `nucleo-financiero` `aportes` `tarifas` | `grupos` `entregas` `garantia` `notificaciones` `organizador` | `auditoria` `cumplimiento` `transparencia` `erp` `publicidad` |
| Réplicas | 4 → 10 | 3 → 6 | 2 |
| `PodDisruptionBudget` | 3 | 2 | 1 |
| Disponibilidad mensual | 99,9 % | 99,5 % | 99,0 % |
| Latencia p95 | 400 ms | 800 ms | — |

**Ninguno con una réplica.** Una réplica es una ventana de caída garantizada en cada
despliegue, en cada drenaje de nodo y en cada `OOMKilled`.

Se declara en `servicios/<x>/descriptor.yml` (`nivel` y `nivel_porque`) y
`python3 scripts/generar_k8s.py` **rechaza** lo que no cierre.

## Pools de conexiones

- El tamaño se calcula contra el **máximo de réplicas**, no contra una. Es la regla
  que evita que escalar tumbe la base, y la verifica el generador:

  ```
  Σ (replicas_max × pool_hikari)      ≤  pgbouncer.max_client_conn
  pgbouncer.default_pool_size × pools ≤  postgres.max_connections − margen
  ```

  **Subir `replicas.max` sin recalcular el pool es un rechazo de revisión**: el pico
  de carga es justo el momento en que agotar `max_connections` cae todo, no solo el
  servicio que escaló.
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

## Degradación controlada — el orden está escrito de antemano

Cuando hay presión y hay que elegir, no se improvisa a las tres de la mañana
([[ADR-037 Alta disponibilidad y balanceo]] §7):

```
se apaga primero →  publicidad · reputación · exportes en línea · listados largos
nunca se apaga   →  aportar · ver saldo · cobrar y acreditar · avisar un plazo legal
```

Y bajo exceso de carga se responde **429 con `Retry-After`**, no se encola sin
límite: rechazar es una respuesta correcta, caerse no.

## Presupuesto de error — lo que le da dientes al objetivo

Un objetivo de disponibilidad sin consecuencia es un deseo. La consecuencia: **cuando
un servicio consume el 100 % de su presupuesto de error del mes, se congela el
despliegue de funciones nuevas de ese servicio** hasta recuperarlo. Las correcciones
de fiabilidad sí se despliegan.

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
- Un servicio con una réplica "porque es chico".
- Un HPA sin tope.
- Dar por bueno un failover que nadie ensayó.

## Ver también

`observabilidad` · `idempotencia-reintentos` · `lecturas-proyecciones` ·
`proveedores-externos` · `trabajos-outbox` · `datos-jooq` · `despliegue-contenedores`
