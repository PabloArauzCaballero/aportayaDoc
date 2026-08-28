# erp — contabilidad, compras, cobranzas y activos

El servicio que lleva los libros de la empresa: el mes contable, el presupuesto por
centro de costo, las facturas de proveedores, las cuentas por cobrar, la depreciacion
de los activos y los estados financieros.

**Lo que este servicio no hace: escribir el libro contable.** El mayor lo escribe
`nucleo-financiero` y nadie mas (invariante 12). erp **lee** el mayor —el cierre del
mes y los estados financieros se calculan sumando `movimiento_contable`— pero no
escribe ni un asiento: los pide por evento, y el `asiento_contable_id` de una factura,
un cobro o una depreciacion se completa cuando `nucleo-financiero` responde. Esa
lectura cruzada esta declarada como hueco en el informe del carril.

## Casos de uso

| CU | Que resuelve | Entrada por |
| --- | --- | --- |
| CU-100 | Abrir el ejercicio y sus doce meses; cerrar el mes contra su cuadre | `POST /erp/ejercicios`, `POST /erp/periodos/{id}/cierre` |
| CU-101 | Presupuestar un centro de costo y aprobar el presupuesto | `POST /erp/presupuestos` |
| CU-102 | Dar de alta un tercero comercial y su orden de compra | `POST /erp/terceros`, `POST /erp/ordenes-de-compra` |
| CU-103 | Registrar y pagar una factura de proveedor, con cuatro ojos | `POST /erp/facturas-de-proveedor` |
| CU-104 | Abrir y cobrar una cuenta por cobrar | `POST /erp/cuentas-por-cobrar` |
| CU-105 | Depreciar un activo, uno o el mes entero | `POST /erp/activos/{id}/depreciaciones` |
| CU-106 | Generar el balance o el estado de resultados del mes | `POST /erp/periodos/{id}/estados-financieros` |

El contrato completo, con los codigos de error de la boveda y las divergencias
declaradas, esta en [`openapi/erp.yaml`](src/main/resources/openapi/erp.yaml).

## Las cuatro decisiones que gobiernan este carril

**1 · El cierre es irreversible.** `cierre_periodo_contable` es append-only y unico
por periodo: un mes se cierra una vez y para siempre. No hay operacion de reapertura,
y no es un olvido — corregir un mes cerrado se hace con un asiento en el mes
siguiente que cita al corregido, que es como se corrige en contabilidad.

**2 · Lo que es append-only no tiene estado mutable.** `factura_proveedor`,
`cuenta_por_cobrar` y `depreciacion_activo` no admiten `UPDATE`. El estado corriente
no se guarda: se deriva sumando las filas hijas — los pagos de una factura, los cobros
de una cuenta. La columna `monto_pagado` queda en cero para siempre, y este servicio
no la lee.

**3 · Los umbrales son catalogo.** La vida util y el metodo de depreciacion salen de
`categoria_activo_fijo`; el tipo de cambio, de `tipo_cambio`. No hay ninguna cifra
cableada en `main/java`, y `testBarrido` lo comprueba.

**4 · Cuatro ojos sobre el egreso.** Quien aprueba una factura no autoriza su pago
(R-CTB-05). El caso de uso lo comprueba para poder explicarlo con un mensaje, y el
trigger `fn_ctb_segregacion_pago` lo sostiene aunque alguien escriba el pago por
fuera.

## Pruebas

```bash
./gradlew :servicios:erp:test              # atomos, sin base
./gradlew :servicios:erp:integrationTest   # los siete CU contra Postgres real
./gradlew :servicios:erp:testBarrido       # tamano de archivo y umbrales literales
```

Cada CU tiene dos archivos: `CU1nnTest` con un `@Test` por criterio de aceptacion, y
`CU1nnRechazosTest` con un `@Test` por restriccion citada. Los rechazos se comprueban
contra la base, no contra el codigo: `rechazaLaBase(sql, params)` ejecuta el SQL
prohibido y devuelve el mensaje con que Postgres lo frena.

## Huecos declarados

Estan en [`planes/informes/carril-3D-erp.md`](../../planes/informes/carril-3D-erp.md).
Ninguno se relleno con una suposicion.
