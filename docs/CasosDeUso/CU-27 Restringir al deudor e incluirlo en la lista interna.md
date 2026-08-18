---
tags:
  - caso-uso
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
  - modulo/01-identidad-usuarios-y-seguridad
codigo: CU-27
criticidad: alta
actores: [Sistema, Cobranza, Oficial de cumplimiento, Usuario]
normas: [ASFI Consumidor Financiero, protección de datos, debido proceso]
---

# CU-27 — Restringir al deudor e incluirlo en la lista interna

> **Objetivo.** Que un deudor confirmado no siga entrando a grupos nuevos mientras
> no regularice, con una restricción proporcional, temporal y reversible — y que
> salir de la lista al pagar sea automático, no un favor que haya que pedir.

## Actores y disparador

- **Actor principal:** el sistema; **quien levanta** una restricción es cobranza o
  el oficial de cumplimiento, nunca quien la aplicó.
- **Disparadores:** incumplimiento `CONFIRMADO`; deuda vencida más allá del plazo de
  política; orden de autoridad; hallazgo de fraude confirmado.

## Precondiciones

1. Existe [[registro_incumplimiento]] confirmado o [[deuda_participante]] vencida.
2. Existe política vigente que define qué nivel de restricción corresponde a qué
   situación. **La restricción se aplica por regla escrita, no por criterio del
   operador de turno.**

## Flujo principal

1. Se crea [[lista_restriccion_interna]] con `usuario_id`, `registro_origen_id`,
   `motivo`, `nivel_restriccion`, `monto_adeudado` y `vigente_hasta`.
2. En paralelo se crean las [[restriccion_usuario]] concretas, que son las que el
   sistema realmente consulta, con `tipo`, `origen`, `referencia_origen_id`,
   `valor_limite` y vigencia:
   - `SIN_GRUPOS_NUEVOS` — no puede postular ni aceptar invitaciones;
   - `TOPE_DE_APORTE` — puede seguir, con `valor_limite` reducido;
   - `SIN_ORGANIZAR` — no puede crear ni administrar grupos;
   - `SOLO_LECTURA` — reservada a fraude confirmado u orden de autoridad.
3. **La restricción nunca alcanza el dinero propio**: el usuario siempre puede ver
   su saldo, recibir lo que le corresponde, pagar lo que debe y reclamar. Restringir
   la operatoria comercial es legítimo; retener el dinero de alguien porque debe en
   otro lado, no.
4. Se notifica al usuario con el motivo exacto, el monto que lo saca de la lista y
   **cómo pagarlo en un toque**. Una restricción que no explica cómo levantarla es
   una sanción encubierta.
5. Cada evaluación de ingreso a un grupo ([[CU-68 Postular a un grupo y ser emparejado]])
   y cada alta de organizador ([[CU-90 Postular a organizador y habilitarse]])
   consultan estas restricciones antes de decidir.
6. **Al saldarse la deuda**, en la misma transacción que acredita el último pago:
   - se escribe `retirado_en`, `retirado_por` y `motivo_retiro` en la lista;
   - se cierra la vigencia de las restricciones asociadas;
   - se emite `evento_dominio` `restriccion.levantada` y se notifica.
7. La fila **no se borra nunca** (`R-AUD-01`): queda el histórico de que estuvo
   restringido, cuánto y por qué, con su fecha de salida.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El incumplimiento está en apelación | Se aplica el nivel mínimo mientras se resuelve; la apelación no es un permiso para seguir endeudándose, pero tampoco justifica el máximo |
| 2a | Ya existe restricción vigente del mismo tipo | Se actualiza el monto y la vigencia; no se acumulan dos restricciones idénticas |
| 4a | El usuario paga parcialmente | El nivel baja según la escalera de la política, pero no se levanta hasta el saldo cero |
| 6a | Prospera la apelación después del levantamiento | Se registra la revocación con motivo y **se compensa la reputación**; el histórico muestra que la restricción fue indebida |
| — | La deuda se castiga por incobrable | La restricción **sigue vigente**: castigar contablemente no es perdonar |
| — | Acuerdo de quita homologado | Se levanta al cumplirse el plan, no al firmarlo ([[plan_regularizacion]]) |
| — | Orden de autoridad | Se aplica `SOLO_LECTURA` por [[CU-17 Bloquear saldo por orden de autoridad]] y solo se levanta con otra orden |
| — | El usuario reclama la restricción | Va por [[CU-52 Atender un reclamo en plazo]]; la restricción sigue mientras se resuelve, y si el reclamo prospera se revierte con compensación |

## Postcondiciones

- Ningún usuario restringido entra a un grupo nuevo mientras dure la causa.
- Ningún usuario queda restringido después de pagar, sin que nadie tenga que
  acordarse de sacarlo.

## Contrato · `openapi/garantia.yaml`

```ts
export const EntradaCU27 = z.object({
  usuarioId: z.string().uuid(),
  origen:    z.enum(['INCUMPLIMIENTO','DEUDA_VENCIDA','FRAUDE','ORDEN_AUTORIDAD']),
  referenciaOrigenId: z.string().uuid(),
  nivelRestriccion:   z.enum(['LEVE','MODERADA','SEVERA']),
  montoAdeudado:      MontoSchema,
  vigenteHasta:       z.string().datetime().nullable(),
}).strict()

export const EntradaLevantarCU27 = z.object({
  listaId: z.string().uuid(),
  motivoRetiro: z.string().min(10).max(300),
}).strict()

export const SalidaCU27 = z.object({
  listaId: z.string().uuid(),
  restricciones: z.array(z.object({
    tipo: z.enum(['SIN_GRUPOS_NUEVOS','TOPE_DE_APORTE','SIN_ORGANIZAR','SOLO_LECTURA']),
    valorLimite: MontoSchema.nullable(),
    vigenteHasta: z.string().datetime().nullable(),
  })),
  montoParaLevantar: MontoSchema,
  puedeOperarSuSaldo: z.literal(true),
}).strict()

export const ErroresCU27 = {
  SIN_CAUSA_VIGENTE:     'AP-CU27-01',
  RESTRICCION_DUPLICADA: 'AP-CU27-02',
  DEUDA_NO_SALDADA:      'AP-CU27-03',
  MISMO_OPERADOR:        'AP-CU27-04',
  NIVEL_DESPROPORCIONADO:'AP-CU27-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_CAUSA_VIGENTE` | No hay incumplimiento confirmado, deuda vencida ni orden que la sustente |
| `RESTRICCION_DUPLICADA` | Ya existe una vigente del mismo tipo y origen |
| `DEUDA_NO_SALDADA` | Se intenta levantar con saldo pendiente |
| `MISMO_OPERADOR` | Quien levanta es quien aplicó (`R-SEG-04`) |
| `NIVEL_DESPROPORCIONADO` | El nivel pedido excede el que la política asigna a esa causa |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `nivelSegunPolitica(causa, monto, antiguedad)` | Traduce la situación al nivel que corresponde; puro |
| Átomo | `restriccionesDe(nivel)` | Expande el nivel en las restricciones concretas; puro |
| Molécula | `ListaRestriccionRepositorio` · `RestriccionUsuarioRepositorio` | Persistencia y consulta de vigentes |
| Molécula | `EvaluadorDeRestricciones` | Responde "¿puede hacer X?" con el motivo, para que la interfaz lo explique |
| Organismo | `CU27AplicarRestriccion` · `CU27LevantarRestriccion` | Transacción: lista, restricciones, notificación y evento |
| Página | `POST /restricciones` · `DELETE /restricciones/:id` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `restriccion.aplicada` | Notificación con motivo y monto para levantarla | `COBRANZA_GESTIONAR` |
| `restriccion.levantada` | Notificación y rehabilitación inmediata | Automático al saldar |
| — | Trabajo diario que vence restricciones cumplidas y revisa saldos cero | — |

## Interfaz

- **App:** aviso persistente pero no bloqueante con el motivo, el monto y el botón
  de pago. Al intentar postular a un grupo, el mensaje dice **por qué** no puede y
  **qué** lo habilita, no un "no disponible" seco.
- **Backoffice:** lista interna con antigüedad, monto y causa, y el botón de
  levantamiento con doble firma.

## Restricciones aplicables

`R-SEG-03` · `R-SEG-04` · `R-AUD-01` · `R-AUD-04` · `R-GAR-05` · `R-CON-05`

## Evidencia que deja

[[lista_restriccion_interna]] · [[restriccion_usuario]] ·
[[registro_incumplimiento]] · [[deuda_participante]] · [[bitacora_evento]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un incumplimiento confirmado con deuda de Bs 800
Cuando se aplica la restricción
Entonces existe una restriccion_usuario de tipo SIN_GRUPOS_NUEVOS vigente
Y el usuario sigue pudiendo consultar y retirar su propio saldo

Dado un usuario restringido
Cuando intenta postular a un grupo
Entonces la postulación se rechaza indicando el motivo y el monto que la levanta

Dado un usuario restringido que paga la totalidad de su deuda
Cuando se acredita el último pago
Entonces la lista queda con retirado_en y motivo_retiro en la misma transacción
Y sus restricciones dejan de estar vigentes

Dada una deuda castigada por incobrable
Cuando corre el proceso de castigo
Entonces la restricción sigue vigente
```

## Ver también

[[CU-17 Bloquear saldo por orden de autoridad]] · [[CU-25 Declarar el incumplimiento con descargo y evidencia]] · [[CU-26 Ejecutar el aval y subrogar la deuda]] · [[CU-52 Atender un reclamo en plazo]] · [[CU-68 Postular a un grupo y ser emparejado]] · [[CU-97 Anticipar el riesgo con alertas tempranas]]
