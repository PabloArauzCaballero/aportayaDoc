---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-56
criticidad: media
actores: [TI, Riesgos, Comité]
normas: [ASFI RNSF Libro 3 Título V (continuidad probada y documentada), ISO 22301]
---

# CU-56 — Ejecutar una prueba de continuidad

> **Objetivo.** Demostrar que los planes funcionan. La norma no pide tener un plan:
> pide **probarlo y documentar el resultado en acta**. Un plan sin pruebas
> registradas es un hallazgo garantizado.

## Actores y disparador

- **Actores:** TI (ejecuta), riesgos (evalúa), comité (aprueba y toma conocimiento).
- **Disparadores:** `plan_continuidad.proxima_prueba <= hoy`; cambio mayor de
  arquitectura; hallazgo previo.

## Precondiciones

1. Existe [[plan_continuidad]] por proceso crítico con `rto_minutos` y
   `rpo_minutos` comprometidos, aprobado en [[politica_interna]].

## Flujo principal

1. Se planifica la prueba: `tipo` (`ESCRITORIO`, `PARCIAL`, `TOTAL`,
   `CONMUTACION_REAL`), fecha y alcance.
2. Se ejecuta y se miden `rto_obtenido_minutos` y `rpo_obtenido_minutos` reales.
3. Se crea [[prueba_continuidad]] con `resultado` (`EXITOSA`, `PARCIAL`,
   `FALLIDA`), `hallazgos` y `evidencia_url`.
4. Se reporta al comité y se enlaza el [[acta_comite]] correspondiente.
5. Se actualiza `plan_continuidad.proxima_prueba`.
6. Todo hallazgo genera [[plan_accion_riesgo]] con responsable y fecha.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | RTO obtenido supera el comprometido | `resultado='PARCIAL'` o `'FALLIDA'`; plan de acción obligatorio |
| 3a | La prueba no se ejecuta en la fecha programada | El plan figura vencido; escala a [[hallazgo_auditoria]] |
| 1a | Proceso crítico sin plan | Es en sí un hallazgo: todo proceso crítico debe tener plan |
| — | Conmutación real con impacto en clientes | Se registra además [[evento_riesgo_operativo]] si hubo pérdida |

## Postcondiciones

- Cada proceso crítico tiene evidencia fechada de su última prueba y su resultado.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU56 = z.object({
  planContinuidadId: z.string().uuid(),
  tipo: z.enum(['ESCRITORIO','PARCIAL','TOTAL','CONMUTACION_REAL']),
  fecha: z.string().date(),
  ejecutadaPor: z.string().uuid(),
}).strict()

export const SalidaCU56 = z.object({
  pruebaId: z.string().uuid(),
  rtoObtenido: z.number().int(),
  rpoObtenido: z.number().int(),
  resultado: z.enum(['EXITOSA','PARCIAL','FALLIDA']),
  planAccionId: z.string().uuid().nullable(),
}).strict()

export const ErroresCU56 = {
  SIN_ACTA_DE_COMITE: 'AP-CU56-01',
  OBJETIVOS_NO_ALCANZADOS: 'AP-CU56-02',
  PLAN_INEXISTENTE: 'AP-CU56-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_ACTA_DE_COMITE` | Una prueba exitosa exige acta que la reporte |
| `OBJETIVOS_NO_ALCANZADOS` | El RTO obtenido supera el comprometido |
| `PLAN_INEXISTENTE` | El proceso crítico no tiene plan: es un hallazgo |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `compararObjetivos` | RTO y RPO obtenidos contra comprometidos; puro |
| Molécula | `PruebaContinuidadRepositorio` | Resultado y evidencia |
| Molécula | `ActaComiteRepositorio` | Acta donde se reportó |
| Organismo | `CU56ProbarContinuidad` | Transacción: prueba, hallazgos y plan de acción |
| Página | `POST /continuidad/pruebas` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `continuidad.probada` | Reprogramación de la siguiente prueba | `RESPONSABLE_RIESGOS` |
| `continuidad.fallida` | Plan de acción obligatorio con responsable | — |

## Interfaz

- **App:** Sin pantalla en la app.
- **Backoffice:** Calendario de pruebas por proceso crítico, con su último resultado.

## Restricciones aplicables

`R-RIS-03` · `R-LIC-03` · `R-AUD-08`

## Evidencia que deja

[[plan_continuidad]] · [[prueba_continuidad]] · [[acta_comite]] ·
[[plan_accion_riesgo]]

## Criterios de aceptación

```gherkin
Dado un plan con RTO comprometido de 60 minutos
Cuando la prueba obtiene 95 minutos
Entonces el resultado no puede ser EXITOSA
Y existe un plan_accion_riesgo asociado

Dado un plan cuya proxima_prueba venció
Cuando corre el control diario
Entonces existe un hallazgo_auditoria abierto

Dada una prueba de conmutación real con impacto en clientes
Cuando se registra su resultado
Entonces existe además un evento_riesgo_operativo con la pérdida cuantificada

Dado un proceso crítico sin plan_continuidad
Cuando corre el control de cobertura
Entonces existe un hallazgo_auditoria que lo identifica por nombre
```

## Ver también

[[CU-54 Registrar un evento de riesgo operativo]] · [[CU-83 Enrutar el envío por proveedor de mensajería]] · [[Cumplimiento]]
