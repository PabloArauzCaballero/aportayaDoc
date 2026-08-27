# Servicio `notificaciones`

Modulo 05 de la boveda — Notificaciones y Comunicaciones.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `notificaciones` |
| **Rol de base** | `svc_notificaciones` |
| **Prefijos de ruta** | `/notificaciones` |
| **Contrato** | [`openapi/notificaciones.yaml`](src/main/resources/openapi/notificaciones.yaml) |
| **Paquete** | `bo.aportaya.notificaciones` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| CU-80 | Despachar una notificación | ⬜ sin implementar |

| CU-81 | Programar recordatorios de aporte | ⬜ sin implementar |

| CU-82 | Procesar una respuesta entrante | ⬜ sin implementar |

| CU-83 | Enrutar el envío por proveedor de mensajería | ⬜ sin implementar |

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
./gradlew :servicios:notificaciones:generateJooq
./gradlew :servicios:notificaciones:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
