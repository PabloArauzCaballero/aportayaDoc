# Servicio `nucleo-financiero`

Modulo 10 de la boveda — Billetera, Custodia y Dinero Electrónico.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `nucleo_financiero` |
| **Rol de base** | `svc_nucleo_financiero` |
| **Prefijos de ruta** | `/billetera` · `/custodia` · `/puntos-atencion` · `/contabilidad` |
| **Contrato** | [`openapi/nucleo-financiero.yaml`](src/main/resources/openapi/nucleo-financiero.yaml) |
| **Paquete** | `bo.aportaya.nucleofinanciero` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| [CU-24](../../docs/CasosDeUso/CU-24%20Registrar%20el%20asiento%20contable%20de%20una%20operación.md) | Registrar el asiento contable de una operación | **Implementado** (carril 1B) |
| CU-10 – CU-17, 50, 57 | Billetera y custodia | Pendiente — carril 2A, Ola 2 |

**CU-24 no tiene endpoint.** Lo invoca otro caso de uso —el que registra el hecho
económico— pasándole el `DSLContext` de **su** transacción, que es lo que hace que el
débito y su asiento confirmen juntos (invariante 12). Quien lo llame:

```java
// dentro del @Transactional + conContexto del organismo que mueve el dinero
cu24.ejecutar(dsl, new EntradaAsiento(OrigenAsiento.PAGO, pagoId, partidas, glosa), ctx);
```

Y para corregir, nunca editar: `cu24.reversar(dsl, asientoId, motivo, ctx)` crea el
asiento inverso enlazado por `asiento_reversa_id` (R-AUD-06).

## Eventos que emite

| Tema | Cuando |
| --- | --- |
| `aportaya.nucleo_financiero.asiento_registrado` | Se confirmó un asiento cuadrado (CU-24) |
| `aportaya.nucleo_financiero.asiento_reversado` | Se corrigió un asiento con su inverso (CU-24) |

## Eventos que consume

| Tema | De quien | Efecto |
| --- | --- | --- |
| | | |

## Trabajos programados

| Bloqueo | Cron | Que hace |
| --- | --- | --- |
| | | |

## Lo que este servicio NO puede hacer

- Leer el esquema de otro servicio. No tiene `GRANT`, y jOOQ no le genero las
  clases (invariante 11).
- Escribir el libro contable, salvo que sea `nucleo-financiero` (invariante 12).
- Usar JPA (ADR-016).
- Publicar a Kafka dentro de una transaccion: para eso esta el outbox (ADR-018).

## Como se trabaja acá

```bash
docker compose --profile base up -d --wait
./gradlew :servicios:nucleo-financiero:generateJooq
./gradlew :servicios:nucleo-financiero:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
