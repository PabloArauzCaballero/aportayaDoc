---
tags:
  - arquitectura
  - adr
titulo: "ADR-018 — Outbox transaccional, Kafka y trabajos programados"
estado: aceptada
fecha: 2026-08-16
---

# ADR-018 — Outbox transaccional y mensajería

> Supera a [[ADR-003 Trabajos, outbox y planificador]], que resolvía las tres cosas
> con Graphile Worker sobre la misma PostgreSQL.

## Contexto

Hay tres necesidades distintas que el ADR anterior atendía con una sola pieza, y que
con catorce servicios dejan de poder compartirla:

1. **Efectos fuera de la transacción.** Ninguna llamada de red ocurre dentro del
   `BEGIN…COMMIT` (invariante 6). El efecto se encola, y encolar tiene que ser parte
   del `COMMIT` o el sistema promete cosas que no hizo.
2. **Hechos que otros servicios necesitan saber.** Un aporte confirmado le importa a
   `grupos`, a `notificaciones`, a `transparencia` y a `garantia`. Antes eran cuatro
   módulos del mismo proceso; ahora son cuatro procesos.
3. **Plazos legales que vencen solos.** Cierre diario, reportes UIF, vencimiento de
   reclamos. Tienen que ejecutarse **exactamente una vez** aunque el servicio corra
   con tres réplicas.

[[ADR-003 Trabajos, outbox y planificador]] tenía razón en lo esencial: *encolar
tiene que confirmar con la transacción*. Y [[Stack]] prohibía las colas externas
justamente por eso. Esa prohibición sigue siendo correcta **para el punto de
escritura**, y deja de ser suficiente para el punto de distribución.

## Decisión

**La tabla de outbox se escribe dentro de la transacción; un relevo la publica en
Kafka después del `COMMIT`; los trabajos programados corren con ShedLock.**

### 1 · Escritura — sigue siendo PostgreSQL, y no se negocia

```java
@Transactional
public ResultadoAporte ejecutar(...) {
    var asiento = libro.registrar(...);          // escritura de dominio
    outbox.emitir("aportes.aporte_confirmado", carga);   // MISMA transacción
    return ...;                                   // COMMIT: o están los dos, o ninguno
}
```

El outbox es **una sola tabla, `comun.evento_dominio`**, en la que todo servicio
inserta y ninguno edita ([[ADR-017 Propiedad de datos por servicio]]). Está fuera del
esquema del servicio a propósito: si viviera dentro de `auditoria`, cada servicio
tendría que escribir en el esquema de otro; y trece copias de la misma tabla harían
imposible consultar el outbox completo. La escritura sigue ocurriendo en la **misma
transacción** que el hecho, que es la única propiedad que hay que preservar — y se
preserva porque todo está en el mismo clúster.

Si la transacción revierte, el evento no existió. **Nunca se publica a Kafka desde dentro de la transacción**: eso es
exactamente el fallo que el outbox evita, y ninguna conveniencia lo justifica.

### 2 · Distribución — Kafka, por un relevo

Un componente del propio servicio lee su outbox con
`SELECT … FOR UPDATE SKIP LOCKED`, publica en Kafka y marca la fila como publicada.
Es *al menos una vez*: puede publicar dos veces si muere entre el envío y la marca.

- **Tema:** `aportaya.<modulo>.<evento>` — el prefijo de módulo lo hace único por
  construcción, igual que las rutas y las métricas.
- **Clave de partición:** el identificador del agregado, para que los eventos de una
  misma billetera o un mismo grupo lleguen en orden.
- **Consumo idempotente, obligatorio:** cada servicio tiene su propia tabla
  `evento_consumido (id_evento, consumidor)` con clave única, **en su esquema**: lo
  que consumió es dato suyo, a diferencia de lo que emitió. Un evento repetido se
  descarta al insertar, no se procesa dos veces.
- **Cola de descartados** por tema: un evento que falla tras sus reintentos va a
  `aportaya.<modulo>.<evento>.descartados` y **abre un evento de riesgo operativo**.
  Un mensaje perdido en silencio es peor que uno que falla ruidosamente.

**Kafka y no RabbitMQ** por retención: una auditoría puede pedir reproducir los
eventos contables de un período cerrado hace meses, y con retención larga eso es
volver a consumir un tema en lugar de reconstruir desde la base.

### 3 · Trabajos programados — `@Scheduled` con ShedLock

```java
@Scheduled(cron = "0 30 23 * * *", zone = "America/La_Paz")
@SchedulerLock(name = "nucleo_financiero.cierre_diario",
               lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
public void cerrarDia() { … }
```

ShedLock toma el bloqueo **en la misma PostgreSQL**, así que la garantía de
exactamente-una-vez entre réplicas no depende de Kafka ni de un planificador
externo. El nombre del bloqueo lleva prefijo de módulo, como todo identificador
global.

**No hay un servicio `worker` separado.** Cada servicio ejecuta sus propios
trabajos y su propio relevo: un worker central volvería a ser un archivo compartido
por catorce carriles y un despliegue acoplado, que es lo que
[[ADR-014 Arquitectura de servicios]] fue a eliminar.

## Motivo

**El outbox preserva la garantía que [[Stack]] protegía.** La prohibición original
—«colas externas rompen la garantía de que el evento se encola exactamente cuando la
transacción confirma»— apuntaba a *publicar directo al broker desde el código de
negocio*. El patrón outbox conserva la garantía intacta: lo que confirma con la
transacción es una fila, y el broker solo transporta lo ya confirmado.

**Con catorce procesos hace falta un transporte.** `LISTEN/NOTIFY` y una cola en
tabla funcionan bien dentro de un proceso; entre catorce, cada consumidor tendría
que sondear la base de otro servicio, que es precisamente el acceso cruzado que
[[ADR-017 Propiedad de datos por servicio]] prohíbe.

**Al menos una vez, más consumo idempotente, es la combinación honesta.** Exactamente
una vez de punta a punta no existe en sistemas distribuidos; se consigue el efecto
haciendo idempotente al consumidor, que es una propiedad local y verificable.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Publicar a Kafka dentro de la transacción** | El fallo clásico: si el `COMMIT` falla después del envío, hay un evento sobre un hecho que no ocurrió. Prohibido, con regla de lint. |
| **RabbitMQ** | Más liviano de operar y suficiente para notificaciones y sagas. Sin retención larga: reproducir un evento contable de hace tres meses deja de ser gratis, y eso es un requisito de auditoría, no un lujo. |
| **Solo PostgreSQL, sin broker** | Obligaría a cada servicio a sondear la base de los demás, contra [[ADR-017 Propiedad de datos por servicio]]. Convertiría el clúster en el bus. |
| **Debezium sobre el WAL** | Elimina el relevo y es elegante, pero agrega Kafka Connect a la operación y ata el formato del evento al esquema de la tabla. Es la mejora que se evalúa cuando el volumen lo pida; el relevo es reemplazable por él sin tocar el código de dominio. |
| **Un servicio `worker` central** | Archivo compartido por catorce carriles y despliegue acoplado. |
| **Quartz con base propia** | ShedLock hace lo necesario con una tabla y sin planificador que administrar. |

## Consecuencias

**A favor**

- La atomicidad de «pasó el hecho» y «se avisó del hecho» se conserva exactamente
  como estaba.
- Los eventos quedan reproducibles, que es un requisito de auditoría.
- Cada servicio es dueño de sus trabajos: sin coordinación para desplegar uno.

**En contra, y hay que asumirlo**

- **Kafka hay que operarlo.** Es la pieza de infraestructura nueva más cara de esta
  arquitectura. En local va con `docker compose`; en producción es un servicio
  gestionado o tres nodos que alguien cuida.
- **Todo consumidor tiene que ser idempotente, sin excepción.** Es trabajo por
  consumidor y se verifica con una prueba obligatoria: el mismo evento dos veces
  produce un solo efecto.
- **Latencia entre el `COMMIT` y la publicación**, del orden del intervalo del
  relevo. Los flujos que no la toleran se resuelven con llamada sincrónica, no
  acelerando el relevo.
- El orden solo está garantizado por clave de partición. Un consumidor que asuma
  orden global está mal escrito, y la prueba de «fuera de orden» lo detecta.

## Cómo se verifica

- [ ] Ninguna llamada a un productor de Kafka ocurre dentro de un método
      `@Transactional`. Verificado por análisis estático.
- [ ] Toda fila de outbox tiene su transacción: revertir la transacción de prueba
      deja la tabla vacía.
- [ ] Todo evento emitido tiene al menos un consumidor registrado, y todo consumidor
      escucha un evento que alguien emite (barrido de outbox sin huérfanos).
- [ ] El mismo evento entregado dos veces produce **un** efecto.
- [ ] Con dos réplicas levantadas, un trabajo con `@SchedulerLock` corre **una** vez.
- [ ] Todo nombre de tema y de bloqueo lleva prefijo de módulo y no está duplicado.

## Ver también

[[ADR-003 Trabajos, outbox y planificador]] · [[ADR-014 Arquitectura de servicios]] · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-022 Comunicación entre servicios]] · [[Flujo de una transacción]] · [[_Arquitectura]]
