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
| CU-54 | Registrar un evento de riesgo operativo | **Implementado** |
| CU-55 | Gestionar un incidente de seguridad | **Implementado** |

**CU-05 vive acá y no en `identidad`**, aunque su ficha apunte a
`openapi/identidad.yaml`: sus dos tablas —`contrato_adhesion` y
`aceptacion_contrato`— están en este esquema, y el esquema tiene precedencia sobre
la ficha. Implementarlo en identidad exigiría leer un esquema ajeno (invariante 11).

**CU-54 y CU-55 también viven acá y no en `auditoria`**, por lo mismo: las tablas de
riesgo operativo, incidentes, activos de información y contratos de terceros están en
este esquema. Las fichas de ambos casos de uso se corrigieron para decirlo.

**La pérdida neta no la calcula la aplicación.** `evento_riesgo_operativo.perdida_neta`
es una columna `GENERATED`: el caso de uso la lee de vuelta después de insertar en vez
de confiar en su propia resta. Si algún día dejaran de coincidir, la que vale es la de
la base (`R-RIS-02`).

**Los tres relojes de un incidente se guardan, no se recalculan.** Contener, reportar y
notificar tienen plazos distintos y dueños distintos; recalcularlos al consultar los
haría moverse solos, y bastaría cambiar la política el mes que viene para que un
incidente de hace tres meses apareciera reportado en plazo cuando no lo estuvo.

**Los dos controles diarios corren sin lock de planificador, y es correcto igual.** Las
filas se toman `FOR UPDATE SKIP LOCKED` y el código del hallazgo se deriva de lo que lo
originó, así que dos réplicas se reparten el trabajo y una segunda corrida no abre un
segundo expediente. Un lock ahorraría trabajo repetido; no es lo que hace correcto esto,
y confundir las dos cosas es como se escriben los controles que duplican expedientes el
día que el lock falla.

**Hueco declarado.** Los plazos de reporte de incidente por severidad viven en
configuración, no en el catálogo de la bóveda: el modelo no tiene tabla para ellos. Los
valores por omisión son deliberadamente cortos — si nadie declara la política real, el
sistema exige más de lo que la norma pide en vez de menos.

## Eventos que emite

| Tema | Cuando |
| --- | --- |
| `riesgo.evento_registrado` | Se registra una pérdida operativa (CU-54) |
| `plan.vencido` | Un plan de acción venció sin cierre y se abrió hallazgo (CU-54) |
| `seguridad.incidente_detectado` | Se abre el expediente de un incidente (CU-55) |
| `seguridad.incidente_reportado` | Se reporta al organismo, en plazo o fuera de él (CU-55) |
| `seguridad.titulares_notificados` | Se avisa a las personas afectadas (CU-55) |
| `seguridad.plazo_reporte_vencido` | Venció el plazo sin reportar y se abrió hallazgo (CU-55) |

## Eventos que consume

| Tema | De quien | Efecto |
| --- | --- | --- |
| | | |

## Trabajos programados

| Bloqueo | Cron | Que hace |
| --- | --- | --- |
| `SKIP LOCKED` por fila | `0 15 6 * * *` | Escala a hallazgo los planes de acción vencidos (CU-54) |
| `SKIP LOCKED` por fila | `0 45 6 * * *` | Escala a hallazgo los incidentes con plazo de reporte vencido (CU-55) |

Corren a horas distintas aunque los dos escriban hallazgos: separarlos hace que, cuando
algo falle, se sepa cuál de los dos falló sin desenredar dos corridas simultáneas en la
misma bitácora.

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
