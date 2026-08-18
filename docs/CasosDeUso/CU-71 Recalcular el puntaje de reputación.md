---
tags:
  - caso-uso
  - modulo/06-transparencia-y-reputacion
codigo: CU-71
criticidad: media
actores: [Sistema, Usuario]
normas: [Transparencia, no discriminación arbitraria]
---

# CU-71 — Recalcular el puntaje de reputación

> **Objetivo.** Producir un número **explicable**: no basta con decir "tenés 720";
> hay que poder decir de dónde salió cada componente y qué hay que hacer para
> subirlo. Un puntaje que no se puede explicar no se puede defender ante un reclamo.

## Actores y disparador

- **Actor principal:** el sistema.
- **Disparadores:** un [[evento_reputacion]] nuevo marcó el puntaje como sucio; el
  cierre mensual; el cambio de versión del [[modelo_scoring]].

## Precondiciones

1. Existe [[modelo_scoring]] vigente con sus [[peso_factor]].

## Flujo principal

1. Se toman los eventos de reputación del usuario dentro de la ventana del modelo,
   con su decaimiento temporal: **lo viejo pesa menos**, porque la gente cambia.
2. Se calcula cada factor —puntualidad, permanencia, incumplimientos, coberturas
   consumidas, reseñas, antigüedad— y se guarda como [[componente_score]]. Ese
   detalle es lo que hace explicable el total.
3. Se aplica la ponderación del modelo y se obtiene el total y el nivel.
4. **En la misma transacción** se cierra la vigencia del [[puntaje_reputacion]]
   anterior y se inserta el nuevo con `modelo_id`, `total`, `nivel` y
   `calculado_en` (`R-REP-02`: un solo puntaje vigente por usuario).
5. Si el nivel cambió, se emite `evento_dominio` `reputacion.nivel_cambiado` y se
   notifica con el motivo concreto, no con el número pelado.
6. Periódicamente se congela un [[snapshot_reputacion]] para poder responder
   "cuánto tenía en marzo".

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | El usuario no tiene historial suficiente | Se devuelve el puntaje base del modelo, marcado como `SIN_HISTORIAL`: **no es cero**, es "todavía no sabemos" |
| — | Cambia la versión del modelo | Se recalcula a todos con la nueva versión, pero los puntajes anteriores **conservan su `modelo_id`**: se puede explicar qué modelo regía cada día |
| — | El usuario reclama su puntaje | Se responde con los componentes y los eventos; si un evento estaba mal, se corrige por [[CU-70 Registrar un evento de reputación]] y se recalcula |
| 4a | Dos recálculos concurrentes | El bloqueo por usuario y la restricción de exclusión impiden dos puntajes vigentes |

## Postcondiciones

- Un solo puntaje vigente por usuario, con sus componentes y su modelo.
- El histórico permite reconstruir cualquier fecha.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU71 = z.object({
  usuarioId: z.string().uuid(),
  motivo:    z.enum(['EVENTO_NUEVO','CIERRE_MENSUAL','CAMBIO_DE_MODELO','RECLAMO']),
}).strict()

export const SalidaCU71 = z.object({
  puntajeId: z.string().uuid(),
  total:     z.string(),
  nivel:     z.enum(['SIN_HISTORIAL','BAJO','MEDIO','ALTO','EXCELENTE']),
  componentes: z.array(z.object({
    factor: z.string(), valor: z.string(), peso: z.string(), aporte: z.string(),
  })),
  nivelAnterior: z.string().nullable(),
  modeloVersion: z.string(),
}).strict()

export const ErroresCU71 = {
  SIN_MODELO_VIGENTE: 'AP-CU71-01',
  USUARIO_INEXISTENTE:'AP-CU71-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_MODELO_VIGENTE` | Ningún modelo de reputación está vigente en la fecha de cálculo |
| `USUARIO_INEXISTENTE` | El usuario no existe o su cuenta fue eliminada por derecho de supresión |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `decaimiento(evento, hoy, ventana)` | Peso temporal; puro |
| Átomo | `componerPuntaje(componentes, pesos)` | Suma ponderada; puro, con propiedad: el total siempre cae dentro del rango del modelo |
| Molécula | `PuntajeRepositorio` · `ModeloScoringRepositorio` | |
| Organismo | `CU71RecalcularPuntaje` | Transacción y cierre de vigencia |
| Página | `GET /usuarios/:id/reputacion` | Solo lectura; el recálculo es interno |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `reputacion.recalculada` | Nada por sí sola | Interno |
| `reputacion.nivel_cambiado` | Notificación con el motivo + revisión de elegibilidad para grupos | — |
| — | Trabajo mensual que congela el snapshot | — |

## Interfaz

- **App:** *Mi reputación*: el número grande, el nivel, y debajo **los componentes
  con su aporte** y una línea de qué mejora cada uno.
- **Backoffice:** vista de auditoría con el modelo, los pesos y el histórico.

## Restricciones aplicables

`R-REP-02` · `R-REP-03` · `R-AUD-01`

## Evidencia que deja

[[puntaje_reputacion]] · [[componente_score]] · [[snapshot_reputacion]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un usuario con eventos positivos y negativos
Cuando se recalcula
Entonces existe un único puntaje vigente
Y la suma de los aportes de los componentes es igual al total

Dado un usuario sin historial
Cuando se recalcula
Entonces el nivel es SIN_HISTORIAL y no cero

Dado un cambio de versión del modelo
Cuando se recalcula
Entonces el puntaje nuevo referencia el modelo nuevo
Y el anterior conserva su modelo_id original
```

## Ver también

[[CU-70 Registrar un evento de reputación]] · [[CU-72 Sellar el bloque de transparencia]] · [[CU-74 Otorgar y revocar una insignia]] · [[CU-75 Emitir un certificado de reputación verificable]] · [[CU-76 Reseñar a un participante y moderar la reseña]]
