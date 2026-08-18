---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-53
criticidad: media
actores: [Cliente, Legal, Supervisor]
normas: [ASFI — central de reclamos / segunda instancia]
---

# CU-53 — Elevar un reclamo a segunda instancia

> **Objetivo.** Que el cliente en desacuerdo con la respuesta pueda acudir a la
> instancia superior, y que la entidad pueda responder con el expediente completo.

## Actores y disparador

- **Actor principal:** cliente disconforme.
- **Actores secundarios:** área legal; supervisor o defensoría.
- **Disparador:** el cliente eleva el caso, o el supervisor requiere antecedentes.

## Precondiciones

1. Existe [[reclamo_cliente]] respondido (o vencido sin respuesta).

## Flujo principal

1. Se crea [[instancia_reclamo]] con `instancia` (`DEFENSORIA`, `REGULADOR`,
   `ARBITRAJE`, `JUDICIAL`), `fecha_elevacion` y `numero_expediente` cuando el
   organismo lo asigna.
2. Se arma el expediente: reclamo, respuesta, evidencia técnica
   ([[bitacora_evento]], [[movimiento_billetera]], [[cotizacion_comision]]) y
   antecedentes del cliente.
3. Se responde al requerimiento dentro del plazo fijado por el organismo; si llega
   como oficio, se tramita también por [[CU-45 Atender un requerimiento de autoridad]].
4. Recibida la resolución, se registra `resolucion`, `fecha_resolucion` y
   `monto_resarcido`.
5. Si la resolución es favorable al cliente, se ejecuta la reparación y se
   actualiza `reclamo_cliente.resultado`.
6. El caso alimenta indicadores: tasa de reclamos elevados y de resoluciones en
   contra, que se revisan en comité ([[acta_comite]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 4a | Resolución en contra de la entidad con multa | Se registra [[observacion_regulatoria]] tipo `MULTA` y se abre [[plan_accion_riesgo]] |
| 2a | Falta evidencia técnica | Es un hallazgo en sí mismo: significa que el flujo no dejó rastro suficiente |
| — | Varios reclamos por la misma causa | Se agrupan y se trata como falla sistémica ([[CU-54 Registrar un evento de riesgo operativo]]) |
| 1a | El cliente eleva fuera del plazo de la norma | Se registra igual y se responde: el plazo vencido lo evalúa el supervisor, no lo filtramos nosotros |
| 3a | El supervisor pide información adicional con plazo | El plazo se guarda al recibirlo y se controla como cualquier otro vencimiento regulatorio |

## Postcondiciones

- Cada elevación tiene expediente, resolución y, si aplica, resarcimiento.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU53 = z.object({
  reclamoId: z.string().uuid(),
  instancia: z.enum(['DEFENSORIA','REGULADOR','ARBITRAJE','JUDICIAL']),
  numeroExpediente: z.string().max(60).optional(),
}).strict()

export const SalidaCU53 = z.object({
  instanciaId: z.string().uuid(),
  estado: z.enum(['PRESENTADA','EN_TRAMITE','RESUELTA','DESISTIDA']),
  expedienteUrl: z.string().url(),
  montoResarcido: MontoSchema.nullable(),
}).strict()

export const ErroresCU53 = {
  RECLAMO_NO_RESPONDIDO: 'AP-CU53-01',
  INSTANCIA_DUPLICADA: 'AP-CU53-02',
  SIN_EVIDENCIA_TECNICA: 'AP-CU53-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `RECLAMO_NO_RESPONDIDO` | Todavía está en plazo de primera instancia |
| `INSTANCIA_DUPLICADA` | Ya hay una elevación abierta en esa instancia |
| `SIN_EVIDENCIA_TECNICA` | No hay rastro suficiente: es un hallazgo en sí mismo |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `armarExpediente` | Reúne reclamo, respuesta y evidencia técnica; puro |
| Molécula | `InstanciaReclamoRepositorio` | Elevación y resolución |
| Molécula | `EvidenciaRepositorio` | Bitácora, movimientos y cotizaciones del caso |
| Organismo | `CU53ElevarReclamo` | Transacción: elevación, expediente y resarcimiento |
| Página | `POST /reclamos/:id/instancias` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `reclamo.elevado` | Armado del expediente y plazo del organismo | `RECLAMO_ATENDER` |
| `instancia.resuelta` | Resarcimiento y actualización de indicadores | — |

## Interfaz

- **App:** El cliente ve que su reclamo fue elevado y a dónde.
- **Backoffice:** Seguimiento de instancias con resolución y monto resarcido.

## Restricciones aplicables

`R-CON-04` · `R-CON-05` · `R-AUD-08`

## Evidencia que deja

[[instancia_reclamo]] · [[reclamo_cliente]] · [[observacion_regulatoria]] ·
[[plan_accion_riesgo]]

## Criterios de aceptación

```gherkin
Dado un reclamo respondido desfavorablemente
Cuando el cliente lo eleva al supervisor
Entonces existe una instancia_reclamo con fecha_elevacion

Dada una resolución favorable al cliente con resarcimiento
Cuando se registra
Entonces existe la transacción o devolución que materializa el monto_resarcido

Dado un reclamo elevado fuera del plazo de la norma
Cuando se registra la elevación
Entonces la instancia queda abierta con el plazo vencido marcado
Y no se rechaza en la plataforma

Dada una instancia con pedido de información del supervisor
Cuando se recibe el pedido
Entonces su fecha límite queda guardada y aparece en el tablero de vencimientos
```

## Ver también

[[CU-52 Atender un reclamo en plazo]] · [[CU-45 Atender un requerimiento de autoridad]]
