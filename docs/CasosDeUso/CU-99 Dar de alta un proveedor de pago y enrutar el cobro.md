---
tags:
  - caso-uso
  - modulo/03-aportes-pagos-qr-y-conciliacion
codigo: CU-99
criticidad: alta
actores: [Operaciones, Tesorería, Sistema, Proveedor de pago]
normas: [ASFI tercerización, BCB pagos, continuidad operativa, ASFI Res. 540/2025]
---

# CU-99 — Dar de alta un proveedor de pago y enrutar el cobro

> **Objetivo.** Que la plataforma no dependa de una sola pasarela, que cambiar de
> proveedor no obligue a tocar el código de los casos de uso, y que el costo real de
> cada uno sea un dato medido y no una promesa comercial.

## Actores y disparador

- **Actor principal:** operaciones, con tesorería y cumplimiento en la evaluación.
- **Disparadores:** alta de un proveedor nuevo; caída o degradación del actual;
  renegociación de comisiones; cambio de cobertura por entidad o moneda.

## Precondiciones

1. El proveedor pasó la evaluación de tercero ([[evaluacion_tercero]]) y tiene
   [[contrato_tercero]] firmado, con cláusulas de datos y de continuidad.
2. Existe entorno de prueba validado ([[entorno_prueba_regulado]]) con casos de
   cobro, reembolso, webhook duplicado y timeout ejecutados.
3. Las credenciales viven en el gestor de secretos: en la tabla solo va
   `referencia_credenciales`. **Ninguna credencial se persiste en la base.**

## Flujo principal

1. Se crea [[proveedor_pago]] con `codigo`, `nombre`, `tipo`, `url_base`,
   `referencia_credenciales`, `comision_fija`, `comision_porcentual`,
   `soporta_webhook`, `soporta_consulta_estado`, `activo` y `prioridad`.
2. Se implementa el adaptador contra la **misma interfaz** que los existentes: crear
   orden, consultar estado, reembolsar, desembolsar. Si el proveedor no soporta una
   operación, el adaptador lo declara y el enrutador no se lo pide.
3. **`soporta_consulta_estado = false` es un dato crítico**: sin consulta de estado,
   un timeout deja la operación en incertidumbre y solo se resuelve por conciliación.
   Un proveedor así entra con prioridad baja y no se usa para montos altos.
4. El enrutado de un cobro elige por **cobertura, prioridad, salud medida y costo**,
   en ese orden. La salud se mide con la ventana móvil de operaciones acreditadas
   sobre iniciadas; el costo real se acumula en [[costo_proveedor_operacion]] y
   **suele no coincidir con el tarifario del contrato**: por eso se mide.
5. Se emite [[orden_cobro]] con su [[qr_cobro]] o su [[enlace_pago_rapido]], que
   guarda `url_corta` única, `clicks` y `expira_en`. El enlace es la versión
   accionable del recordatorio ([[CU-81 Programar recordatorios de aporte]]).
6. Cada intento contra el proveedor queda en [[intento_pago]] con su
   `clave_idempotencia`, `codigo_error` y `mensaje_proveedor`. **La clave es de la
   orden, no del proveedor**: conmutar no vuelve a cobrar.
7. El [[webhook_pasarela]] entrante se valida por firma, se registra crudo y se
   procesa idempotentemente ([[CU-21 Cobrar el aporte del período]]). Todo se
   concilia después contra el [[extracto_bancario]].

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El proveedor no soporta reembolso | Se declara en el adaptador; los reembolsos van por el procedimiento manual de [[CU-19 Reembolsar un pago y atender una disputa]] |
| 3a | Timeout sin consulta de estado | La orden queda `EN_VERIFICACION`; **no se acredita ni se cancela** hasta conciliar |
| 4a | Salud por debajo del umbral | Se degrada y el tráfico pasa al siguiente; se avisa a operaciones y se registra |
| 4b | El costo medido supera al contratado | Se abre la revisión comercial con el número medido; el dato pesa más que el discurso |
| 5a | El enlace corto se comparte a un tercero | El pago se acredita a la obligación correcta igual: el enlace identifica la deuda, no a quien paga |
| 6a | Webhook con firma inválida | Se descarta, se registra el intento y puede abrir [[incidente_seguridad]] |
| 6b | Webhook fuera de orden (acreditación antes que creación) | Se guarda y se reprocesa cuando llega el faltante; la red no garantiza orden y el diseño lo absorbe |
| — | Baja de un proveedor | Se desactiva para operaciones nuevas, pero **sigue disponible para consultar y conciliar lo viejo**; nunca se borra |
| — | Proveedor que exige guardar datos de tarjeta | No se integra: el modelo no persiste números en claro (`R-SEG-01`) |

## Postcondiciones

- Todo cobro sale por un proveedor elegido con criterio registrado y reversible.
- Ninguna caída de un proveedor deja a la plataforma sin poder cobrar.

## Contrato · `openapi/aportes.yaml`

```ts
export const EntradaCU99 = z.object({
  codigo: z.string().max(30),
  nombre: z.string().max(80),
  tipo:   z.enum(['PASARELA','BANCO','BILLETERA','QR_INTEROPERABLE']),
  urlBase: z.string().url(),
  referenciaCredenciales: z.string().max(120),   // apunta al gestor de secretos
  comisionFija: MontoSchema,
  comisionPorcentual: z.string(),
  soportaWebhook: z.boolean(),
  soportaConsultaEstado: z.boolean(),
  cobertura: z.array(z.object({
    entidad: z.string().max(60), moneda: z.enum(['BOB','USD']),
    operaciones: z.array(z.enum(['COBRO','REEMBOLSO','DESEMBOLSO'])),
  })).min(1),
  prioridad: z.number().int().min(1).max(99),
}).strict()

export const SalidaEnrutarCU99 = z.object({
  proveedorCodigo: z.string(),
  motivoSeleccion: z.string(),
  ordenCobroId: z.string().uuid(),
  qr: z.object({ payloadEmv: z.string(), expiraEn: z.string().datetime() }).nullable(),
  enlaceRapido: z.object({
    urlCorta: z.string(), expiraEn: z.string().datetime(),
  }).nullable(),
  costoEstimado: MontoSchema,
}).strict()

export const ErroresCU99 = {
  CODIGO_DUPLICADO:        'AP-CU99-01',
  SIN_CONTRATO_TERCERO:    'AP-CU99-02',
  PRUEBAS_INCOMPLETAS:     'AP-CU99-03',
  SIN_COBERTURA:           'AP-CU99-04',
  CREDENCIAL_EN_TABLA:     'AP-CU99-05',
  PROVEEDOR_DEGRADADO:     'AP-CU99-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `CODIGO_DUPLICADO` | Ya existe un proveedor con ese código |
| `SIN_CONTRATO_TERCERO` | Falta contrato firmado o evaluación de tercero vigente |
| `PRUEBAS_INCOMPLETAS` | No se ejecutó el juego de casos en el entorno de prueba |
| `SIN_COBERTURA` | Ningún proveedor activo cubre esa entidad, moneda y operación |
| `CREDENCIAL_EN_TABLA` | Se intentó guardar una credencial en vez de su referencia (`R-SEG-01`) |
| `PROVEEDOR_DEGRADADO` | El elegido está bajo el umbral de salud; se conmuta |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `elegirProveedor(candidatos, operacion, salud, costo)` | Cobertura, prioridad, salud y costo; puro |
| Átomo | `costoEstimado(monto, fija, porcentual)` | Con `Dinero` y redondeo de la política; puro |
| Molécula | `ProveedorPagoRepositorio` · `EnlacePagoRepositorio` | Persistencia, unicidad de código y de URL corta |
| Molécula | `AdaptadorPasarela` | Uno por proveedor, misma interfaz; declara qué soporta |
| Molécula | `RegistroDeSalud` | Ventana móvil por proveedor y operación |
| Organismo | `CU99EnrutarCobro` | Elige, emite la orden y registra el intento |
| Página | `POST /proveedores-pago` · `POST /cobros` · `POST /webhooks/:proveedor` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `proveedor.activado` | Ingreso gradual de tráfico y medición de salud | `PROVEEDORES_ADMINISTRAR` |
| `proveedor.degradado` | Conmutación y aviso a operaciones | — |
| `cobro.enrutado` | Emisión del QR o del enlace y registro del intento | `BILLETERA_OPERAR` |
| — | Trabajo que consulta estado de órdenes sin acuse y alimenta la conciliación | — |

## Interfaz

- **App:** el usuario ve un QR o un enlace, no el proveedor. Si uno falla, se le
  ofrece otro medio sin explicarle la arquitectura.
- **Backoffice:** *Proveedores*: cobertura, salud, costo medido contra el contratado,
  y el control manual de prioridad para conmutar sin esperar la métrica.

## Restricciones aplicables

`R-SEG-01` · `R-BIL-06` · `R-BIL-10` · `R-BIL-12` · `R-LIC-01` · `R-AUD-01` ·
`R-AUD-04` · `R-RIS-03`

## Evidencia que deja

[[proveedor_pago]] · [[enlace_pago_rapido]] · [[intento_pago]] · [[orden_cobro]] ·
[[qr_cobro]] · [[webhook_pasarela]] · [[costo_proveedor_operacion]] ·
[[contrato_tercero]] · [[evaluacion_tercero]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un proveedor con contrato y pruebas completas
Cuando se lo activa
Entonces recibe tráfico según su prioridad y su salud se empieza a medir

Dado un proveedor sin soporte de consulta de estado
Cuando una orden sufre timeout
Entonces queda EN_VERIFICACION y no se acredita hasta conciliar

Dada una orden ya intentada con un proveedor caído
Cuando se conmuta a otro proveedor
Entonces se usa la misma clave de idempotencia y no se cobra dos veces

Dado un intento de guardar una credencial en la tabla del proveedor
Cuando se envía
Entonces se rechaza con CREDENCIAL_EN_TABLA
```

## Ver también

[[CU-10 Recargar saldo]] · [[CU-18 Registrar y verificar una cuenta bancaria de destino]] · [[CU-19 Reembolsar un pago y atender una disputa]] · [[CU-21 Cobrar el aporte del período]] · [[CU-28 Emitir la orden de desembolso y ejecutar el intento]] · [[CU-50 Conciliar la custodia y verificar el encaje]]
