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
| CU-07 | Ejercer derechos sobre datos personales | **Implementado** |
| CU-58 | Definir, programar y exportar un reporte | **Implementado** |
| CU-98 | Publicar el tablero de indicadores | **Implementado** |

**CU-07 no elige entre el derecho y la obligacion de conservar: reparte.** Borra lo
que ya vencio, seudonimiza lo que la ley obliga a guardar, y le devuelve al titular la
lista de lo que quedo con su base legal. Por eso el estado puede ser `PARCIAL`.

**El tablero publica, no calcula.** El valor lo deja el trabajo de calculo en
`indicador_kpi`; CU-98 lo lee unido a su `definicion_indicador` y decide tres cosas
de publicacion: si cumple la meta segun el sentido de la definicion, si la muestra
alcanza para mostrarlo sin identificar personas, y como se lee la variacion.
Calcular aca crearia un segundo lugar donde nace el mismo numero.

**Sacar datos del sistema deja rastro.** CU-58 hace que un reporte sea una operacion
con permiso, huella y vencimiento en vez de una consulta que alguien corre contra
produccion y manda por correo. Tres decisiones sostienen eso: los parametros van por
lista blanca y viajan ligados, nunca concatenados; el resultado deja un hash canonico
que dos corridas iguales reproducen; y el archivo se cifra y caduca cuando la
definicion declara datos sensibles.

El rechazo de CU-58 se registra en **otra transaccion**. Escribirlo en la misma y
despues lanzar el error de negocio haria que el `ROLLBACK` se llevara la constancia —
y en el caso del tiempo excedido no seria siquiera posible, porque una consulta
cortada deja la transaccion abortada y PostgreSQL no acepta ni un `INSERT`.

Dos divergencias entre el texto del caso de uso y el modelo, resueltas a favor del
modelo, que es el que rechaza: el estado terminal exitoso es `COMPLETADA` y no
`LISTA`, y `registro_acceso_datos` vive en `comun`, no en `auditoria`.

**Hueco declarado.** `registro_acceso_datos.usuario_afectado_id` es NOT NULL: la tabla
esta pensada para el acceso a UNA persona y un reporte alcanza a muchas. Cuando los
parametros nombran al titular se registra con su identificador; cuando no, no se
inventa un afectado — una fila con un identificador de relleno es peor que ninguna,
porque una auditoria la leeria como cierta. Queda la ejecucion, con solicitante,
parametros y huella.

## Eventos que emite

| Tema | Cuando |
| --- | --- |
| `auditoria.derecho_solicitado` | Se abre un expediente de datos personales (CU-07) |
| `auditoria.anonimizacion_planificada` | La supresion se resuelve y queda un proceso (CU-07) |
| `auditoria.reporte_ejecutado` | Un reporte termina con resultado (CU-58) |
| `auditoria.reporte_denegado` | Se rechaza por permiso o por tiempo excedido (CU-58) |
| `auditoria.reporte_descargado` | Se autoriza y cuenta una descarga (CU-58) |

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
