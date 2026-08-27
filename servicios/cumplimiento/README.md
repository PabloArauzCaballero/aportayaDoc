# Servicio `cumplimiento`

Modulo 12 de la boveda — Cumplimiento Regulatorio y Consumidor Financiero.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `cumplimiento` |
| **Rol de base** | `svc_cumplimiento` |
| **Prefijos de ruta** | `/cumplimiento` · `/uif` · `/reclamos` · `/licencia` |
| **Contrato** | [`openapi/cumplimiento.yaml`](src/main/resources/openapi/cumplimiento.yaml) |
| **Paquete** | `bo.aportaya.cumplimiento` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| CU-46 | Verificar el alcance de la licencia | **Implementado** |
| CU-05 | Aceptar contrato de adhesión y tarifario | **Implementado** |
| CU-03 | Declaración PEP y beneficiario final | **Implementado** |
| CU-02 | Elevar nivel de debida diligencia | **Implementado** |
| CU-06 | Revisión periódica de conocimiento del cliente | **Implementado** |

**CU-05 vive acá y no en `identidad`**, aunque su ficha apunte a
`openapi/identidad.yaml`: sus dos tablas —`contrato_adhesion` y
`aceptacion_contrato`— están en este esquema, y el esquema tiene precedencia sobre
la ficha. Implementarlo en identidad exigiría leer un esquema ajeno (invariante 11).

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
./gradlew :servicios:cumplimiento:generateJooq
./gradlew :servicios:cumplimiento:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
