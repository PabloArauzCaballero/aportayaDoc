# Servicio `grupos`

Modulo 02 de la boveda — Grupos, Cupos, Turnos y Gobernanza.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `grupos` |
| **Rol de base** | `svc_grupos` |
| **Prefijos de ruta** | `/grupos` · `/turnos` · `/acuerdos` |
| **Contrato** | [`openapi/grupos.yaml`](src/main/resources/openapi/grupos.yaml) |
| **Paquete** | `bo.aportaya.grupos` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| CU-59 | Mantener el calendario de días no hábiles | ⬜ sin implementar |

| CU-60 | Sortear los turnos | ⬜ sin implementar |

| CU-63 | Proponer y votar un acuerdo | ⬜ sin implementar |

| CU-62 | Permutar turnos entre participantes | ⬜ sin implementar |

| CU-65 | Retirarse de un grupo | ⬜ sin implementar |

| CU-64 | Traspasar un cupo | ⬜ sin implementar |

| CU-69 | Invitar a un contacto y registrar sus referencias | ⬜ sin implementar |

| CU-68 | Postular a un grupo y ser emparejado | ⬜ sin implementar |

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
./gradlew :servicios:grupos:generateJooq
./gradlew :servicios:grupos:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
