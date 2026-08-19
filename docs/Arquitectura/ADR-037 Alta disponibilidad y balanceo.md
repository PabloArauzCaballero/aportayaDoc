---
tags:
  - arquitectura
  - adr
titulo: "ADR-037 — Alta disponibilidad, redundancia y balanceo de carga"
estado: aceptada
fecha: 2026-08-19
---

# ADR-037 — Alta disponibilidad y balanceo

> Corrige la línea de [[ADR-025 Empaquetado y despliegue de los servicios]] que decía
> *«réplicas: 1 por omisión; 3 en `nucleo-financiero`, `aportes` e `identidad`»*.

## Contexto

Once de los catorce servicios estaban declarados con **una réplica**, y los otros tres
con tres. Una réplica no es un despliegue de alta disponibilidad: es un punto único de
falla con un `Deployment` alrededor. Con una sola réplica hay caída total garantizada en cuatro
situaciones **normales**, no excepcionales:

| Situación | Frecuencia | Qué pasa con 1 réplica |
| --- | --- | --- |
| Despliegue de una versión nueva | Cada merge a `main` | Ventana de caída en cada release |
| Drenaje de un nodo (mantenimiento del clúster) | Mensual | Caída hasta que reprograme |
| `OOMKilled` o reinicio por sonda | Impredecible | Caída hasta que arranque: ≤ 20 s de JVM ([[ADR-025 Empaquetado y despliegue de los servicios]]) |
| Pico de carga | Día de cobro del período | No hay a dónde repartir: se degrada o se cae |

Y falta lo de más abajo: PostgreSQL, PgBouncer y Kafka son piezas únicas en la
descripción actual. Replicar los servicios y dejar la base sola es mover el punto
único de falla, no eliminarlo.

## Decisión

**Tres niveles de criticidad, ninguno con menos de dos réplicas; redundancia también
en base, pooler y mensajería; balanceo ciego porque la API es sin estado; y un tope de
escalado atado al pool de conexiones.**

### 1 · El nivel lo impone el peor dependiente, no el dueño del servicio

| Nivel | Qué significa | Servicios |
| :-: | --- | --- |
| **N1 · Crítico en línea** | Si se cae, el usuario no puede entrar ni mover plata. No hay degradación aceptable | `gateway` · `identidad` · `nucleo-financiero` · `aportes` · `tarifas` |
| **N2 · Operación del producto** | Se cae y el producto se atrasa, no se pierde. El outbox y las colas absorben | `grupos` · `entregas` · `garantia` · `notificaciones` · `organizador` |
| **N3 · Diferible** | Puede esperar minutos sin que nadie afuera lo note | `auditoria` · `cumplimiento` · `transparencia` · `erp` · `publicidad` |

**Por qué `tarifas` es N1 y `cumplimiento` no lo es**, que es la pregunta que este
cuadro tiene que contestar sin discusión:

- [[ADR-022 Comunicación entre servicios]] pone *«¿cuánto es la comisión?»* entre los
  ejemplos de llamada **sincrónica cuya respuesta el usuario necesita**. `tarifas`
  está en el camino de un cobro: si no responde, no se cobra.
- Los umbrales y límites que hacen falta para *denegar por omisión* se leen del
  esquema `catalogo`, que **todos los servicios leen directo**
  ([[ADR-029 Catálogo legible por todos los servicios]]). Bloquear una operación por
  límite **no** requiere que `cumplimiento` esté arriba. Lo que hace `cumplimiento`
  —monitorear, alertar, armar casos— llega por evento y tolera atraso.
- `notificaciones` es N2 y no N1 porque el outbox retiene: una caída se convierte en
  **atraso**, no en aviso perdido ([[ADR-018 Outbox transaccional y mensajería]]).

**La regla que evita que este cuadro envejezca mal:**

> **Si un servicio de nivel inferior entra al camino sincrónico de uno superior, o
> sube de nivel, o la llamada deja de ser sincrónica.** No hay tercera opción, y la
> decisión se toma al escribir el contrato, no cuando se cae en producción.

### 2 · Lo que cada nivel obliga

| | **N1** | **N2** | **N3** |
| --- | :-: | :-: | :-: |
| Réplicas mínimas | **4** | **3** | **2** |
| Escalado automático (HPA) | 4 → 10 | 3 → 6 | no |
| `PodDisruptionBudget` (`minAvailable`) | 3 | 2 | 1 |
| Anti-afinidad entre réplicas | `required`, por nodo | `preferred` | `preferred` |
| Reparto entre zonas | obligatorio si hay ≥ 2 | preferido | preferido |
| `maxUnavailable` en el rolling | **0** | 0 | 1 |
| Objetivo de disponibilidad mensual | **99,9 %** (≤ 43 min) | 99,5 % (≤ 3,6 h) | 99,0 % |
| Objetivo de latencia p95 | 400 ms | 800 ms | — |

**Ningún servicio con una réplica. Ninguno.** Dos no es alta disponibilidad —es
sobrevivir a un reinicio—, pero elimina la ventana de caída *garantizada* en cada
despliegue, que es el problema de hoy.

### 3 · El tope de escalado lo fija el pool de conexiones, no el apetito

Es el punto donde la alta disponibilidad puede tumbar el sistema que pretende
proteger: escalar réplicas sin recalcular el pool agota PostgreSQL, y entonces **se
cae todo, no solo el servicio que escaló**.

```
Σ (réplicas_máximas × pool_hikari)  ≤  pgbouncer.max_client_conn
pgbouncer.default_pool_size × pools ≤  postgres.max_connections − margen
                                                                  (worker, trabajos,
                                                                   migrador, auditor)
```

Por eso el HPA **siempre tiene tope** y el tope es un dato del descriptor, no una
casilla del panel del clúster. Cambiar `replicas_max` sin tocar el pool es un rechazo
de revisión.

### 4 · Balanceo

```
Internet
   └── NGINX Ingress            TLS · límite de tasa por IP · ≥ 2 réplicas
         └── gateway            ≥ 4 réplicas · límite de tasa por identidad
               └── Service ClusterIP → endpoints sanos del servicio
```

| Decisión | Cuál | Por qué |
| --- | --- | --- |
| Afinidad de sesión | **Ninguna** | La API es sin estado: nada en memoria local que otra réplica necesite. El balanceo puede ser ciego, y por eso es simple |
| Reparto | Round-robin sobre endpoints **listos** | La `readiness` mira base y Kafka ([[ADR-025 Empaquetado y despliegue de los servicios]]): una réplica que no puede trabajar sale del reparto sola |
| Exceso de carga | **429 con `Retry-After`**, no cola infinita | Rechazar es mejor que aceptar y morir. El 429 es una respuesta correcta; la caída no |
| Aislamiento entre dependencias | Mamparo por dependencia ([[ADR-022 Comunicación entre servicios]]) | Un servicio caído no se lleva los hilos de los demás |
| Arranque | `readiness` recién cuando el pool está caliente | Sin eso, el balanceador manda tráfico a una réplica que todavía no puede responder, y el despliegue "sin caída" tiene un pico de errores |

### 5 · La redundancia de abajo — donde de verdad está el riesgo

| Pieza | Redundancia | Regla |
| --- | --- | --- |
| **PostgreSQL** | Primaria + **espera síncrona** + réplica de lectura asíncrona | Con dinero *append-only*, perder una transacción confirmada no es aceptable: `synchronous_commit = on` contra la espera. El costo es latencia de confirmación, y se asume |
| **Failover** | Automático, con RTO y RPO **declarados y ensayados** | RPO = 0 contra la espera síncrona; RTO objetivo ≤ 2 min. Un failover que nadie ensayó no es un failover ([[ADR-013 Respaldo y continuidad]]) |
| **Réplica de lectura** | La que ya define [[ADR-031 Lecturas, réplica y rol auditor]] | No participa del failover: si el rezago crece, se degrada la lectura, no la escritura |
| **PgBouncer** | ≥ 2 instancias | Un pooler único convierte catorce servicios redundantes en un sistema con un solo punto de falla |
| **Kafka** | `replication.factor = 3` · `min.insync.replicas = 2` · `acks = all` | Con menos, la redundancia es decorativa: un broker caído pierde mensajes confirmados |
| **Outbox** | No necesita redundancia propia | Vive en la base. Si Kafka se cae, el outbox **retiene** y el relevo reintenta: la caída se vuelve atraso |

### 6 · Más réplicas nunca significa más ejecuciones

Los trabajos con fecha corren **una sola vez** por bloqueo de identificador
(ShedLock), sin importar cuántas réplicas haya
([[ADR-018 Outbox transaccional y mensajería]]). Es la condición que hace seguro
escalar: si escalar un servicio
duplicara el cierre diario o el barrido de mora, la alta disponibilidad sería un
riesgo contable.

**Verificación**: escalar un servicio a N réplicas en QA y comprobar que el cierre
diario produce **un** asiento, no N.

### 7 · Degradación controlada — qué se apaga primero

Cuando hay presión y hay que elegir, el orden está escrito de antemano y no se
improvisa a las tres de la mañana:

| Orden | Se apaga | Se conserva siempre |
| :-: | --- | --- |
| 1 | Publicidad y recomendaciones | Aportar |
| 2 | Reputación, insignias y certificados | Ver saldo y movimientos |
| 3 | Exportes y reportes en línea (pasan a asíncronos) | Cobrar y acreditar |
| 4 | Búsquedas y listados largos (paginación más corta) | Ver el turno y la deuda |
| — | **Nunca**: notificar un plazo legal, registrar un aporte, entregar un fondo | |

### 8 · Presupuesto de error, que es lo que le da dientes al objetivo

Un objetivo de disponibilidad sin consecuencia es un deseo. La consecuencia es esta:

> **Cuando un servicio consume el 100 % de su presupuesto de error del mes, se congela
> el despliegue de funciones nuevas de ese servicio** hasta recuperarlo. Las
> correcciones de fiabilidad sí se despliegan; lo nuevo espera.

## Motivo

**Once servicios con una réplica hacen que cada release sea una caída programada.** No
es un riesgo teórico: es una ventana de indisponibilidad en cada merge a `main`, y con
cinco carriles fusionando eso pasa varias veces por semana.

**Replicar los servicios sin replicar la base es teatro.** El punto único de falla se
mueve una capa abajo y queda menos visible. Por eso este ADR incluye base, pooler y
mensajería, aunque sean piezas que ningún carril posee.

**Los tres niveles evitan pagar HA donde no importa.** Cuatro réplicas de
`publicidad` no compran nada; cuatro de `identidad` son la diferencia entre que la
gente pueda entrar o no. La clasificación es lo que hace que el costo sea
defendible.

**El tope atado al pool es la lección que se aprende cara.** Escalar bajo presión es
justo el momento en que agotar `max_connections` produce una caída total, y ocurre
cuando el sistema ya estaba en problemas.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Dejar 1 réplica y confiar en el reinicio rápido** | La JVM arranca en ≤ 20 s: son 20 s de caída total por cada reinicio, más la ventana entera de cada despliegue. Con `maxUnavailable: 0` y una sola réplica, el rolling ni siquiera puede avanzar. |
| **Tres réplicas para los catorce, sin niveles** | Paga 42 procesos para proteger cosas que nadie nota si se caen diez minutos, y deja a `identidad` con la misma protección que a `publicidad`. |
| **Escalado automático sin tope** | Es la forma documentada de tumbar PostgreSQL: el pico crea réplicas, las réplicas abren conexiones y la base se queda sin cupo justo cuando más se la necesita. |
| **Sesión pegajosa en el balanceador** | Ata al usuario a una réplica, arruina el drenaje y esconde estado en memoria que no debería existir. La API es sin estado a propósito. |
| **Multi-región activo-activo** | Escritura en dos regiones con dinero y RLS es un problema de consistencia que este sistema no necesita resolver todavía. La continuidad se cubre con respaldo y ensayo ([[ADR-013 Respaldo y continuidad]]). |
| **Réplica de lectura como espera de failover** | Mezcla dos funciones con requisitos opuestos: la de lectura tolera rezago, la de failover no. Un rezago de lectura pasaría a ser pérdida de datos. |

## Consecuencias

**A favor**

- Desaparece la ventana de caída garantizada en cada despliegue.
- El costo de infraestructura queda justificado servicio por servicio, y es discutible
  con un cuadro en la mano.
- Escalar deja de ser peligroso, porque el tope está atado al recurso que se agota.

**En contra, y hay que asumirlo**

- **Cuesta más plata.** El piso pasa de 20 procesos a 39 (4×N1 con gateway, 3×N2,
  2×N3). Es la decisión explícita de pagar disponibilidad, y se revisa cuando haya
  cifras de uso real.
- **La confirmación síncrona a la espera agrega latencia a cada escritura.** Se acepta
  porque el sistema es de dinero; si midiera mal, la salida es acotar qué transacciones
  la exigen, **no** apagarla para todo.
- **Nada de esto vale si no se ensaya.** Un failover que nadie probó falla el día que
  hace falta; por eso el ensayo es un entregable, no una buena intención.

## Lo que esto NO da todavía, y hay que decirlo

- **Si el entorno de arranque tiene un solo nodo, la redundancia es de proceso, no de
  nodo**: sobrevive a un `OOMKilled` y a un rolling, no a que se caiga la máquina. La
  anti-afinidad `required` de N1 **no se puede cumplir** ahí, y el generador tiene que
  fallar diciéndolo en vez de programar réplicas en el mismo nodo fingiendo que hay
  redundancia.
- **No hay continuidad entre regiones.** El plan de continuidad sigue siendo respaldo
  más ensayo de restauración.
- **No hay medición todavía.** Los objetivos de la §2 son objetivos, no observaciones:
  hasta que exista el panel, no se puede afirmar que se cumplen
  (`definicion-de-terminado`).

## Cómo se verifica

- [ ] Ningún `descriptor.yml` declara `replicas` menor a 2, ni un HPA sin tope.
- [ ] `Σ (replicas_max × pool)` cabe en el pooler, y el pooler cabe en
      `max_connections`. Lo calcula el generador y **falla** si no cierra.
- [ ] Todo servicio tiene `PodDisruptionBudget`, y el `minAvailable` es coherente con
      su nivel.
- [ ] Un servicio N3 **no** aparece en el camino sincrónico de un N1: se comprueba
      sobre los `openapi/` y los clientes generados.
- [ ] Matar una réplica N1 durante una prueba de carga: cero peticiones perdidas.
- [ ] Escalar a N réplicas: el cierre diario produce **un** asiento.
- [ ] Ensayo de failover de base con cronómetro, trimestral, con el resultado escrito
      ([[ADR-013 Respaldo y continuidad]], `respaldos-restauracion`).
- [ ] Cortar Kafka: ningún evento perdido; el outbox los entrega al volver.

## Ver también

[[ADR-025 Empaquetado y despliegue de los servicios]] · [[ADR-022 Comunicación entre servicios]] ·
[[ADR-018 Outbox transaccional y mensajería]] · [[ADR-031 Lecturas, réplica y rol auditor]] ·
[[ADR-013 Respaldo y continuidad]] · [[ADR-029 Catálogo legible por todos los servicios]] ·
`resiliencia-rendimiento` · `despliegue-contenedores` · `respaldos-restauracion` · `observabilidad`
