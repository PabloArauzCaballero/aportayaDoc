---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-02
criticidad: alta
actores: [Usuario, Analista de cumplimiento]
normas: [UIF EBR, límites BCB]
---

# CU-02 — Elevar nivel de debida diligencia

> **Objetivo.** Que el usuario pueda operar montos mayores entregando más
> información, y que ese aumento de capacidad sea **consecuencia automática** del
> nivel de conocimiento alcanzado, no de una decisión manual.

## Actores y disparador

- **Actor principal:** usuario que choca contra un límite.
- **Disparadores:** rechazo por límite en [[CU-40 Evaluar límites antes de una operación]];
  vencimiento de [[debida_diligencia]]; recalificación de riesgo por
  [[desvio_perfil]].

## Precondiciones

1. El usuario tiene [[cuenta_billetera]] activa y calificación de riesgo vigente.
2. Existen filas de [[limite_operativo_billetera]] para el nivel destino.

## Flujo principal

1. El sistema muestra qué falta: `debida_diligencia.documentos_requeridos` menos
   `documentos_recibidos` para el `tipo` destino (`ESTANDAR` o `AMPLIADA`).
2. El usuario carga los documentos; se registran en [[expediente_cliente]] y, si
   corresponde, en [[direccion_usuario]] y [[declaracion_origen_fondos]] con
   `documento_respaldo_url` y `hash_documento`.
3. Un analista revisa y aprueba (`aprobada_por`). En nivel `REFORZADA` se exige
   `segunda_revision_por` distinto de `aprobada_por` (**cuatro ojos**).
4. **En la misma transacción**: se cierra la vigencia de la
   [[calificacion_riesgo_cliente]] anterior y se inserta la nueva con
   `nivel_dd_requerido` actualizado y `motivo_cambio`.
5. Se actualiza `cuenta_billetera.nivel_debida_diligencia`. **Los límites no se
   copian**: se resuelven al evaluar, leyendo [[limite_operativo_billetera]] por
   nivel.
6. Se emite `evento_dominio` `NIVEL_DDD_ELEVADO` y se notifica al usuario los
   nuevos topes.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | Documentación insuficiente | `debida_diligencia.estado='OBSERVADA'` con `observaciones`; el nivel no cambia |
| 3b | El usuario es PEP | Se fuerza `tipo='REFORZADA'` y aprobación de nivel superior (`R-UIF-10`) |
| 4a | Existe [[coincidencia_lista]] pendiente | Se bloquea la elevación hasta resolverla |
| — | La debida diligencia vence (`vence_en < now()`) | La cuenta pasa a `LIMITADA`: puede recibir y retirar su saldo, no puede aumentar posición |

## Postcondiciones

- Una sola calificación vigente por usuario (`R-UIF-11`).
- El histórico de calificaciones y diligencias queda intacto y consultable.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU02 = z.object({
  claveIdempotencia: z.string().uuid(),
  usuarioId:         z.string().uuid(),
  nivelDestino:      z.enum(['ESTANDAR','AMPLIADA','REFORZADA']),
  documentos:        z.array(z.object({ tipo: z.string(), url: z.string().url(), hash: z.string().length(64) })),
}).strict()

export const SalidaCU02 = z.object({
  diligenciaId:      z.string().uuid(),
  estado:            z.enum(['EN_PROCESO','COMPLETA','OBSERVADA']),
  faltantes:         z.array(z.string()),
  limitesNuevos:     z.array(z.object({ concepto: z.string(), monto: MontoSchema })),
}).strict()

export const ErroresCU02 = {
  DOCUMENTACION_INSUFICIENTE: 'AP-CU02-01',
  REQUIERE_SEGUNDA_REVISION: 'AP-CU02-02',
  COINCIDENCIA_PENDIENTE: 'AP-CU02-03',
  NIVEL_NO_ASCENDENTE: 'AP-CU02-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `DOCUMENTACION_INSUFICIENTE` | Faltan documentos del nivel destino |
| `REQUIERE_SEGUNDA_REVISION` | Es PEP y falta la revisión independiente (R-UIF-10) |
| `COINCIDENCIA_PENDIENTE` | Hay coincidencia de lista sin resolver |
| `NIVEL_NO_ASCENDENTE` | El destino no es superior al actual |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `faltantesDeNivel` | Compara requeridos contra recibidos; puro |
| Átomo | `calcularProximaRevision` | Periodicidad según el nivel de riesgo |
| Molécula | `DebidaDiligenciaRepositorio` | Expediente y estado |
| Molécula | `CalificacionRiesgoRepositorio` | Cierra la vigencia anterior e inserta la nueva |
| Organismo | `CU02ElevarDiligencia` | Transacción: diligencia, calificación y nivel de la cuenta |
| Página | `POST /usuarios/:id/diligencia` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `diligencia.elevada` | Recalcula límites y notifica los topes nuevos | `PARTICIPANTE` o `ANALISTA_CUMPLIMIENTO` |
| `diligencia.observada` | Aviso con lo que falta | — |

## Interfaz

- **App:** *Aumentá tu límite*: muestra qué desbloquea cada nivel antes de pedir papeles.
- **Backoffice:** Bandeja de diligencias por aprobar, con doble revisión obligatoria para PEP.

## Restricciones aplicables

`R-UIF-09` · `R-UIF-10` · `R-UIF-11` · `R-LIM-01` · `R-LIM-03` · `R-SEG-04` · `R-AUD-08`

## Evidencia que deja

[[debida_diligencia]] · [[calificacion_riesgo_cliente]] (histórico) ·
[[factor_riesgo_evaluado]] · [[declaracion_origen_fondos]] · [[expediente_cliente]]

## Criterios de aceptación

```gherkin
Dado un usuario en nivel SIMPLIFICADA
Cuando completa la documentación de nivel ESTANDAR y un analista la aprueba
Entonces existe una única calificacion_riesgo_cliente vigente con nivel_dd_requerido ESTANDAR
Y la calificación anterior conserva su vigente_hasta

Dado un usuario marcado como PEP
Cuando un solo analista intenta aprobar su debida diligencia
Entonces la aprobación es rechazada por falta de segunda revisión (R-UIF-10)

Dado un usuario cuya debida_diligencia venció
Cuando intenta una recarga
Entonces la operación es rechazada y la cuenta figura LIMITADA
```

## Ver también

[[CU-01 Registro y apertura de billetera]] · [[CU-06 Revisión periódica de conocimiento del cliente]] · [[CU-40 Evaluar límites antes de una operación]] · [[Restricciones]]
