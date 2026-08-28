---
tags:
  - plan
  - informe
  - carril
titulo: "Carril 3A — 03_aportes"
ola: 3
fase: 3
modulo: 03_aportes
rama: pablo/feature/carril-3A-aportes
estado: en curso
---

# Carril 3A — aportes

**Fase** 3 · **Casos de uso** 19, 21, 99 (+ 51, mudado) · **Máquina** mac

> Este archivo lo escribe **solo este carril**.

## Casos de uso

| CU | Contrato | Dominio | Infra | Aplicación | HTTP | Pruebas | Gate |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| CU-21 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ 18 | ✅ |
| CU-19 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ 19 | ✅ |
| CU-99 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ 17 | ✅ |
| CU-51 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ 11 | ✅ |

La capa `web/` queda vacía a propósito: el contrato está escrito y los clientes se
generan de él, pero el arranque HTTP del servicio es de la fase de exposición. Nada de
lo que hay depende de que exista.

## CU-51 se mudó a `nucleo-financiero`

`docs/CasosDeUso/CU-51` dice `## Contrato · openapi/aportes.yaml`, y `planes/07` lo
asigna a este carril. **No se puede implementar acá.** Escribe `cierre_diario` y lee
`asiento_contable`; las dos tablas están en el esquema `nucleo_financiero`, y
escribirlas desde `aportes` es el invariante 11. Es la misma mudanza que CU-40, por la
misma razón.

Consecuencias, para que nadie las descubra después:

- La clase vive en `servicios/nucleo-financiero/.../CU51EjecutarCierreDiario.java`.
- `scripts/verificar_criterios.py` sigue la cabecera del CU y por eso reporta
  **«CU-51 (aportes) pendiente»**. No lo está: está implementado y probado en el otro
  servicio. Corregir la cabecera es tocar `docs/`, que es troncal — va como micro-PR.
- El conteo de excepciones de conciliación **entra como parámetro**. Esas filas viven
  en `aportes.excepcion_conciliacion` y `nucleo-financiero` no las lee. Quien dispara
  el cierre las trae resueltas.

## Huecos declarados

Regla cero: ninguno silencioso.

| # | Qué falta o diverge | Dónde | Qué se hizo |
| :-: | --- | --- | --- |
| H-1 | El CU-19 nombra los motivos `MONTO_ERRONEO`, `NO_RECONOCIDO` y `SERVICIO_NO_PRESTADO`; `ck_reembolso_motivo` sólo admite `DISPUTA`, `DUPLICADO`, `ERROR_MONTO`, `GRUPO_CANCELADO` | `docs/CasosDeUso/CU-19` §Flujo 1 vs. `sql/` | Manda la DDL (precedencia). `MotivoDeReembolso` valida **antes** de escribir, para que sea una regla de negocio y no un error 500 |
| H-2 | El CU-19 nombra los tipos de disputa `FRAUDE_DECLARADO` y `ERROR_TECNICO`; `ck_disputa_pago_tipo` no los admite | ídem | Igual que H-1, con `TipoDeDisputa` |
| H-3 | El CU-19 define un campo `observacion` en la entrada; `aportes.reembolso` no tiene esa columna | ídem | No se inventó columna. La observación no se persiste |
| H-4 | **No existe salud de proveedor** en ninguna tabla ni endpoint, pero CU-99 enruta por ella | `aportes.proveedor_pago` | Antes el repositorio devolvía `100` fijo, con lo que el umbral **nunca podía disparar** — un control apagado que parecía encendido. Ahora la salud **entra como dato** (`saludObservada`), la mide quien observa el tráfico, y un proveedor sin medición se trata como sano porque acaba de pasar contrato y pruebas: negarle tráfico por no tener historial lo dejaría sin poder generarlo nunca |
| H-5 | `AP-CU19-05` se llama `PROVEEDOR_SIN_SOPORTE` en el CU; acá se usa para el motivo que la DDL no admite | `openapi/aportes.yaml` | Declarado en el contrato con su comentario. No se inventó un código nuevo |
| H-6 | `AP-CU21-03 SALDO_INSUFICIENTE` lo decide `nucleo-financiero` al debitar, no `aportes` | `openapi/aportes.yaml` | Declarado en el contrato para que el cliente lo conozca; este servicio no lo lanza |
| H-7 | El trabajo diario de mora **no está disparado**: `generarRecargos` existe, se prueba, y no lo llama ningún `@Scheduled` | `servicios/aportes/.../trabajos/` (vacío) | No se cableó. Cablearlo sin el ShedLock del servicio arrancado es escribir algo que no se puede probar |

## Supuestos declarados

1. **CU-21, monto exacto.** El CU no dice si un pago puede exceder lo que la
   obligación debe. Se rechaza. Cobrar de más no es un favor: es plata del grupo
   aplicada a una cuota que ya estaba, y después no hay a quién devolvérsela sin
   romper el calendario.
2. **CU-19, reembolsos parciales.** El CU no los prohíbe. Se admiten, midiendo
   siempre contra **lo ya devuelto** y no contra el monto original: dos parciales que
   juntos superan el pago devuelven de más.
3. **CU-99, alta idempotente.** El CU no define clave de idempotencia para el alta.
   La clave natural es el `codigo`, que ya es único en la base.

## Fronteras transaccionales respondidas

### CU-21 · Cobrar el aporte del período
1. **Todo junto o nada:** el `pago` y la actualización de `obligacion_aporte`, con el
   evento de outbox. Un pago sin obligación actualizada le cobra dos veces al mismo.
2. **Fuera del commit:** el movimiento de dinero — lo hace `nucleo-financiero` al
   consumir el evento (invariante 12). Ninguna llamada de red dentro de la transacción.
3. **Clave de idempotencia:** del cliente, `(obligacion_id, clave_idempotencia)` con
   índice único. Se valida **antes** de escribir.
4. **Qué se bloquea:** la fila de la obligación, por versión optimista. Dos pagos que
   leyeron la misma versión no escriben los dos.
5. **Si el proceso muere tras el commit:** el pago está aplicado y el evento en el
   outbox sin publicar. El relay lo publica; `nucleo-financiero` deduplica por
   `evento_consumido`.

### CU-19 · Reembolsar un pago y atender una disputa
1. **Todo junto o nada:** solicitar es una transacción; aprobar-y-ejecutar es otra.
   Son dos actos de dos personas distintas y no pueden compartir transacción.
2. **Fuera del commit:** la devolución del dinero y el asiento — de `nucleo-financiero`.
3. **Clave de idempotencia:** la del webhook del proveedor, anotada en
   `evento_consumido` con el consumidor `proveedor-pago`.
4. **Qué se bloquea:** la fila del reembolso, con `WHERE estado = 'SOLICITADO'` en el
   `UPDATE`. La segunda aprobación no devuelve la plata otra vez.
5. **Si el proceso muere tras el commit:** el reembolso quedó `EJECUTADO` y la
   obligación pendiente por ese importe. El evento sale por el relay.

### CU-99 · Dar de alta un proveedor y enrutar el cobro
1. **Todo junto o nada:** la fila del proveedor y su evento de activación. Un
   proveedor a medio dar de alta recibiendo tráfico es plata en manos de un tercero
   sin contrato.
2. **Fuera del commit:** todo lo del tercero. Enrutar **no escribe**.
3. **Clave de idempotencia:** el `codigo`, único en la base.
4. **Qué se bloquea:** nada; el índice único decide.
5. **Si el proceso muere tras el commit:** el proveedor quedó activo y su evento en el
   outbox. Reintentar el alta devuelve `CODIGO_DUPLICADO`, que es lo correcto.

### CU-51 · Ejecutar el cierre diario (en `nucleo-financiero`)
1. **Todo junto o nada:** el `cierre_diario` y **todos** los `saldo_diario_billetera`.
   Medio cierre deja la foto de un día que nunca se cerró.
2. **Fuera del commit:** el conteo de excepciones y el cuadre de custodia, que llegan
   resueltos desde afuera.
3. **Clave de idempotencia:** la **fecha**. El planificador reintenta.
4. **Qué se bloquea:** la unicidad de `cierre_diario.fecha`.
5. **Si el proceso muere tras el commit:** el día está cerrado; la siguiente corrida
   devuelve el cierre existente y **no reescribe los saldos**.

## Piezas declaradas por nivel

| Pieza | Nivel | CU | Estado |
| --- | --- | :-: | :-: |
| `RecargoDeMora` | átomo | 21 | ✅ |
| `SaldoDeLaObligacion` | átomo | 21, 19 | ✅ |
| `MotivoDeReembolso` | átomo | 19 | ✅ |
| `TipoDeDisputa` | átomo | 19 | ✅ |
| `ObligacionRepositorio` | molécula | 21, 19 | ✅ |
| `PagoRepositorio` | molécula | 21, 19 | ✅ |
| `ProveedorPagoRepositorio` | molécula | 99 | ✅ |
| `CU21CobrarAporte` | organismo | 21 | ✅ |
| `CU19ReembolsarPago` | organismo | 19 | ✅ |
| `CU99EnrutarProveedor` | organismo | 99 | ✅ |
| `CU51EjecutarCierreDiario` | organismo | 51 | ✅ (en `nucleo-financiero`) |

## Micro-PR abiertos al troncal

| Rama | Qué agrega | Estado |
| --- | --- | :-: |
| — | Pendiente: corregir la cabecera de contrato de `docs/CasosDeUso/CU-51` para que apunte a `openapi/nucleo-financiero.yaml`, y así el verificador deje de reportarlo como pendiente | ⬜ |

## Bloqueos

Ninguno. Nada de este carril esperó a otro.

## Matriz de gates

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `verificar_criterios.py --servicio aportes`: «casos de uso implementados y verificados: 3 · Sin divergencias» | ✅ |
| Datos | Restricciones citadas con prueba de rechazo | CU-19 9/9 · CU-21 7/7 · CU-99 8/8 · CU-51 3/3 | ✅ |
| Seguridad | Prueba negativa de RLS | pendiente: `AislamientoEsquemaTest` corre a nivel servicio | ⬜ |
| Plazos | Vencimiento y aviso previo | `fecha_limite_respuesta` persistida al crear, verificado en `rechazaRCON01` | ✅ |
| Arquitectura | Piezas por nivel, sin saltos | tabla de arriba | ✅ |
| Operación | Health, readiness, trazas | pendiente: capa `web/` de la fase de exposición | ⬜ |
| Entrega | Pruebas | ver el gate de salida | ✅ |

## Gate de salida — evidencia

- [x] `./gradlew :servicios:aportes:integrationTest --tests "*CU19*"` — BUILD SUCCESSFUL, 19 pruebas
- [x] `./gradlew :servicios:aportes:integrationTest --tests "*CU21*" --tests "*CU99Rechazos*"` — BUILD SUCCESSFUL
- [x] `./gradlew :servicios:aportes:integrationTest --tests "*CU99Test*"` — BUILD SUCCESSFUL, 17 pruebas
- [x] `python3 scripts/verificar_criterios.py --servicio aportes` — Sin divergencias
- [x] Cada criterio de aceptación con su `@Test` + `@DisplayName` nombrado igual
- [x] Cada `R-XXX-nn` citado con prueba de rechazo
- [ ] `./gradlew spotlessCheck check` — se corre al cerrar el carril
- [ ] `sagaTest` — este carril no tiene operaciones que crucen servicios en saga

## Ver también

[[informe]] · [[07 Carriles de trabajo concurrente]] · [[carril-1B]]
