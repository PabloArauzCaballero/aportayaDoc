---
tags:
  - caso-uso
  - modulo/08-garantia-incumplimiento-cobranza-y-sanciones
codigo: CU-25
criticidad: alta
actores: [Sistema, Participante, Organizador, Grupo]
normas: [Debido proceso, ASFI Consumidor Financiero, gobernanza del grupo]
---

# CU-25 — Declarar el incumplimiento con descargo y evidencia

> **Objetivo.** Que declarar a alguien incumplido sea un acto con procedimiento
> —aviso, plazo para explicarse, evidencia archivada y decisión motivada— y no la
> consecuencia automática de un contador que llegó a cero.

## Actores y disparador

- **Actor principal:** el sistema, que detecta; **quien decide** es el grupo o el
  organizador según [[politica_sancion]].
- **Disparadores:** vencido el plazo de gracia de una [[obligacion_aporte]];
  devolución de un débito autorizado; reincidencia dentro de la ventana.

## Precondiciones

1. La obligación está vencida y su [[politica_mora]] agotada.
2. Existe [[politica_sancion]] vigente para el grupo —o la general si el grupo no
   definió la suya— con `plazo_descargo_dias`, `plazo_apelacion_dias`,
   `prescribe_en_dias` y `requiere_acuerdo_grupo`.
3. Se agotaron los avisos de [[CU-81 Programar recordatorios de aporte]]: **no se
   declara incumplido a quien nunca fue notificado**, y eso se prueba con los acuses.

## Flujo principal

1. Se crea [[registro_incumplimiento]] en estado `PRESUNTO`, con la obligación que
   lo origina, el monto y la fecha de detección. **La fecha límite de descargo se
   calcula en ese momento y se guarda**, en días hábiles según
   [[CU-59 Mantener el calendario de días no hábiles]].
2. Se adjunta la evidencia automática como [[evidencia_incumplimiento]] con
   `es_inmutable = true`: la obligación impaga, los recordatorios enviados con sus
   acuses, los intentos de débito fallidos y el saldo disponible al vencimiento.
   Cada pieza lleva `hash_archivo` cuando es un documento.
3. Se notifica al participante por todos sus canales verificados, con **qué se le
   imputa, cuánto y hasta cuándo puede responder**.
4. El participante presenta [[descargo_participante]] y puede aportar su propia
   [[evidencia_incumplimiento]] (`aportada_por`): comprobante de pago no conciliado,
   falla del canal, hecho de fuerza mayor.
5. Vencido el plazo, se decide:
   - **descargo aceptado** → `REGULARIZADO` o `DESESTIMADO`, y si hubo pago no
     conciliado se abre [[comprobante_manual]] y se corrige la conciliación;
   - **descargo rechazado o ausente** → `CONFIRMADO`, y recién ahí se habilitan
     [[CU-23 Cubrir un incumplimiento con el fondo]], la [[sancion]] y
     [[CU-66 Reemplazar a un participante moroso]].
6. **Cada cambio de estado escribe [[historial_estado_incumplimiento]]** con
   `estado_anterior`, `estado_nuevo`, `motivo`, `monto_asociado`, quién lo ejecutó y
   `es_automatico`. Esa tabla es el expediente: sin ella no hay debido proceso que
   demostrar.
7. Confirmado, se registra [[evento_reputacion]] negativo y se emite
   `evento_dominio` `incumplimiento.confirmado`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | El participante no tiene canal verificado activo | El plazo **no corre** hasta que se acredite el aviso por otra vía; un plazo que corre sin notificación no es un plazo |
| 4a | Paga durante el plazo de descargo | El registro pasa a `REGULARIZADO`; queda el antecedente pero sin sanción |
| 4b | Presenta comprobante de un pago que sí existía | Se corrige la conciliación, se desestima el incumplimiento y **se revierte el evento de reputación con un compensatorio** |
| 5a | El reglamento exige acuerdo del grupo y no se alcanza | El registro queda `PRESUNTO` hasta que se resuelva; no se confirma por silencio |
| 6a | Se intenta editar una evidencia ya cargada | Imposible: `es_inmutable` y la tabla es *append-only* (`R-AUD-01`) |
| — | El incumplimiento prescribe | Cumplido `prescribe_en_dias` sin confirmar, se cierra como `PRESCRITO` y no se puede reabrir por el mismo hecho |
| — | Reincidencia dentro de la ventana | La política puede acortar el plazo de descargo, nunca eliminarlo |
| — | Fuerza mayor documentada | El grupo puede condonar por [[acuerdo]] ([[CU-63 Proponer y votar un acuerdo]]); queda el motivo y la votación |

## Postcondiciones

- Todo incumplimiento confirmado tiene aviso probado, plazo cumplido, evidencia
  archivada y decisión motivada.
- El expediente completo se puede exhibir sin reconstruir nada.

## Contrato · `openapi/garantia.yaml`

```ts
export const EntradaCU25 = z.object({
  claveIdempotencia: z.string().uuid(),
  obligacionId: z.string().uuid(),
}).strict()

export const EntradaDescargoCU25 = z.object({
  registroId: z.string().uuid(),
  argumento:  z.string().min(20).max(2000),
  evidencias: z.array(z.object({
    tipo: z.enum(['COMPROBANTE','CAPTURA','DOCUMENTO','DECLARACION']),
    descripcion: z.string().max(300),
    urlArchivo: z.string().url().nullable(),
    hashArchivo: z.string().length(64).nullable(),
  })).max(10),
}).strict()

export const SalidaCU25 = z.object({
  registroId: z.string().uuid(),
  estado: z.enum(['PRESUNTO','CONFIRMADO','REGULARIZADO','DESESTIMADO','PRESCRITO']),
  fechaLimiteDescargo: z.string().datetime(),
  evidenciasAutomaticas: z.number().int(),
  habilitaCobertura: z.boolean(),
}).strict()

export const ErroresCU25 = {
  OBLIGACION_NO_VENCIDA:   'AP-CU25-01',
  SIN_AVISO_PROBADO:       'AP-CU25-02',
  PLAZO_DESCARGO_VENCIDO:  'AP-CU25-03',
  DESCARGO_DUPLICADO:      'AP-CU25-04',
  SIN_POLITICA_SANCION:    'AP-CU25-05',
  REGISTRO_YA_CERRADO:     'AP-CU25-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `OBLIGACION_NO_VENCIDA` | Todavía corre el plazo de gracia de [[politica_mora]] |
| `SIN_AVISO_PROBADO` | No hay acuses de los recordatorios: **primero se avisa, después se imputa** |
| `PLAZO_DESCARGO_VENCIDO` | El descargo llegó fuera de plazo; se registra igual como antecedente, sin efecto suspensivo |
| `DESCARGO_DUPLICADO` | Ya presentó descargo para ese registro |
| `SIN_POLITICA_SANCION` | No hay política vigente y sin ella no hay plazos que aplicar |
| `REGISTRO_YA_CERRADO` | Está regularizado, desestimado o prescrito |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularPlazoHabil(desde, dias, calendario)` | Suma días hábiles salteando [[dia_no_habil]]; puro |
| Átomo | `armarEvidenciaAutomatica(obligacion, avisos, intentos)` | Estructura lo que ya sabemos; puro |
| Molécula | `RegistroIncumplimientoRepositorio` | Persistencia con bloqueo por obligación |
| Molécula | `HistorialEstadoRepositorio` | Escribe la transición junto con el cambio, nunca después |
| Organismo | `CU25DeclararIncumplimiento` · `CU25ResolverDescargo` | Transacción: estado, historial, evidencia, reputación y evento |
| Página | `POST /incumplimientos` · `POST /incumplimientos/:id/descargo` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `incumplimiento.presunto` | Notificación con plazo y trabajo de vencimiento del descargo | — |
| `incumplimiento.confirmado` | Cobertura del fondo, sanción, reputación y cobranza | `INCUMPLIMIENTO_RESOLVER` |
| `incumplimiento.regularizado` | Cancela sanción y compensa la reputación | — |
| — | Trabajo diario que cierra descargos vencidos y prescribe registros | — |

## Interfaz

- **App:** *Mis obligaciones → aviso de incumplimiento*: qué se le imputa, hasta
  cuándo puede responder, un formulario para explicarse y adjuntar, y el estado del
  expediente en cada momento.
- **Backoffice:** expediente por registro, con la línea de tiempo del historial de
  estados y toda la evidencia de ambas partes.

## Restricciones aplicables

`R-AUD-01` · `R-AUD-04` · `R-GAR-01` · `R-GAR-02` · `R-CON-01`

## Evidencia que deja

[[registro_incumplimiento]] · [[evidencia_incumplimiento]] ·
[[historial_estado_incumplimiento]] · [[descargo_participante]] ·
[[politica_sancion]] · [[evento_reputacion]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dada una obligación vencida con recordatorios acusados
Cuando se declara el incumplimiento
Entonces el registro queda PRESUNTO con fecha_limite_descargo guardada
Y existen evidencias automáticas con es_inmutable en true

Dado un registro presunto sin ningún recordatorio acusado
Cuando se intenta declararlo
Entonces se rechaza con SIN_AVISO_PROBADO

Dado un participante que paga dentro del plazo de descargo
Cuando corre el cierre del plazo
Entonces el registro queda REGULARIZADO
Y no se habilita la cobertura del fondo

Dado un registro confirmado
Cuando se intenta modificar una evidencia ya cargada
Entonces la base lo rechaza por append-only
```

## Ver también

[[CU-23 Cubrir un incumplimiento con el fondo]] · [[CU-26 Ejecutar el aval y subrogar la deuda]] · [[CU-27 Restringir al deudor e incluirlo en la lista interna]] · [[CU-59 Mantener el calendario de días no hábiles]] · [[CU-66 Reemplazar a un participante moroso]] · [[CU-97 Anticipar el riesgo con alertas tempranas]]
