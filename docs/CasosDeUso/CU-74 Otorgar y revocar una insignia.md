---
tags:
  - caso-uso
  - modulo/06-transparencia-y-reputacion
codigo: CU-74
criticidad: baja
actores: [Sistema, Usuario]
normas: [Transparencia, no discriminación arbitraria]
---

# CU-74 — Otorgar y revocar una insignia

> **Objetivo.** Reconocer conductas concretas —terminar un ciclo sin atrasarse,
> avalar y responder, sostener un grupo— con un criterio publicado que el usuario
> pueda leer antes de esforzarse por cumplirlo.

## Actores y disparador

- **Actor principal:** el sistema.
- **Disparadores:** cierre de un grupo; recálculo de reputación
  ([[CU-71 Recalcular el puntaje de reputación]]); evento de dominio que completa un
  criterio; revocación por hecho posterior.

## Precondiciones

1. Existe [[insignia_logro]] con `codigo`, `nombre`, `descripcion`, `criterio`
   publicado e `icono_url`.
2. El criterio es **verificable con datos del sistema**: no hay insignias otorgadas
   a dedo.

## Flujo principal

1. Al ocurrir un evento relevante, el evaluador revisa las [[insignia_logro]] cuyo
   criterio pueda haberse cumplido con ese hecho — no todas, solo las afectadas.
2. Si el criterio se cumple, se crea [[insignia_otorgada]] con `usuario_id`,
   `insignia_id` y `otorgada_en`. **Una insignia por usuario** (`R-REP-05`): el
   reintento del outbox no la duplica.
3. Se notifica al usuario con **qué hizo para ganarla**, no solo que la ganó. El
   reconocimiento sin motivo es una calcomanía; con motivo, es información.
4. Las insignias son públicas dentro del perfil que el usuario decide compartir con
   su grupo, y forman parte del [[certificado_reputacion]]
   ([[CU-75 Emitir un certificado de reputación verificable]]).
5. **Revocación.** Si un hecho posterior invalida el logro —el incumplimiento que se
   creía saldado se reabre, la reseña que la sostenía se modera como falsa— se
   escribe `revocada_en` y `motivo_revocacion`. **La fila no se borra** y el usuario
   recibe la explicación.
6. Las insignias **no otorgan derechos económicos ni cambian el precio por sí solas**.
   Si algo debe abaratar, es el [[segmento_comercial]]
   ([[CU-36 Segmentar comercialmente y aplicar precio diferenciado]]) con su
   criterio propio: mezclar reconocimiento con tarifa vuelve opaco a los dos.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El evento llega dos veces | La unicidad por usuario e insignia lo corta (`R-REP-05`) |
| 1a | Cambia el criterio de una insignia | Se versiona: quien ya la tiene la conserva, y el criterio nuevo rige hacia adelante. **No se quitan logros por cambiar la regla** |
| 5a | Se revoca y después se demuestra que el hecho era correcto | Se otorga de nuevo con nota de que hubo revocación indebida, y ambas quedan visibles |
| — | Insignia retirada del catálogo | Deja de otorgarse; las ya otorgadas siguen visibles con la marca de discontinuada |
| — | Usuario que pide no mostrar sus insignias | Se respeta: la visibilidad es del usuario, el registro es del sistema |
| — | Insignia que resulta estigmatizante al revés (por ausencia) | Se revisa el diseño: **no hay insignias negativas**; lo negativo va al historial, que no es público |

## Postcondiciones

- Toda insignia otorgada tiene un hecho verificable detrás y una fecha.
- Ninguna revocación borra el registro de que alguna vez se otorgó.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU74 = z.object({
  usuarioId:  z.string().uuid(),
  insigniaCodigo: z.string().max(40),
  referenciaTipo: z.string().max(40),
  referenciaId:   z.string().uuid(),
}).strict()

export const EntradaRevocarCU74 = z.object({
  otorgadaId: z.string().uuid(),
  motivoRevocacion: z.string().min(10).max(160),
}).strict()

export const SalidaCU74 = z.object({
  otorgadaId: z.string().uuid(),
  insignia: z.object({
    codigo: z.string(), nombre: z.string(), descripcion: z.string(), iconoUrl: z.string(),
  }),
  otorgadaEn: z.string().datetime(),
  motivoLegible: z.string(),
  revocada: z.boolean(),
}).strict()

export const ErroresCU74 = {
  INSIGNIA_INEXISTENTE: 'AP-CU74-01',
  CRITERIO_NO_CUMPLIDO: 'AP-CU74-02',
  YA_OTORGADA:          'AP-CU74-03',
  YA_REVOCADA:          'AP-CU74-04',
  REFERENCIA_INVALIDA:  'AP-CU74-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `INSIGNIA_INEXISTENTE` | El código no existe o la insignia fue discontinuada |
| `CRITERIO_NO_CUMPLIDO` | La verificación contra los datos no da: **no se otorga por pedido** |
| `YA_OTORGADA` | El usuario ya la tiene (`R-REP-05`); el reintento es inocuo |
| `YA_REVOCADA` | Se intenta revocar una revocada |
| `REFERENCIA_INVALIDA` | El hecho que la sustenta no existe |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `cumpleCriterio(criterio, hechos)` | Evalúa contra los datos del usuario; puro |
| Átomo | `insigniasAfectadasPor(evento)` | Filtra el catálogo por el hecho ocurrido; puro |
| Molécula | `InsigniaRepositorio` | Catálogo y otorgadas, con unicidad |
| Organismo | `CU74EvaluarInsignias` | Transacción: otorga, notifica y emite evento |
| Página | Consumidor de `evento_dominio` · `GET /perfil/insignias` | Sin endpoint de otorgamiento manual |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `insignia.otorgada` | Notificación con el motivo y actualización del perfil | — |
| `insignia.revocada` | Notificación con la explicación y actualización del certificado | `REPUTACION_ADMINISTRAR` |
| — | Trabajo que reevalúa insignias tras cada recálculo de reputación | — |

## Interfaz

- **App:** *Mi perfil → Logros*: las obtenidas con su fecha y su motivo, y las
  disponibles con **cuánto falta** para cada una. La barra de progreso es la parte
  que sirve.
- **Backoffice:** catálogo de insignias, cuántas se otorgaron y el detalle de
  revocaciones.

## Restricciones aplicables

`R-REP-01` · `R-REP-05` · `R-AUD-01` · `R-AUD-04` · `R-SEG-03`

## Evidencia que deja

[[insignia_logro]] · [[insignia_otorgada]] · [[evento_reputacion]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un usuario que cierra su primer grupo sin atrasos
Cuando se procesa el cierre
Entonces se otorga la insignia correspondiente con su motivo legible

Dado un usuario que ya tiene una insignia
Cuando el evento se procesa de nuevo
Entonces no se crea una segunda fila

Dada una insignia otorgada por un hecho que luego se invalida
Cuando se revoca
Entonces queda revocada_en y motivo_revocacion, sin borrar la fila

Dado un cambio en el criterio de una insignia
Cuando se publica la versión nueva
Entonces quienes ya la tenían la conservan
```

## Ver también

[[CU-70 Registrar un evento de reputación]] · [[CU-71 Recalcular el puntaje de reputación]] · [[CU-75 Emitir un certificado de reputación verificable]] · [[CU-76 Reseñar a un participante y moderar la reseña]]
