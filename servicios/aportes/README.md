# Servicio `aportes`

Modulo 03 de la boveda — Aportes, Pagos QR y Conciliación.

> **Este README enlaza, no repite.** La especificacion esta en `docs/CasosDeUso/`;
> un dato en dos lugares diverge.

| | |
| --- | --- |
| **Esquema** | `aportes` |
| **Rol de base** | `svc_aportes` |
| **Prefijos de ruta** | `/aportes` · `/pagos` · `/qr` · `/conciliacion` |
| **Contrato** | [`openapi/aportes.yaml`](src/main/resources/openapi/aportes.yaml) |
| **Paquete** | `bo.aportaya.aportes` |

## Casos de uso

| CU | Nombre | Estado |
| --- | --- | --- |
| CU-19 | Reembolsar un pago y atender una disputa | Implementado · 19 pruebas |
| CU-21 | Cobrar el aporte del periodo | Implementado · 18 pruebas |
| CU-99 | Dar de alta un proveedor de pago y enrutar el cobro | Implementado · 17 pruebas |
| CU-51 | Ejecutar el cierre diario | **Vive en `nucleo-financiero`.** Escribe `cierre_diario` y lee `asiento_contable`, las dos de ese esquema; implementarlo aca seria escribir un esquema ajeno (invariante 11). El desvio esta declarado en [`planes/informes/carril-3A.md`](../../planes/informes/carril-3A.md) |

Los demas casos de uso del modulo 03 siguen pendientes; ninguno esta a medias.

## Eventos que emite

| Tema | Cuando |
| --- | --- |
| `aportes.aporte_cobrado` | Se acredito un pago contra una obligacion. Lleva `monto` y `participanteId`: sin ellos, `cumplimiento` no puede evaluar el umbral (R-UIF-02) ni `nucleo-financiero` cuadrar el asiento |
| `aportes.reembolso_solicitado` | Alguien pidio devolver un pago. Todavia no salio plata |
| `aportes.reembolso_ejecutado` | Se aprobo y ejecuto: `nucleo-financiero` mueve el dinero |
| `aportes.disputa_abierta` | El proveedor abrio una disputa, con su `fechaLimiteRespuesta` ya guardada |
| `aportes.proveedor_activado` | Un proveedor de pago quedo activo y empieza a recibir trafico |
| `aportes.recargo_generado` | El trabajo diario creo una obligacion de tipo `RECARGO_MORA` |

## Eventos que consume

| Tema | De quien | Efecto |
| --- | --- | --- |
| — | — | Ninguno todavia. Las confirmaciones del proveedor llegan por webhook y se anotan en `evento_consumido` con el consumidor `proveedor-pago`, que es lo que impide que un reenvio abra dos disputas |

## Trabajos programados

| Bloqueo | Cron | Que hace |
| --- | --- | --- |
| `aportes-recargos-mora` | diario | `CU21CobrarAporte.generarRecargos` — **uno por obligacion, no uno por corrida**: si el trabajo corre dos veces el mismo dia, el segundo no vuelve a cobrar. Sin politica de mora escrita no genera nada (denegar por omision, invariante 9) |

> El disparo (`@Scheduled` + ShedLock) todavia no esta cableado: el metodo existe,
> se prueba y no lo llama nadie. Declarado en el informe del carril.

## Lo que este servicio NO puede hacer

- Leer el esquema de otro servicio. No tiene `GRANT`, y jOOQ no le genero las
  clases (invariante 11).
- Escribir el libro contable, salvo que sea `nucleo-financiero` (invariante 12).
- Usar JPA (ADR-016).
- Publicar a Kafka dentro de una transaccion: para eso esta el outbox (ADR-018).

## Como se trabaja acá

```bash
docker compose --profile base up -d --wait
./gradlew :servicios:aportes:generateJooq
./gradlew :servicios:aportes:bootRun
```

Skills: `arrancar-carril` primero, despues las diecinueve de todo carril de backend.
