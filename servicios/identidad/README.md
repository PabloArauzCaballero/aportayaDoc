# Servicio `identidad`

Modulo 01 de la boveda — Identidad, Usuarios y Seguridad.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `identidad` |
| **Rol de base** | `svc_identidad` |
| **Prefijos de ruta** | `/identidad` · `/usuarios` · `/sesion` · `/roles` |
| **Contrato** | [`openapi/identidad.yaml`](src/main/resources/openapi/identidad.yaml) |
| **Paquete** | `bo.aportaya.identidad` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| CU-04 | Autenticar con MFA y registrar dispositivo | ⬜ sin implementar |

| CU-08 | Asignar y revocar roles de operador | ⬜ sin implementar |

| CU-09 | Cambiar credenciales y solicitar la baja | ⬜ sin implementar |

| CU-01 | Registro y apertura de billetera | ⬜ sin implementar |

| CU-05 | Aceptar contrato de adhesión y tarifario | ⬜ sin implementar |

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
./gradlew :servicios:identidad:generateJooq
./gradlew :servicios:identidad:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
