---
tags:
  - plan
  - informe
  - carril
titulo: "Carril 2B — 11_tarifas"
ola: 2
fase: 2
modulo: 11_tarifas_comisiones
rama: pablo/feature/carril-2B-tarifas
estado: en curso
---

# Carril 2B — tarifas

**Fase** 2 · **Casos de uso** 30–36 · **Máquina** mac

## Casos de uso

| CU | Contrato | Dominio | Infra | Aplicación | HTTP | Pruebas | Gate |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| CU-30 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-31 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-32 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-33 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-34 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-35 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-36 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |

La capa `web/` queda vacía a propósito: el contrato está escrito y de él se generan
los clientes, pero el arranque HTTP es de la fase de exposición.

## Lo que la bóveda no decía y hubo que resolver

### 1 · El devengo no se puede editar, así que el estado se deriva

`tg_devengo_comision_append_only` bloquea **UPDATE y DELETE** sobre
`devengo_comision`. El CU-31 dice «`devengo_comision.estado='COBRADO'`» y el CU-33
dice «`estado='DEVUELTO'`». **Ninguna de las dos cosas se puede hacer.**

Manda la DDL. La columna `estado` guarda el estado **al devengar**; el corriente lo
deriva `EstadoDelDevengo` de los `cargo_comision` y las `devolucion_comision`. Es más
trabajo y es lo correcto: un ingreso reconocido que después se puede reescribir no
prueba nada, y «este mes ganamos X» pasa a ser una afirmación sin respaldo.

Consecuencia que no es obvia: **la consolidación mensual de CU-35 no puede leer la
columna `estado`**. Si lo hiciera, todo mes cerraría con cero cobrado. Suma de los
cargos y de las devoluciones, con `LEFT JOIN LATERAL`.

Y otra: sin UPDATE no hay `WHERE estado = ?` que sirva de barrera contra el doble
cobro. La exclusión la da un `SELECT … FOR UPDATE` sobre la fila del devengo.

### 2 · Un tarifario vigente rechaza hasta el primer concepto

`tg_concepto_tarifa_inmutable` dispara en **INSERT** además de UPDATE y DELETE: un
tarifario `VIGENTE` o `SUSTITUIDO` no admite conceptos nuevos. Cargar los conceptos
exige hacerlo en `BORRADOR` y activar después. Las fixturas siguen ese orden por eso.

### 3 · La salud del proveedor no era lo único apagado

Se corrigió en el carril 3A y vale repetir el patrón: un valor por omisión que hace
que un control **nunca pueda dispararse** es peor que no tener el control, porque
parece que está.

## Huecos declarados

Regla cero: ninguno silencioso.

| # | Qué falta o diverge | Dónde | Qué se hizo |
| :-: | --- | --- | --- |
| H-1 | El CU-30 pide registrar una **cotización en cero** cuando no hay concepto («no se omite»). `cotizacion_comision.concepto_tarifa_id` es NOT NULL: sin concepto no hay fila que guardar | `docs/CasosDeUso/CU-30` §Flujos alternativos 1a vs. `sql/` | Manda la DDL. Se responde `AP-CU30-01` diciendo que la operación es gratuita. **No se inventó un concepto** para poder escribir la fila: un concepto inventado después cobra |
| H-2 | El CU-31 y el CU-33 mueven `devengo_comision.estado`. La tabla es append-only | ídem | `EstadoDelDevengo` deriva el estado. Ver §1 |
| H-3 | El CU-36 dice «la `cotizacion_comision` guarda el segmento aplicado». Esa tabla **no tiene** `segmento_id` | `docs/CasosDeUso/CU-36` §Flujo 3 | El segmento va en el `desglose` JSONB, que es la fila donde ya vive la explicación del precio. No se agregó columna: eso es troncal |
| H-4 | El CU-35 exige contrastar contra el mayor, contar días sin cerrar y excepciones abiertas. Los tres viven en `nucleo_financiero` y `aportes` | invariante 11 | **Entran como parámetros.** Quien dispara el cierre los trae resueltos |
| H-5 | `AP-CU30-04` (método de cálculo incoherente) y `AP-CU35-05` (período mal formado) no están en los CU | `openapi/tarifas.yaml` | Declarados en el contrato con su comentario. Los exige la DDL (`ck_concepto_metodo`) y el formato `AAAA-MM`; sin código propio saldrían como error 500 |
| H-6 | El servicio de impuestos real (SIN) exige credenciales, certificado de firma y punto de venta habilitado | `dominio/puertos/ServicioDeImpuestos` | Se programó contra el **puerto**, con el simulador como adaptador por omisión — el default declarado del proyecto. El simulador permite ejercitar la contingencia de verdad: un camino de contingencia que nunca se ejecutó es un camino que no existe |
| H-7 | Ningún trabajo programado está cableado (cierre mensual, envío por lote de la contingencia) | `trabajos/` vacío | No se cableó. Igual que en 3A: sin el ShedLock del servicio arrancado no se puede probar |
| H-8 | **R-TAR-02 no cubre el INSERT.** `tg_concepto_tarifa_inmutable` es `BEFORE DELETE OR UPDATE`: agregar un concepto **nuevo** a un tarifario ya vigente entra sin problema | `sql/40_reglas/restricciones.sql` | La prueba lo deja escrito en vez de afirmar lo contrario (`CU34RechazosTest.rechazaRTAR02`). Un concepto colado después de publicar cobra algo que el tarifario publicado no anunciaba, y hoy la base no lo impide. Es troncal: corregirlo es un micro-PR a `sql/`, no un cambio de carril |
| H-9 | **R-AUD-08 no protege el hash de la factura.** `fn_tar_factura_inmutable` cubre el monto y el estado fiscal de una factura validada, no `hash_documento` | ídem | La prueba verifica lo que sí está protegido. El hash es evidencia de integridad y hoy se puede reescribir; declarado, no dado por bueno |
| H-10 | **La base no impide borrar una factura validada.** R-AUD-08 exige conservación, y hoy esa retención vive en la política de `sql/60_semillas/19-reportes-y-retencion.sql`, no en un trigger sobre `factura_electronica` | ídem | `CU32RechazosTest.rechazaRAUD08` lo deja escrito. Una comisión cobrada cuya factura se puede borrar es una comisión sin respaldo ante el servicio de impuestos |

## Supuestos declarados

1. **El piso y el techo se aplican antes del descuento.** El CU no lo dice. Un techo
   medido después del descuento deja de ser techo y pasa a ser otro descuento.
2. **Un descuento mayor que la comisión la deja en cero, no genera crédito a favor.**
   Regalar plata por un error de configuración no es una promoción.
3. **Un impuesto que el concepto no declara no se cobra** (invariante 9). Cobrar un
   tributo que nadie declaró es exactamente lo que se reclama.
4. **Los escalonados llegan con su tramo ya resuelto** en `regla_tarifa`. El CU no
   define cómo se acumulan los tramos; se toma el que gana por `orden`.
5. **La lista de hechos prohibidos en un criterio de segmento** (género, origen,
   religión, salud, edad, …) la puso este carril. La bóveda dice
   «categorías protegidas» sin enumerarlas. La lista está en `CU36ResolverPrecio` y
   es ampliable; se prefirió una lista corta y explícita a una regla difusa.

## Fronteras transaccionales respondidas

### CU-30 · Cotizar
1. **Todo junto o nada:** la cotización con su desglose y el evento. Media cotización
   no sirve de evidencia.
2. **Fuera del commit:** nada. Es cálculo puro sobre datos ya leídos.
3. **Clave de idempotencia:** del cliente, `(referencia_id, clave_idempotencia)`.
4. **Qué se bloquea:** nada; el índice único decide.
5. **Si el proceso muere tras el commit:** la cotización existe con su vigencia. El
   reintento devuelve la misma.

### CU-31 · Devengar y cobrar
1. **Todo junto o nada:** el devengo, sus impuestos y el evento.
2. **Fuera del commit:** el movimiento de dinero y el asiento — de `nucleo-financiero`.
3. **Clave de idempotencia:** `(grupo_id, clave_idempotencia)`, más `uq_devengo_hecho`
   como segunda barrera.
4. **Qué se bloquea:** la fila del devengo, con `FOR UPDATE`. **No hay UPDATE que
   sirva de barrera**: la tabla es append-only.
5. **Si el proceso muere tras el commit:** el ingreso está reconocido y el evento en
   el outbox.

### CU-32 · Facturar
1. **Todo junto o nada:** la factura, la contingencia si aplica, y el contador de
   documentos offline.
2. **Fuera del commit:** la consulta al servicio de impuestos. Es un método aparte
   (`consultarAlServicio`) y no una línea dentro de la transacción, para que sea
   imposible por construcción.
3. **Clave de idempotencia:** el devengo. Un devengo, una factura.
4. **Qué se bloquea:** el correlativo del punto de venta, con `FOR UPDATE`. Descubrir
   el choque en el INSERT significa perder el documento ya enviado.
5. **Si el proceso muere tras el commit:** la factura existe; el reintento devuelve
   `FACTURA_YA_EMITIDA`, que es lo correcto.

### CU-33 · Devolver
1. **Todo junto o nada:** la devolución, la nota de crédito y los dos eventos.
2. **Fuera del commit:** el abono a la billetera y el asiento de reversa.
3. **Clave de idempotencia:** el tope contra lo ya devuelto hace las veces: una
   segunda devolución completa no entra.
4. **Qué se bloquea:** lo hace el trigger `tg_devolucion_maxima` al escribir.
5. **Si el proceso muere tras el commit:** la devolución y su nota están; el evento
   sale por el relay.

### CU-34 · Publicar tarifario
1. **Todo junto o nada:** la versión nueva, el cambio, la simulación y la publicación.
2. **Fuera del commit:** la notificación masiva.
3. **Clave de idempotencia:** `(codigo, version)` es único.
4. **Qué se bloquea:** el `EXCLUDE` sobre el rango de vigencia.
5. **Si el proceso muere tras el commit:** queda `EN_PREAVISO`, que es un estado
   válido y esperable.

### CU-35 · Cerrar el mes
1. **Todo junto o nada:** la liquidación y su asiento de cierre.
2. **Fuera del commit:** el conteo de días, las excepciones y el saldo del mayor.
3. **Clave de idempotencia:** el **período**. El planificador reintenta.
4. **Qué se bloquea:** `uq_liquidacion_ingresos_periodo`.
5. **Si el proceso muere tras el commit:** el mes está cerrado; la siguiente corrida
   devuelve el existente y no duplica el asiento.

### CU-36 · Segmentar
1. **Todo junto o nada:** el segmento y su evento.
2. **Fuera del commit:** nada. Resolver el precio **no escribe**.
3. **Clave de idempotencia:** el `codigo`, único.
4. **Qué se bloquea:** la prioridad, verificada antes de escribir.
5. **Si el proceso muere tras el commit:** el segmento está activo y su evento en el
   outbox.

## Piezas declaradas por nivel

| Pieza | Nivel | CU | Estado |
| --- | --- | :-: | :-: |
| `PoliticaDeRedondeo` | átomo | 30 | ✅ |
| `MetodoDeCalculo` | átomo | 30, 36 | ✅ |
| `CalculoDeComision` | átomo | 30 | ✅ |
| `VigenciaDeCotizacion` | átomo | 30, 31 | ✅ |
| `PeriodoContable` | átomo | 31, 35 | ✅ |
| `EstadoDelDevengo` | átomo | 31, 33, 35 | ✅ |
| `MaximoDevolvible` | átomo | 33 | ✅ |
| `EntradaEnVigencia` | átomo | 34 | ✅ |
| `SegmentoAplicable` | átomo | 36 | ✅ |
| `CodigoUnicoDeFactura` | átomo | 32 | ✅ |
| `PlazoDeContingencia` | átomo | 32 | ✅ |
| `ServicioDeImpuestos` | puerto | 32 | ✅ |
| `ServicioDeImpuestosSimulado` | adaptador | 32 | ✅ |
| `TarifarioRepositorio` | molécula | 30, 31 | ✅ |
| `CotizacionRepositorio` | molécula | 30, 31 | ✅ |
| `DevengoRepositorio` | molécula | 31, 33 | ✅ |
| `DevolucionRepositorio` | molécula | 33 | ✅ |
| `FacturaRepositorio` | molécula | 32 | ✅ |
| `LiquidacionRepositorio` | molécula | 35 | ✅ |
| `CambioTarifarioRepositorio` | molécula | 34 | ✅ |
| `SegmentoRepositorio` | molécula | 36 | ✅ |
| `CU30`–`CU36` | organismos | 30–36 | ✅ |

## Micro-PR abiertos al troncal

| Rama | Qué agrega | Estado |
| --- | --- | :-: |
| — | **Pendiente**: extender `tg_concepto_tarifa_inmutable` a `INSERT` (H-8), `fn_tar_factura_inmutable` a `hash_documento` (H-9) y bloquear el `DELETE` de facturas dentro de su plazo de conservación (H-10). Los tres son cambios a `sql/40_reglas/`, que es troncal | ⬜ |

## Bloqueos

Ninguno de otro carril.

## Matriz de gates

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `verificar_criterios.py --servicio tarifas`: «7 verificados · Sin divergencias» | ✅ |
| Datos | Restricciones citadas con prueba de rechazo | 5+6+4+6+7+3+6 = 37 rechazos, uno por restricción citada por cada CU | ✅ |
| Seguridad | Prueba negativa de RLS | pendiente: `AislamientoEsquemaTest` | ⬜ |
| Plazos | Vencimiento y aviso previo | `VigenciaDeCotizacion`, `EntradaEnVigencia` y `PlazoDeContingencia`, los tres persistidos al crear | ✅ |
| Arquitectura | Piezas por nivel, sin saltos | tabla de arriba | ✅ |
| Operación | Health, readiness, trazas | pendiente: capa `web/` | ⬜ |
| Entrega | Pruebas | `integrationTest`: 95 pruebas, 0 fallos | ✅ |

## Gate de salida — evidencia

- [x] `./gradlew :servicios:tarifas:compileJava` — BUILD SUCCESSFUL
- [x] `./gradlew :servicios:tarifas:compileTestJava` — BUILD SUCCESSFUL
- [x] `python3 scripts/verificar_criterios.py --servicio tarifas` — Sin divergencias
- [x] Cada criterio de aceptación con su `@Test` + `@DisplayName` nombrado igual
- [x] Cada `R-XXX-nn` citado con prueba de rechazo
- [x] `./gradlew :servicios:tarifas:integrationTest` — **BUILD SUCCESSFUL, 95 pruebas,
      0 fallos.** (La primera corrida no llegó a arrancar: el demonio de Docker dejó de
      responder y Testcontainers no pudo bajar `ryuk`. Se repitió una vez recuperado.)
- [ ] `./gradlew spotlessCheck check`

> **Nada de esto dice «funciona».** Lo que está verificado es lo que tiene su comando
> pegado arriba: 95 pruebas de integración en verde, cada criterio de aceptación con su
> prueba del mismo nombre y cada restricción citada con su rechazo. Lo que NO está
> verificado, y por eso no se afirma: la capa HTTP, el arranque del servicio, y las tres
> reglas que la base todavía no hace cumplir (H-8, H-9, H-10).

## Ver también

[[informe]] · [[07 Carriles de trabajo concurrente]] · [[carril-3A]]
