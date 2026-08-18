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
| | *(los llena `nuevo_cu.py`)* | |

## Eventos que emite

| Tema | Cuando |
| --- | --- |
| | |

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
