---
tags:
  - caso-uso
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-57
criticidad: alta
actores: [Responsable del punto, Tesorería, Usuario, Sistema]
normas: [ASFI puntos de atención y corresponsalía, BCB efectivo, UIF]
---

# CU-57 — Operar un punto de atención y arquear el efectivo

> **Objetivo.** Que el efectivo que entra y sale por un corresponsal cuadre todos
> los días contra lo que el sistema dice, y que la diferencia, si la hay, aparezca
> ese mismo día con nombre y monto en vez de acumularse.

## Actores y disparador

- **Actor principal:** responsable del [[punto_atencion]].
- **Disparadores:** apertura y cierre de la jornada; recarga en efectivo
  ([[CU-10 Recargar saldo]]); retiro en efectivo ([[CU-11 Retirar saldo]]); arqueo
  sorpresivo de tesorería.

## Precondiciones

1. El [[punto_atencion]] está `HABILITADO`, con `limite_efectivo_diario` y
   `responsable_usuario_id` asignado.
2. El punto está dentro del alcance de la licencia
   ([[CU-46 Verificar el alcance de la licencia]]): operar efectivo por
   corresponsales no autorizados es operar fuera de licencia.
3. El responsable tiene el rol correspondiente vigente
   ([[CU-08 Asignar y revocar roles de operador]]).

## Flujo principal

1. Al abrir la jornada se crea [[arqueo_punto_atencion]] del día con
   `saldo_inicial`, que es el `saldo_contado` del cierre anterior. **Si no cuadran,
   la jornada no abre**: arrastrar una diferencia es perderla.
2. Cada operación de efectivo se registra en el acto:
   - **recarga** → se recibe efectivo, se acredita saldo al usuario y se genera la
     [[constancia_pago]] con su comprobante;
   - **retiro** → se entrega efectivo contra [[orden_retiro]] aprobada, con
     verificación de identidad y MFA del titular (`R-BIL-09`).
3. Cada operación acumula en `total_recargas` o `total_retiros` y actualiza el
   `saldo_teorico`, que es `saldo_inicial + recargas − retiros`. **Ese número lo
   calcula el sistema; el punto no lo escribe.**
4. Al cerrar, el responsable cuenta el efectivo e informa `saldo_contado`. La
   `diferencia` es **columna derivada**: nadie la escribe a mano.
5. **En la misma transacción** que cierra:
   - se fija `estado` según la diferencia: `CUADRADO`, `SOBRANTE` o `FALTANTE`;
   - toda diferencia distinta de cero exige `observaciones`;
   - se escribe `cerrado_en` y se emite `evento_dominio` `arqueo.cerrado`.
6. Un faltante por encima del umbral de política abre
   [[evento_riesgo_operativo]] ([[CU-54 Registrar un evento de riesgo operativo]]) y,
   si hay indicio de apropiación, [[incidente_seguridad]].
7. El efectivo del punto se concilia contra la [[cuenta_custodia]] en el cierre
   diario: **el efectivo en poder del corresponsal es parte de la custodia y cuenta
   para el encaje** ([[CU-50 Conciliar la custodia y verificar el encaje]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El cierre anterior quedó descuadrado | La jornada no abre hasta resolver o autorizar la apertura con constancia |
| 2a | Se supera el `limite_efectivo_diario` | Las operaciones nuevas se rechazan hasta remesar; el límite protege al corresponsal antes que a nosotros |
| 2b | Recarga en efectivo por encima del umbral UIF | Se exige [[declaracion_origen_fondos]] antes de acreditar ([[CU-41 Detectar umbral y registrar formulario PCC-01]]) |
| 4a | Se intenta cerrar sin contar | Rechazo: `saldo_contado` es obligatorio y no se precarga con el teórico |
| 4b | Diferencia sin observación | Rechazo `DIFERENCIA_SIN_JUSTIFICAR` |
| 5a | Faltante reiterado en el mismo punto | Se suspende el punto y se abre investigación; el patrón importa más que el monto |
| — | Arqueo sorpresivo de tesorería | Se registra como arqueo adicional del día, sin cerrar la jornada, y su diferencia se trata igual |
| — | Caída de conectividad en el punto | Las operaciones se registran en contingencia con numeración propia y se concilian al reconectar; **no se acredita saldo sin registro** |
| — | El responsable cambia a mitad de jornada | Se cierra un arqueo parcial y se abre otro: cada tramo tiene su responsable con nombre |

## Postcondiciones

- Cada punto y cada día tiene exactamente un arqueo cerrado, con su diferencia
  derivada y justificada.
- El efectivo en puntos está incluido en la custodia conciliada del día.

## Contrato · `openapi/nucleo-financiero.yaml`

```ts
export const EntradaAbrirCU57 = z.object({
  puntoAtencionId: z.string().uuid(),
  fecha: z.string().date(),
}).strict()

export const EntradaCerrarCU57 = z.object({
  arqueoId:     z.string().uuid(),
  saldoContado: MontoSchema,
  observaciones: z.string().max(300).nullable(),
}).strict()

export const SalidaCU57 = z.object({
  arqueoId: z.string().uuid(),
  saldoInicial:  MontoSchema,
  totalRecargas: MontoSchema,
  totalRetiros:  MontoSchema,
  saldoTeorico:  MontoSchema,
  saldoContado:  MontoSchema.nullable(),
  diferencia:    MontoSchema.nullable(),
  estado: z.enum(['ABIERTO','CUADRADO','SOBRANTE','FALTANTE']),
  eventoRiesgoId: z.string().uuid().nullable(),
}).strict()

export const ErroresCU57 = {
  PUNTO_NO_HABILITADO:      'AP-CU57-01',
  CIERRE_ANTERIOR_ABIERTO:  'AP-CU57-02',
  LIMITE_EFECTIVO_EXCEDIDO: 'AP-CU57-03',
  DIFERENCIA_SIN_JUSTIFICAR:'AP-CU57-04',
  ARQUEO_YA_CERRADO:        'AP-CU57-05',
  RESPONSABLE_NO_ASIGNADO:  'AP-CU57-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PUNTO_NO_HABILITADO` | El punto está suspendido, de baja o fuera del alcance de licencia |
| `CIERRE_ANTERIOR_ABIERTO` | Quedó una jornada sin cerrar o sin cuadrar |
| `LIMITE_EFECTIVO_EXCEDIDO` | La operación supera el tope diario del punto |
| `DIFERENCIA_SIN_JUSTIFICAR` | Hay diferencia y no se escribió observación |
| `ARQUEO_YA_CERRADO` | Reintento sobre un arqueo cerrado; se devuelve el existente (`R-BIL-18`) |
| `RESPONSABLE_NO_ASIGNADO` | El punto no tiene responsable con rol vigente |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `saldoTeorico(inicial, recargas, retiros)` | La suma; puro y con pruebas de propiedad |
| Átomo | `clasificarDiferencia(teorico, contado, umbral)` | Cuadrado, sobrante o faltante; puro |
| Molécula | `PuntoAtencionRepositorio` · `ArqueoRepositorio` | Persistencia y unicidad por punto y fecha |
| Molécula | `AcumuladorDeEfectivo` | Actualiza totales dentro de la transacción de cada operación |
| Organismo | `CU57CerrarArqueo` | Transacción: estado, evento de riesgo y conciliación de custodia |
| Página | `POST /puntos/:id/arqueos` · `POST /arqueos/:id/cierre` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `arqueo.abierto` | Habilita las operaciones de efectivo del punto | `PUNTO_OPERAR` |
| `arqueo.cerrado` | Conciliación de custodia y alimentación del cierre diario | `PUNTO_OPERAR` |
| `arqueo.descuadrado` | Evento de riesgo operativo y alerta a tesorería | — |
| — | Control diario de puntos sin cerrar y de límites de efectivo | — |

## Interfaz

- **App:** para el usuario, el punto aparece como lugar donde recargar o retirar,
  con horario y saldo disponible del punto para retiros.
- **Backoffice:** panel del punto con las operaciones del día, el teórico en vivo, y
  el formulario de cierre que **no muestra el teórico hasta después de informar el
  contado** — para que contar sea contar.

## Restricciones aplicables

`R-BIL-01` · `R-BIL-06` · `R-BIL-09` · `R-BIL-11` · `R-BIL-12` · `R-BIL-18` ·
`R-LIC-01` · `R-AUD-01`

## Evidencia que deja

[[punto_atencion]] · [[arqueo_punto_atencion]] · [[constancia_pago]] ·
[[movimiento_custodia]] · [[evento_riesgo_operativo]] · [[bitacora_evento]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un punto con saldo inicial de Bs 1.000 y recargas por Bs 500
Cuando cierra con saldo contado de Bs 1.500
Entonces la diferencia es 0 y el estado es CUADRADO

Dado un cierre con diferencia de Bs 20 y sin observaciones
Cuando se intenta cerrar
Entonces se rechaza con DIFERENCIA_SIN_JUSTIFICAR

Dado un punto cuya jornada anterior quedó descuadrada
Cuando se intenta abrir la jornada de hoy
Entonces se rechaza con CIERRE_ANTERIOR_ABIERTO

Dado un faltante superior al umbral de política
Cuando se cierra el arqueo
Entonces existe un evento_riesgo_operativo asociado
```

## Ver también

[[CU-10 Recargar saldo]] · [[CU-11 Retirar saldo]] · [[CU-50 Conciliar la custodia y verificar el encaje]] · [[CU-51 Ejecutar el cierre diario]] · [[CU-54 Registrar un evento de riesgo operativo]]
