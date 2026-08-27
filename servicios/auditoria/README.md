# Servicio `auditoria`

Modulo 09 de la boveda — Auditoría, Reportes y Cumplimiento.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `auditoria` |
| **Rol de base** | `svc_auditoria` |
| **Prefijos de ruta** | `/auditoria` · `/reportes` · `/indicadores` |
| **Contrato** | [`openapi/auditoria.yaml`](src/main/resources/openapi/auditoria.yaml) |
| **Paquete** | `bo.aportaya.auditoria` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| CU-98 | Publicar el tablero de indicadores | **Implementado** |

**El tablero publica, no calcula.** El valor lo deja el trabajo de calculo en
`indicador_kpi`; CU-98 lo lee unido a su `definicion_indicador` y decide tres cosas
de publicacion: si cumple la meta segun el sentido de la definicion, si la muestra
alcanza para mostrarlo sin identificar personas, y como se lee la variacion.
Calcular aca crearia un segundo lugar donde nace el mismo numero.

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
./gradlew :servicios:auditoria:generateJooq
./gradlew :servicios:auditoria:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
