---
tags:
  - caso-uso
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
codigo: CU-26
criticidad: alta
actores: [Sistema, Avalista, Organizador, Cobranza]
normas: [Debido proceso, ASFI Consumidor Financiero, contabilidad]
---

# CU-26 — Ejecutar el aval y subrogar la deuda

> **Objetivo.** Que quien firmó como avalista responda cuando corresponde, sabiendo
> desde el primer día a qué se comprometió, y que su pago genere una deuda del
> incumplido hacia él, no un regalo.

## Actores y disparador

- **Actor principal:** el sistema, al confirmarse un incumplimiento.
- **Disparadores:** [[registro_incumplimiento]] en estado `CONFIRMADO` con
  [[aval_participante]] vigente; agotada la gestión directa con el deudor.

## Precondiciones

1. Existe [[aval_participante]] aceptado por el avalista, con su alcance y tope
   explícitos, firmado **antes** del hecho que se ejecuta.
2. El incumplimiento está `CONFIRMADO` tras el descargo
   ([[CU-25 Declarar el incumplimiento con descargo y evidencia]]): **no se ejecuta
   un aval sobre un incumplimiento discutible**.
3. Existe [[deuda_participante]] con el monto exigible.

## Flujo principal

1. Se crea [[ejecucion_aval]] con `aval_id`, `registro_id`, `deuda_id`,
   `monto_ejecutado` —acotado al tope del aval— y estado `NOTIFICADA`. El
   `plazo_respuesta` se calcula y se guarda.
2. Se notifica al avalista con el detalle completo: quién incumplió, cuánto, por qué
   se lo llama a él, cuál es el tope que firmó y qué pasa si paga y qué si no.
3. El avalista puede:
   - **pagar** → se registra el [[pago]] o el débito de su billetera;
   - **oponerse** → presenta descargo; si el aval no cubría ese hecho o venció, la
     ejecución se anula;
   - **no responder** → vencido el plazo, procede la ejecución forzosa si
     `genera_deuda_del_avalista = true`.
4. **En la misma transacción** que acredita el pago del avalista:
   - se imputa contra la [[deuda_participante]] del incumplido;
   - se crea la [[subrogacion]] a favor del avalista: **el que pagó pasa a ser
     acreedor del que debía**;
   - si el [[fondo_garantia]] ya había cubierto, se reparte según prelación del
     reglamento y se repone lo que corresponda;
   - se registra el [[asiento_contable]];
   - se emite `evento_dominio` `aval.ejecutado`.
5. Se registra [[evento_reputacion]]: negativo para el incumplido, y **positivo para
   el avalista que respondió** — cumplir cuesta y tiene que valer.
6. Si el avalista no paga, su propia obligación entra a [[gestion_cobranza]] y puede
   derivar en [[CU-27 Restringir al deudor e incluirlo en la lista interna]].

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El monto supera el tope del aval | Se ejecuta hasta el tope y el excedente sigue como deuda del incumplido; **el tope firmado no se estira** |
| 1b | El aval venció antes del hecho | No se ejecuta: `AVAL_NO_VIGENTE`. La fecha del hecho manda, no la de la ejecución |
| 3a | El avalista se opone con razón | La ejecución queda `ANULADA` con motivo; no hay impacto de reputación para él |
| 3b | El avalista paga parcialmente | Se imputa lo pagado y se subroga por ese importe; el resto sigue vivo |
| 4a | El incumplido paga antes que el avalista | La ejecución se cierra como `INNECESARIA` y se avisa al avalista de inmediato |
| — | Varios avalistas para la misma deuda | Se ejecutan según el orden y la proporción pactados; nunca se cobra más que la deuda entre todos |
| — | El avalista es a su vez participante del mismo grupo | Se ejecuta igual, pero el cobro no puede dejarlo sin cubrir su propio aporte del período: se prioriza su obligación propia |
| — | El incumplido paga después de la subrogación | El dinero va al avalista subrogado, no al fondo ni al grupo |

## Postcondiciones

- Nadie paga como avalista más de lo que firmó ni por un hecho fuera del alcance.
- Todo pago de avalista deja una acreencia registrada a su favor.

## Contrato · `openapi/garantia.yaml`

```ts
export const EntradaCU26 = z.object({
  claveIdempotencia: z.string().uuid(),
  registroIncumplimientoId: z.string().uuid(),
  avalId: z.string().uuid(),
  monto:  MontoSchema,
}).strict()

export const EntradaRespuestaCU26 = z.object({
  ejecucionId: z.string().uuid(),
  respuesta:   z.enum(['PAGAR', 'OPONERSE']),
  montoPago:   MontoSchema.optional(),
  argumento:   z.string().max(2000).optional(),
}).strict()

export const SalidaCU26 = z.object({
  ejecucionId: z.string().uuid(),
  estado: z.enum(['NOTIFICADA','PAGADA','PARCIAL','ANULADA','INNECESARIA','EN_COBRANZA']),
  montoEjecutado:  MontoSchema,
  topeDisponible:  MontoSchema,
  subrogacionId:   z.string().uuid().nullable(),
  plazoRespuesta:  z.string().datetime(),
}).strict()

export const ErroresCU26 = {
  AVAL_NO_VIGENTE:       'AP-CU26-01',
  INCUMPLIMIENTO_NO_FIRME:'AP-CU26-02',
  TOPE_EXCEDIDO:         'AP-CU26-03',
  EJECUCION_DUPLICADA:   'AP-CU26-04',
  FUERA_DE_ALCANCE:      'AP-CU26-05',
  DEUDA_YA_SALDADA:      'AP-CU26-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `AVAL_NO_VIGENTE` | El aval venció o fue revocado antes de la fecha del hecho |
| `INCUMPLIMIENTO_NO_FIRME` | Sigue `PRESUNTO`: primero el descargo |
| `TOPE_EXCEDIDO` | El monto pedido supera el tope firmado, sumadas las ejecuciones previas |
| `EJECUCION_DUPLICADA` | Ya hay ejecución abierta para ese aval y registro |
| `FUERA_DE_ALCANCE` | El hecho no está entre los que el aval cubre |
| `DEUDA_YA_SALDADA` | El incumplido pagó: la ejecución se cierra como innecesaria |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `topeDisponible(aval, ejecucionesPrevias)` | Cuánto queda del tope firmado; puro |
| Átomo | `cubreElHecho(aval, registro)` | Alcance y vigencia a la fecha del hecho; puro |
| Molécula | `AvalRepositorio` · `EjecucionAvalRepositorio` | Persistencia con bloqueo por aval |
| Molécula | `SubrogacionRepositorio` | Crea la acreencia del avalista |
| Organismo | `CU26EjecutarAval` | Transacción: pago, imputación, subrogación, asiento y evento |
| Página | `POST /avales/:id/ejecuciones` · `POST /ejecuciones-aval/:id/respuesta` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `aval.notificado` | Aviso al avalista y trabajo con el plazo de respuesta | — |
| `aval.ejecutado` | Subrogación, asiento y reputación de ambas partes | `COBRANZA_GESTIONAR` |
| `aval.anulado` | Aviso y cierre sin impacto de reputación | `INCUMPLIMIENTO_RESOLVER` |

## Interfaz

- **App:** *Mis avales*: a quién avaló, por cuánto, cuánto de su tope está
  comprometido y, si lo llaman, la pantalla con el detalle y los dos botones. Y en
  *Me deben*, la acreencia por subrogación.
- **Backoffice:** ejecuciones por estado y plazo, con el tope consumido por avalista.

## Restricciones aplicables

`R-BIL-01` · `R-BIL-06` · `R-AUD-01` · `R-AUD-05` · `R-GAR-03` · `R-GAR-04`

## Evidencia que deja

[[ejecucion_aval]] · [[aval_participante]] · [[subrogacion]] ·
[[deuda_participante]] · [[transaccion_billetera]] · [[asiento_contable]] ·
[[evento_reputacion]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un aval con tope de Bs 1.000 y una deuda confirmada de Bs 1.500
Cuando se ejecuta el aval
Entonces monto_ejecutado es 1.000
Y el saldo de Bs 500 sigue como deuda del incumplido

Dado un incumplimiento todavía PRESUNTO
Cuando se intenta ejecutar el aval
Entonces se rechaza con INCUMPLIMIENTO_NO_FIRME

Dado un avalista que paga la ejecución
Cuando se acredita su pago
Entonces existe una subrogacion a su favor por el monto pagado
Y el asiento contable cuadra

Dado un incumplido que paga después de la subrogación
Cuando se acredita su pago
Entonces el dinero se imputa al avalista subrogado
```

## Ver también

[[CU-23 Cubrir un incumplimiento con el fondo]] · [[CU-25 Declarar el incumplimiento con descargo y evidencia]] · [[CU-27 Restringir al deudor e incluirlo en la lista interna]] · [[CU-29 Devolver los aportes del fondo de garantía]] · [[CU-66 Reemplazar a un participante moroso]] · [[CU-69 Invitar a un contacto y registrar sus referencias]]
