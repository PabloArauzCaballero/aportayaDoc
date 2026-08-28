# Carril 3D · erp — informe de cierre

**Servicio:** `erp` · **Esquema:** `erp` · **Rol:** `svc_erp`
**Casos de uso:** CU-100 a CU-106 · **Rama:** `dev`

Siete casos de uso implementados, con sus criterios de aceptacion y sus rechazos
verificados contra Postgres real. Lo que sigue son las divergencias entre lo que la
boveda dice y lo que la base hace. **Ninguna se relleno con una suposicion:** donde el
DDL contradice al caso de uso, gana el DDL, se implementa el comportamiento verdadero
mas cercano y queda escrito aca.

---

## Hueco 1 · Un periodo contable se cierra una vez, y para siempre

`cierre_periodo_contable` es append-only (R-AUD-01) **y** unico por periodo. Las dos
cosas juntas hacen que reabrir un mes sea imposible: no se puede borrar el cierre ni
marcar el periodo como ABIERTO otra vez.

CU-100 ya lo dice en sus flujos alternativos («no existe ese caso de uso»), asi que
esto no es una contradiccion sino una confirmacion: `PeriodoRepositorio` **no expone
`reabrir`**, y su javadoc explica que corregir un mes cerrado se hace con un asiento
en el mes siguiente.

Demostrado en `CU100RechazosTest > rechaza por R-AUD-01`.

---

## Hueco 2 · `factura_proveedor.monto_pagado` es una columna muerta

La tabla es append-only, asi que `monto_pagado` **no se puede actualizar nunca**.
Queda en el valor del insert — cero — mientras los pagos se acumulan en
`pago_a_proveedor`.

Consecuencia: `ck_factura_proveedor_pagado` (`monto_pagado <= monto`) nunca puede
fallar, y el limite real de R-CTB-04 lo tiene que sostener la aplicacion. Lo hace
`ComprasRepositorio.facturaBloqueada`, que toma `SELECT … FOR UPDATE` sobre la factura
—una lectura, permitida por el trigger append-only— y suma los pagos existentes. El
bloqueo sobre el padre es lo que impide que dos pagos concurrentes se pasen del monto.

Lo mismo vale para `estado`: una factura nunca pasa de APROBADA a PAGADA. El estado
que devuelve el contrato es derivado.

Demostrado en `CU103Test` y en `CU103RechazosTest > rechaza por R-CTB-04`.

---

## Hueco 3 · Una factura nace aprobada o no se aprueba nunca

Del mismo append-only se sigue que `aprobada_por` no se puede completar despues del
alta, y `ck_factura_proveedor_aprobacion` no admite una fila APROBADA sin aprobador.
**No hay operacion de aprobacion posterior**, y el flujo del CU-103 —registrar, luego
aprobar, luego pagar— no se puede implementar como tres pasos.

Lo que si se sostiene entero es la segregacion: quien figura como aprobador no puede
ser quien autoriza el pago. Se comprueba en el caso de uso, para poder explicarlo, y
el trigger `fn_ctb_segregacion_pago` lo frena igual si alguien escribe el pago por
fuera.

Demostrado en `CU103RechazosTest > rechaza por R-CTB-05` y `> rechaza por R-SEG-04`.

---

## Hueco 4 · Una cuenta por cobrar no puede *volverse* incobrable

`cuenta_por_cobrar` es append-only. `monto_cobrado` y `estado` quedan como se
insertaron; el cobrado real se deriva de `cobro_cuenta_por_cobrar` y el estado
(PENDIENTE / COBRADA_PARCIAL / COBRADA) se calcula.

El estado INCOBRABLE, en cambio, **solo puede decidirse al abrir la cuenta**. El
proceso que CU-104 describe —una cuenta vencida que despues de N dias se da por
perdida— no tiene donde escribirse. La fixtura `cuentaIncobrable` la crea asi a
proposito, y el caso de uso rechaza cobrarla.

Demostrado en `CU104Test` y en `CU104RechazosTest > rechaza por R-CTB-06`.

---

## Hueco 5 · El asiento contable lo escribe otro servicio

`factura_proveedor.asiento_contable_id`, `cobro_cuenta_por_cobrar.asiento_contable_id`
y `depreciacion_activo.asiento_contable_id` apuntan a `nucleo_financiero`. erp no
escribe el mayor (invariante 12) y tampoco puede completarlos despues (append-only).

Este carril los deja nulos y emite el evento; el asiento lo arma `nucleo-financiero`
al consumirlo. **El contrato declara `asientoContableId` nulable**, en divergencia con
CU-103, que lo declara requerido en su salida.

---

## Hueco 6 · erp lee el esquema de nucleo-financiero

El invariante 11 dice que un servicio no lee la base de otro. El cierre de CU-100 y los
estados financieros de CU-106, en cambio, se calculan sumando
`nucleo_financiero.movimiento_contable` contra `nucleo_financiero.cuenta_contable`, y
el `periodo_contable_id` vive en `nucleo_financiero.asiento_contable`.

**No hay forma de evitarlo con el modelo tal como esta**: la clave que ata un asiento a
un mes contable esta del lado del mayor, no del lado de erp. Cerrar el mes preguntando
por HTTP tampoco sirve — seria una llamada de red dentro de la transaccion del cierre,
que el invariante 6 prohibe.

Este carril lee esas tres tablas y no escribe ninguna. Queda declarado para que la
decision se tome en la boveda: o el mayor expone una vista de solo lectura con contrato
propio, o el invariante 11 admite explicitamente la lectura del libro.

---

## Hueco 7 · El catalogo de errores no tiene codigo de «no existe»

La boveda cierra los codigos de cada CU en tres o cuatro, y ninguno cubre «el
identificador no corresponde a nada». Inventar `AP-CU103-05` seria inventar una
constante que la boveda no tiene, asi que cada CU reutiliza el codigo mas cercano de
su propio catalogo:

| CU | Codigo reutilizado para «no existe» |
| --- | --- |
| CU-100 | `AP-CU100-01` |
| CU-101 | `AP-CU101-01` |
| CU-102 | `AP-CU102-03` |
| CU-103 | `AP-CU103-03` |
| CU-104 | `AP-CU104-01` |
| CU-105 | `AP-CU105-03` |
| CU-106 | `AP-CU106-02` |

Queda pendiente decidir en la boveda si estas condiciones merecen codigo propio. Esta
declarado tambien en la NOTA de `openapi/erp.yaml`.

---

## Hueco 8 · CU-106 no tiene codigo para el descuadre

CU-100 tiene `DESCUADRE_DETECTADO` (`AP-CU100-03`), pero CU-106 —que tambien
comprueba que activo = pasivo + patrimonio antes de publicar— solo tiene
`YA_GENERADO_PARA_ESE_PERIODO` y `CUENTA_PLANTILLA_INVALIDA`. El descuadre al generar
un balance devuelve `AP-CU106-02`.

---

## Hueco 9 · La ecuacion contable depende de la naturaleza, no del tipo

Sumar `debe - haber` para todas las cuentas no cierra: las acreedoras (pasivo,
patrimonio, ingreso) tienen su saldo en el otro sentido. `PresupuestoRepositorio`
`saldosDelPeriodo` decide el signo por `cuenta_contable.naturaleza`, no por `tipo`,
porque hay cuentas de activo con naturaleza acreedora — la depreciacion acumulada es
la mas comun, y la usa el propio CU-105.

Sin ese detalle, el balance de cualquier empresa con activos depreciados daria un
descuadre exactamente igual a la depreciacion acumulada.

---

## Hueco 10 · `asiento_plantilla` no se llama como el CU la nombra

El CU-106 habla de `descripcion` y `evento_origen`; las columnas son `glosa`,
`periodicidad` y `creada_por` (NOT NULL). `periodicidad` es un catalogo cerrado:
ANUAL, MANUAL, MENSUAL, TRIMESTRAL. La fixtura usa MANUAL.

---

## Verificado por maquina

```
./gradlew :servicios:erp:spotlessCheck build           BUILD SUCCESSFUL
./gradlew :servicios:erp:integrationTest               BUILD SUCCESSFUL
./gradlew :servicios:erp:testBarrido                   BUILD SUCCESSFUL
python3 scripts/verificar_criterios.py                 Sin divergencias
python3 scripts/verificar_boveda.py                    TODO OK
python3 scripts/verificar_seguridad.py                 sin hallazgos
```

## Lo que no verifica ninguna maquina

**¿Los nombres dicen lo que las cosas son?** `PeriodoRepositorio.totalesDe` devuelve
debe y haber del mes, no un «total» a secas; se llamo asi porque es lo que el cierre
pregunta. `CuadreContable.resultados` devuelve ingresos menos egresos: es el estado de
resultados, no un resultado cualquiera.

**¿La frontera transaccional es la correcta?** El cierre de CU-100 hace las tres cosas
—constancia, estado del periodo, evento— en una transaccion, porque un cierre a
medias deja un mes que no esta ni abierto ni cerrado. La corrida de depreciacion de
CU-105, en cambio, deprecia activo por activo dentro de una sola transaccion: si un
activo falla, ninguno se deprecia, y eso es lo que se quiere — una corrida mensual
parcial seria peor que ninguna.

**¿Que supuse que no estaba en la boveda?** Las rutas HTTP. Los CU-100 a CU-106
declaran entradas, salidas y errores, pero no rutas; se eligieron bajo el prefijo
reservado `/erp` siguiendo la forma de los otros contratos del sistema.

**¿Que deje peor de como lo encontre?** Nada de la troncal. El unico cambio fuera de
`servicios/erp/` fue el micro-PR que ensancho `CodigoError` para admitir CU de tres
digitos, que ya esta en `dev` con su prueba.
