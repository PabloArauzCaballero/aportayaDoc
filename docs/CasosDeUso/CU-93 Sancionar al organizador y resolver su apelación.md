---
tags:
  - caso-uso
  - modulo/07-organizador-y-automatizacion
codigo: CU-93
criticidad: alta
actores: [Operaciones, Comité de gobierno, Organizador]
normas: [Debido proceso, ASFI Consumidor Financiero, gobernanza]
---

# CU-93 — Sancionar al organizador y resolver su apelación

> **Objetivo.** Que sancionar a quien administra grupos siga un procedimiento con
> causal, descargo, decisión motivada y apelación — y que la sanción nunca perjudique
> a los participantes de sus grupos.

## Actores y disparador

- **Actor principal:** operaciones, con el comité como segunda instancia.
- **Disparadores:** [[evaluacion_desempeno]] con acción sugerida de sanción;
  incumplimiento del [[contrato_organizador]]; reclamo procedente contra el
  organizador; fraude detectado.

## Precondiciones

1. Existe causal escrita: métrica bajo el mínimo por N períodos, obligación
   contractual incumplida, o hecho grave con evidencia.
2. Existe [[politica_sancion]] vigente con `plazo_descargo_dias`,
   `plazo_apelacion_dias` y `prescribe_en_dias`.
3. Los grupos que administra tienen destino previsto si la sanción implica apartarlo.

## Flujo principal

1. Se crea [[sancion_organizador]] con `organizador_id`, `evaluacion_id` cuando
   nace de una evaluación, `tipo` —`AMONESTACION`, `LIMITACION_DE_NIVEL`,
   `SUSPENSION`, `RESCISION`—, `motivo`, `vigente_desde`, `aplicada_por` y estado
   `PROPUESTA`.
2. Se notifica al organizador con la causal, la evidencia y **el plazo para
   descargo, calculado y guardado**. Sin descargo no hay sanción firme, salvo el
   caso de fraude, donde la suspensión es cautelar e inmediata y el descargo viene
   después.
3. Vencido el plazo se decide con fundamento escrito. Firme, la sanción pasa a
   `VIGENTE` con su `vigente_hasta` cuando es temporal.
4. **Los grupos no se sancionan.** Según el tipo:
   - `AMONESTACION` → queda en el historial y pesa en la próxima evaluación;
   - `LIMITACION_DE_NIVEL` → no toma grupos nuevos por encima de cierto monto;
   - `SUSPENSION` → no crea grupos; **los vigentes los sigue administrando** salvo
     riesgo, en cuyo caso se reasignan;
   - `RESCISION` → se encadena [[CU-91 Firmar y rescindir el contrato de organizador]]
     con su transición ordenada.
5. **Apelación.** El organizador presenta [[apelacion_sancion_org]] con `argumento`
   y `evidencias`, **una sola por sanción**. La resuelve el
   [[comite_gobierno]] ([[CU-94 Elevar una decisión al comité de gobierno]]), no
   quien la aplicó (`R-SEG-04`).
6. Si la apelación prospera, la sanción pasa a `REVOCADA`: se restituye el nivel, se
   compensa la reputación y, si hubo perjuicio económico medible, se resarce. **El
   registro de que existió no se borra**, pero queda con su revocación al lado.
7. Toda la secuencia se refleja en el historial del organizador y se conserva por el
   plazo de retención.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Fraude con evidencia | Suspensión cautelar inmediata y reasignación de grupos; el descargo corre igual y puede revertirla |
| 3a | El organizador no responde | La sanción queda firme por vencimiento, con constancia del aviso entregado |
| 4a | Suspensión de quien administra muchos grupos | Se prioriza la continuidad: los grupos siguen con él o se reasignan con plan, nunca quedan sin administración |
| 5a | Presenta una segunda apelación | Se rechaza: una apelación por sanción (`R-ORG-05`); lo que queda es la vía externa |
| 5b | El comité no alcanza quórum en el plazo | La apelación se resuelve a favor del apelante por vencimiento: la demora del órgano no la paga la persona |
| 6a | Se revoca una sanción que ya causó pérdida de ingresos | Se cuantifica y se resarce; el caso entra como [[evento_riesgo_operativo]] |
| — | Sanción prescrita | Cumplido `prescribe_en_dias` sin resolución, se cierra como `PRESCRITA` y no se puede reabrir por el mismo hecho |
| — | El organizador renuncia durante el proceso | El procedimiento continúa: renunciar no borra el antecedente para una postulación futura |

## Postcondiciones

- Ninguna sanción firme carece de causal, descargo y decisión motivada.
- Ningún grupo pierde administración por una sanción a su organizador.

## Contrato · `openapi/organizador.yaml`

```ts
export const EntradaCU93 = z.object({
  organizadorId: z.string().uuid(),
  evaluacionId:  z.string().uuid().nullable(),
  tipo: z.enum(['AMONESTACION','LIMITACION_DE_NIVEL','SUSPENSION','RESCISION']),
  motivo: z.string().min(30).max(300),
  cautelar: z.boolean().default(false),
  vigenteHasta: z.string().datetime().nullable(),
}).strict()

export const EntradaApelacionCU93 = z.object({
  sancionId: z.string().uuid(),
  argumento: z.string().min(50).max(4000),
  evidencias: z.array(z.object({
    descripcion: z.string().max(300), urlArchivo: z.string().url().nullable(),
  })).max(10),
}).strict()

export const SalidaCU93 = z.object({
  sancionId: z.string().uuid(),
  estado: z.enum(['PROPUESTA','VIGENTE','REVOCADA','CUMPLIDA','PRESCRITA']),
  fechaLimiteDescargo: z.string().datetime(),
  fechaLimiteApelacion: z.string().datetime().nullable(),
  gruposReasignados: z.array(z.string().uuid()),
  apelacionId: z.string().uuid().nullable(),
}).strict()

export const ErroresCU93 = {
  SIN_CAUSAL:            'AP-CU93-01',
  PLAZO_DESCARGO_ABIERTO:'AP-CU93-02',
  APELACION_DUPLICADA:   'AP-CU93-03',
  MISMO_RESOLUTOR:       'AP-CU93-04',
  GRUPOS_SIN_DESTINO:    'AP-CU93-05',
  SANCION_PRESCRITA:     'AP-CU93-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_CAUSAL` | No hay evaluación, incumplimiento contractual ni hecho con evidencia |
| `PLAZO_DESCARGO_ABIERTO` | Se intenta hacer firme la sanción antes de vencer el plazo |
| `APELACION_DUPLICADA` | Ya presentó apelación para esa sanción (`R-ORG-05`) |
| `MISMO_RESOLUTOR` | Quien resuelve la apelación es quien aplicó la sanción (`R-SEG-04`) |
| `GRUPOS_SIN_DESTINO` | La suspensión con reasignación no tiene a quién reasignar |
| `SANCION_PRESCRITA` | Venció `prescribe_en_dias` sin resolución |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `efectosDeSancion(tipo)` | Qué habilita y qué bloquea cada tipo; puro |
| Átomo | `plazosDeSancion(politica, desde, calendario)` | Descargo, apelación y prescripción; puro |
| Molécula | `SancionOrganizadorRepositorio` · `ApelacionRepositorio` | Persistencia y unicidad de apelación |
| Molécula | `ReasignadorDeGrupos` | Compartido con [[CU-91 Firmar y rescindir el contrato de organizador]] |
| Organismo | `CU93AplicarSancion` · `CU93ResolverApelacion` | Transacción: estado, efectos, reasignación y evento |
| Página | `POST /organizadores/:id/sanciones` · `POST /sanciones-org/:id/apelacion` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `sancion_organizador.propuesta` | Notificación con causal y plazo de descargo | `ORGANIZADORES_SANCIONAR` |
| `sancion_organizador.firme` | Aplicación de efectos y reasignación si corresponde | — |
| `sancion_organizador.revocada` | Restitución, compensación de reputación y resarcimiento | Quórum del comité |
| — | Trabajo que vence descargos, apelaciones y prescribe sanciones | — |

## Interfaz

- **App:** el organizador ve la sanción con su causal, la evidencia, el plazo y el
  formulario de descargo y de apelación. Los participantes de sus grupos **no ven la
  sanción**; ven, si corresponde, el cambio de administrador.
- **Backoffice:** expediente de sanción con línea de tiempo, plazos en semáforo y la
  bandeja del comité.

## Restricciones aplicables

`R-SEG-04` · `R-ORG-05` · `R-CON-01` · `R-AUD-01` · `R-AUD-04` · `R-RIS-01`

## Evidencia que deja

[[sancion_organizador]] · [[apelacion_sancion_org]] · [[evaluacion_desempeno]] ·
[[contrato_organizador]] · [[acta_comite]] · [[evento_riesgo_operativo]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dada una evaluación con acción sugerida de sanción
Cuando operaciones propone una suspensión
Entonces la sanción queda PROPUESTA con fecha_limite_descargo guardada

Dada una sanción propuesta con el plazo de descargo abierto
Cuando se intenta hacerla firme
Entonces se rechaza con PLAZO_DESCARGO_ABIERTO

Dada una suspensión firme de un organizador con grupos activos
Cuando se aplica
Entonces ningún grupo queda sin administrador

Dada una apelación que el comité no resuelve dentro del plazo
Cuando vence
Entonces la sanción queda REVOCADA a favor del apelante
```

## Ver también

[[CU-91 Firmar y rescindir el contrato de organizador]] · [[CU-92 Evaluar el desempeño del organizador]] · [[CU-94 Elevar una decisión al comité de gobierno]] · [[CU-54 Registrar un evento de riesgo operativo]]
