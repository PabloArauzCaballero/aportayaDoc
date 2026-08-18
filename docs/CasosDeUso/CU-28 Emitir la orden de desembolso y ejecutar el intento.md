---
tags:
  - caso-uso
  - modulo/04-entregas-de-fondo
  - modulo/03-aportes-pagos-qr-y-conciliacion
codigo: CU-28
criticidad: alta
actores: [Sistema, Tesorería, Proveedor de pago, Participante]
normas: [ASFI conciliación, BCB pagos, contabilidad]
---

# CU-28 — Emitir la orden de desembolso y ejecutar el intento

> **Objetivo.** Que la plata salga una sola vez hacia la cuenta correcta, que cada
> intento fallido quede escrito con su motivo, y que el estado que ve el
> participante sea el real y no un optimismo del sistema.

## Actores y disparador

- **Actor principal:** el sistema, tras autorizarse una entrega.
- **Disparadores:** [[entrega_fondo]] autorizada ([[CU-22 Liquidar y entregar el fondo]]);
  [[orden_retiro]] aprobada ([[CU-11 Retirar saldo]]); devolución por disolución
  ([[CU-67 Disolver el grupo anticipadamente]]).

## Precondiciones

1. La entrega o el retiro está **autorizado**, con el neto ya calculado y las
   deducciones aplicadas.
2. Existe [[cuenta_bancaria_beneficiario]] con `estado_verificacion = 'VERIFICADA'`
   y fuera de la ventana de enfriamiento
   ([[CU-18 Registrar y verificar una cuenta bancaria de destino]]).
3. Hay [[proveedor_pago]] activo que atienda esa entidad y esa moneda, con saldo
   operativo suficiente en la cuenta de custodia.
4. El monto está **retenido** ([[retencion_saldo]]): no se ordena un desembolso
   contra saldo que otra operación puede gastar mientras tanto.

## Flujo principal

1. Se crea [[orden_desembolso]] con `entrega_id`, `proveedor_id`,
   `cuenta_destino_id`, `monto`, `moneda`, `glosa` y **`clave_idempotencia` derivada
   de la entrega**, en estado `CREADA`. Esa clave es la que impide pagar dos veces
   aunque el trabajo corra dos veces (`R-BIL-06`).
2. Se registra [[intento_desembolso]] número 1 con `iniciado_en` y se llama al
   proveedor. Cada llamada al proveedor es un intento, y cada intento deja fila:
   `resultado`, `codigo_error`, `mensaje_proveedor` y `reintentable_en`.
3. Según la respuesta:
   - **aceptada** → `ENVIADA`, se guarda `referencia_proveedor` y se espera el
     acuse de acreditación;
   - **rechazo definitivo** (cuenta inexistente, titular no coincide, cuenta
     cerrada) → `RECHAZADA`, **sin reintento**: reintentar un rechazo definitivo
     solo gasta intentos y confunde al usuario;
   - **error transitorio o sin respuesta** → se programa el reintento con espera
     creciente y tope de intentos.
4. Al llegar el acuse de acreditación, **en la misma transacción**:
   - la orden pasa a `ACREDITADA` con `acreditada_en`;
   - se libera la retención y se debita efectivamente el saldo;
   - se registra el [[movimiento_custodia]] y el [[asiento_contable]];
   - se emite `evento_dominio` `desembolso.acreditado`.
5. **Cada transición escribe [[historial_estado_entrega]]** con estado anterior,
   nuevo, motivo y quién la ejecutó. El participante ve esa misma línea de tiempo,
   sin traducciones piadosas.
6. Si el desembolso se traba —rechazado, sin acuse, acreditado por monto distinto—
   se abre [[incidencia_entrega]] con `tipo`, `severidad`, `sla_horas` y
   **`fecha_limite_sla` guardada**, asignada a un responsable con nombre.
7. Toda orden se cruza contra el [[extracto_bancario]] en la conciliación diaria; lo
   que no cruza es [[excepcion_conciliacion]] y **bloquea el cierre** (`R-BIL-12`).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El trabajo se ejecuta dos veces | La `clave_idempotencia` única corta la segunda: una orden por entrega |
| 3a | El proveedor responde éxito y después reporta rechazo | Manda el último acuse; se revierte el estado, se repone la retención y se abre incidencia |
| 3b | Se agotan los reintentos | La orden queda `FALLIDA`, el dinero **vuelve a estar disponible** para el beneficiario y se le ofrece otra cuenta o retiro por otro canal |
| 4a | El proveedor acredita un monto distinto | Excepción de conciliación con la diferencia; no se cierra el día hasta resolverla |
| 4b | Acreditado en la cuenta equivocada por error del proveedor | Incidencia de severidad alta, gestión de recupero con el proveedor y [[evento_riesgo_operativo]] si hay pérdida |
| 5a | El beneficiario dice que no le llegó y el proveedor dice que sí | Se pide el comprobante de acreditación al proveedor y se resuelve con evidencia; mientras tanto la incidencia queda abierta con su SLA corriendo |
| — | Cambio de proveedor a mitad de camino | Se cierra la orden y se emite una nueva; **nunca se reasigna una orden viva a otro proveedor** |
| — | Desembolso a cuenta en otra moneda | Se aplica el [[tipo_cambio]] del día, se guarda cuál se usó, y la diferencia de cambio va a su propia cuenta contable |

## Postcondiciones

- Por cada entrega autorizada existe exactamente una orden, con su historia de
  intentos completa.
- Ningún saldo queda debitado sin acuse ni acreditado sin conciliar.

## Contrato · `openapi/entregas.yaml`

```ts
export const EntradaCU28 = z.object({
  claveIdempotencia: z.string().uuid(),
  entregaId:  z.string().uuid(),
  cuentaDestinoId: z.string().uuid(),
  proveedorId: z.string().uuid().optional(),   // si se omite, enruta por prioridad
}).strict()

export const SalidaCU28 = z.object({
  ordenId: z.string().uuid(),
  estado:  z.enum(['CREADA','ENVIADA','ACREDITADA','RECHAZADA','FALLIDA','REVERSADA']),
  intentos: z.number().int(),
  referenciaProveedor: z.string().nullable(),
  proximoReintento: z.string().datetime().nullable(),
  incidenciaId: z.string().uuid().nullable(),
}).strict()

export const ErroresCU28 = {
  ENTREGA_NO_AUTORIZADA:  'AP-CU28-01',
  CUENTA_NO_VERIFICADA:   'AP-CU28-02',
  SIN_PROVEEDOR_DISPONIBLE:'AP-CU28-03',
  SALDO_NO_RETENIDO:      'AP-CU28-04',
  ORDEN_YA_EXISTE:        'AP-CU28-05',
  RECHAZO_DEFINITIVO:     'AP-CU28-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `ENTREGA_NO_AUTORIZADA` | La entrega o el retiro todavía no fue autorizado |
| `CUENTA_NO_VERIFICADA` | La cuenta destino está pendiente, rechazada o en enfriamiento |
| `SIN_PROVEEDOR_DISPONIBLE` | Ningún proveedor activo atiende esa entidad y moneda |
| `SALDO_NO_RETENIDO` | Falta la retención previa: no se ordena contra saldo suelto |
| `ORDEN_YA_EXISTE` | Reintento con la misma clave; se devuelve la orden existente |
| `RECHAZO_DEFINITIVO` | El proveedor rechazó por un motivo que no se reintenta |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `esReintentable(codigoError)` | Clasifica el error del proveedor; puro y con tabla explícita |
| Átomo | `proximoIntento(numero, politica)` | Espera creciente con jitter; puro |
| Molécula | `OrdenDesembolsoRepositorio` · `IntentoDesembolsoRepositorio` | Persistencia y unicidad por clave |
| Molécula | `EnrutadorDeProveedores` | Elige proveedor por entidad, moneda, prioridad y salud |
| Molécula | `AdaptadorDesembolso` | Uno por proveedor, misma interfaz |
| Organismo | `CU28EmitirOrden` · `CU28ProcesarAcuse` | Transacción: estado, retención, custodia, asiento y evento |
| Página | Trabajo `desembolsar` · `POST /webhooks/:proveedor/desembolsos` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `desembolso.ordenado` | Trabajo de ejecución con reintentos y aviso al beneficiario | `TESORERIA_OPERAR` |
| `desembolso.acreditado` | Liberación de retención, asiento, conciliación y aviso | — |
| `desembolso.fallido` | Reposición del saldo, incidencia con SLA y aviso con alternativa | — |
| — | Trabajo que vence SLA de incidencias y escala al responsable | — |

## Interfaz

- **App:** *Mi entrega*: la línea de tiempo real —autorizada, enviada al banco,
  acreditada— con la fecha de cada paso y, si algo falla, qué se está haciendo y
  qué puede hacer él.
- **Backoffice:** cola de desembolsos por estado y antigüedad, con los intentos y el
  mensaje literal del proveedor, e incidencias ordenadas por SLA restante.

## Restricciones aplicables

`R-BIL-01` · `R-BIL-02` · `R-BIL-06` · `R-BIL-09` · `R-BIL-12` · `R-AUD-01` ·
`R-AUD-05` · `R-DES-01` · `R-DES-02`

## Evidencia que deja

[[orden_desembolso]] · [[intento_desembolso]] · [[historial_estado_entrega]] ·
[[incidencia_entrega]] · [[movimiento_custodia]] · [[asiento_contable]] ·
[[excepcion_conciliacion]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dada una entrega autorizada con cuenta destino verificada
Cuando se emite la orden de desembolso
Entonces existe una orden_desembolso con clave_idempotencia
Y un intento_desembolso número 1

Dada una orden ya emitida
Cuando el trabajo se ejecuta de nuevo con la misma clave
Entonces se devuelve la orden existente y no se crea una segunda

Dado un proveedor que responde cuenta inexistente
Cuando se procesa la respuesta
Entonces la orden queda RECHAZADA sin programar reintento
Y el saldo vuelve a estar disponible para el beneficiario

Dada una orden acreditada que no cruza con el extracto bancario
Cuando se intenta cerrar el día
Entonces el cierre no puede marcarse cuadrado
```

## Ver también

[[CU-11 Retirar saldo]] · [[CU-18 Registrar y verificar una cuenta bancaria de destino]] · [[CU-22 Liquidar y entregar el fondo]] · [[CU-50 Conciliar la custodia y verificar el encaje]] · [[CU-99 Dar de alta un proveedor de pago y enrutar el cobro]]
