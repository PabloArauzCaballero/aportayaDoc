---
tags:
  - caso-uso
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
codigo: CU-29
criticidad: alta
actores: [Sistema, Contabilidad, Participante]
normas: [ASFI Consumidor Financiero, contabilidad, transparencia]
---

# CU-29 — Devolver los aportes del fondo de garantía

> **Objetivo.** Que al cerrar el grupo cada uno recupere lo que puso en el fondo
> menos lo que su propio comportamiento consumió, con la cuenta hecha a la vista y
> sin que sobre ni falte un centavo.

## Actores y disparador

- **Actor principal:** el sistema, al cerrarse el ciclo.
- **Disparadores:** último período liquidado; disolución anticipada
  ([[CU-67 Disolver el grupo anticipadamente]]); retiro con derecho a devolución
  parcial según reglamento.

## Precondiciones

1. El [[fondo_garantia]] del grupo no tiene coberturas en curso ni deudas
   pendientes de subrogación sin resolver.
2. Todas las [[cobertura_incumplimiento]] están cerradas: repuestas, subrogadas o
   castigadas con autorización.
3. El grupo cerró sus [[periodo]] y no quedan [[obligacion_aporte]] vivas.

## Flujo principal

1. Se congela la posición del fondo: total aportado por cada participante, total
   consumido por coberturas que lo tuvieron a él como incumplido, y lo recuperado
   por [[subrogacion]] o [[abono_recuperacion]].
2. Por cada participante se crea [[devolucion_fondo]] con `monto_aportado`,
   `monto_consumido` y **`monto_a_devolver` = aportado − consumido**, nunca
   negativo: si consumió más de lo que puso, la diferencia es deuda, no una
   devolución en rojo.
3. Lo recuperado después de una cobertura **vuelve al fondo antes del reparto**, no
   al bolsillo de quien lo cobró: el fondo es del grupo, no del que gestionó.
4. **En una sola transacción**:
   - se acreditan las devoluciones en las [[cuenta_billetera]] de cada uno;
   - se registran los [[movimiento_fondo]] de salida y el [[asiento_contable]];
   - el fondo queda en cero y pasa a `CERRADO`;
   - se emite `evento_dominio` `fondo.devuelto`.
5. Cada participante recibe el desglose: cuánto puso período a período, qué
   cobertura consumió y por qué, qué se recuperó y qué le vuelve. **El número tiene
   que poder explicarse línea por línea o no se manda.**
6. Si alguien tiene deuda viva con el grupo, su devolución se compensa contra ella
   primero, con `motivo_retencion` escrito, y solo el remanente se acredita.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El consumido supera lo aportado | `monto_a_devolver` es cero y la diferencia queda como [[deuda_participante]] en cobranza |
| 4a | La suma de devoluciones no coincide con el saldo del fondo | **La transacción no confirma**; se abre [[descuadre_custodia]] y se resuelve antes de repartir (`R-GAR-06`) |
| 1a | Hay una cobertura en curso | La devolución no arranca: repartir con una cobertura viva es repartir plata que puede hacer falta mañana |
| 6a | El participante tiene deuda con otro grupo | **No se compensa**: los fondos de garantía son por grupo y la deuda ajena no se cobra con plata de este grupo |
| — | Disolución anticipada | La devolución del fondo entra en la prelación del reglamento, después de reponer las coberturas consumidas |
| — | Participante con cuenta cerrada | Se acredita igual y se le notifica por el canal de contacto conservado; el dinero no se pierde ni prescribe en silencio |
| — | Recuperación tardía de una deuda ya devuelta | Se reparte entre quienes tenían derecho, a prorrata, en una devolución complementaria |

## Postcondiciones

- El fondo cierra en cero, con una devolución por participante y su asiento.
- Nadie recibe más de lo que puso ni menos de lo que le corresponde, y puede
  verificarlo con el desglose.

## Contrato · `openapi/garantia.yaml`

```ts
export const EntradaCU29 = z.object({
  claveIdempotencia: z.string().uuid(),
  fondoId: z.string().uuid(),
  motivo:  z.enum(['CIERRE_DE_CICLO','DISOLUCION','RETIRO_INDIVIDUAL']),
  participanteId: z.string().uuid().optional(),   // solo en RETIRO_INDIVIDUAL
}).strict()

export const SalidaCU29 = z.object({
  devoluciones: z.array(z.object({
    participanteId: z.string().uuid(),
    montoAportado:  MontoSchema,
    montoConsumido: MontoSchema,
    montoADevolver: MontoSchema,
    compensadoContraDeuda: MontoSchema,
    motivoRetencion: z.string().nullable(),
  })),
  saldoFondoDespues: MontoSchema,
  fondoCerrado: z.boolean(),
}).strict()

export const ErroresCU29 = {
  COBERTURA_EN_CURSO:   'AP-CU29-01',
  OBLIGACIONES_VIVAS:   'AP-CU29-02',
  FONDO_NO_CUADRA:      'AP-CU29-03',
  FONDO_YA_CERRADO:     'AP-CU29-04',
  SUBROGACION_ABIERTA:  'AP-CU29-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `COBERTURA_EN_CURSO` | Hay una cobertura sin cerrar: no se reparte con plata comprometida |
| `OBLIGACIONES_VIVAS` | Quedan obligaciones de aporte sin resolver en el grupo |
| `FONDO_NO_CUADRA` | La suma de devoluciones difiere del saldo del fondo (`R-GAR-06`) |
| `FONDO_YA_CERRADO` | Reintento sobre un fondo cerrado; se devuelve el reparto existente |
| `SUBROGACION_ABIERTA` | Hay subrogaciones sin resolver que podrían cambiar los importes |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularDevolucion(aportado, consumido, recuperado)` | La resta y su piso en cero; puro y con pruebas de propiedad |
| Átomo | `repartirRemanente(saldo, derechos)` | Prorrata con reparto exacto del centavo residual; puro |
| Molécula | `FondoGarantiaRepositorio` | Toma el fondo para actualizar, con bloqueo |
| Molécula | `DevolucionFondoRepositorio` | Persistencia del reparto |
| Organismo | `CU29DevolverFondo` | Transacción: devoluciones, movimientos, asiento y cierre |
| Página | Trabajo `cerrar-fondo` · `POST /fondos/:id/devoluciones` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `fondo.devuelto` | Notificación con desglose a cada participante | `FONDO_ADMINISTRAR` |
| `fondo.descuadrado` | Incidente y bloqueo del reparto | — |
| — | Trabajo que dispara la devolución al cerrarse el último período | — |

## Interfaz

- **App:** *Grupo → Fondo de garantía*: lo que puso, lo que se consumió y por qué, y
  lo que va a recuperar al cierre; visible **durante todo el ciclo**, no recién al
  final.
- **Backoffice:** cuadre del fondo por grupo, con el detalle de coberturas,
  recuperaciones y el reparto propuesto antes de ejecutarlo.

## Restricciones aplicables

`R-BIL-01` · `R-BIL-02` · `R-BIL-06` · `R-AUD-01` · `R-AUD-05` · `R-GAR-06` ·
`R-GRP-13`

## Evidencia que deja

[[devolucion_fondo]] · [[fondo_garantia]] · [[movimiento_fondo]] ·
[[cobertura_incumplimiento]] · [[subrogacion]] · [[transaccion_billetera]] ·
[[asiento_contable]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un participante que aportó Bs 300 al fondo y consumió Bs 100 en coberturas
Cuando se cierra el fondo
Entonces su monto_a_devolver es 200

Dado un participante que consumió más de lo que aportó
Cuando se calcula su devolución
Entonces monto_a_devolver es 0
Y la diferencia queda como deuda_participante

Dado un fondo cuyo reparto no coincide con su saldo
Cuando se intenta ejecutar la devolución
Entonces la transacción no confirma y se abre un descuadre

Dado un fondo con una cobertura todavía abierta
Cuando se intenta cerrarlo
Entonces se rechaza con COBERTURA_EN_CURSO
```

## Ver también

[[CU-23 Cubrir un incumplimiento con el fondo]] · [[CU-26 Ejecutar el aval y subrogar la deuda]] · [[CU-65 Retirarse de un grupo]] · [[CU-67 Disolver el grupo anticipadamente]]
