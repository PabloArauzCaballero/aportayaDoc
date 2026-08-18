---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-52
criticidad: alta
actores: [Cliente, Punto de Reclamo, Responsable]
normas: [ASFI RNSF Libro 4 Título I — 5 días hábiles, prórroga a 10, conservación 10 años]
---

# CU-52 — Atender un reclamo en plazo

> **Objetivo.** Que todo reclamo tenga número único, plazo guardado, respuesta
> comprensible y —si el cliente tiene razón— reparación efectiva. Los plazos son
> literales y su incumplimiento es observable por el supervisor.

## Actores y disparador

- **Actor principal:** cliente.
- **Actores secundarios:** [[punto_reclamo]] (app, web, teléfono, presencial,
  correo); responsable asignado.
- **Disparador:** ingreso del reclamo por cualquier canal.

## Precondiciones

1. Existe al menos un punto de reclamo activo.

## Flujo principal

1. Se crea [[reclamo_cliente]] con **`codigo` único y correlativo**, `categoria`,
   `producto`, `monto_reclamado`, `canal_ingreso` y `fecha_ingreso`.
2. **Se calcula y se guarda** `plazo_respuesta` = 5 días hábiles administrativos
   desde el ingreso, junto con `dias_habiles_plazo` (`R-CON-01`).
3. Se comunica al cliente su número de reclamo y el plazo.
4. Se asigna `responsable_id` y se investiga; si hay ticket técnico, se enlaza
   `ticket_soporte_id`.
5. Se responde antes del plazo: `fecha_respuesta`, `respuesta` y `resultado`
   (`FAVORABLE`, `DESFAVORABLE`, `PARCIAL`, `DESISTIDO`).
6. Si el resultado es favorable y hay monto reclamado, **la reparación es
   obligatoria** antes de cerrar: [[CU-33 Devolver comisión y emitir nota de crédito]]
   o transacción de resarcimiento (`R-CON-04`).
7. Se fija `conservar_hasta` = 10 años y se marca `incluido_en_reporte_mensual`
   para el envío periódico al supervisor.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 5a | Requiere más análisis | Dentro de los 5 días se comunica al cliente la fecha de respuesta: `plazo_prorrogado_hasta` (máximo 10 días) y `prorroga_comunicada_al_cliente_en` (`R-CON-02`) |
| 5b | Se necesita más de 10 días | Además se comunica por escrito al supervisor y al cliente con justificación: `prorroga_comunicada_al_organismo_en` + `justificacion_prorroga` (`R-CON-03`) |
| 5c | Vence sin respuesta | Aparece en el tablero de vencidos y escala a [[hallazgo_auditoria]] |
| — | El cliente no queda conforme | [[CU-53 Elevar un reclamo a segunda instancia]] |
| 4a | El reclamo revela una falla sistémica | Se abre [[evento_riesgo_operativo]] y se evalúa comunicación proactiva a otros afectados |

## Postcondiciones

- Todo reclamo tiene número, plazo guardado, respuesta y —si corresponde—
  reparación trazable.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU52 = z.object({
  usuarioId: z.string().uuid(),
  puntoReclamoCodigo: z.string(),
  categoria: z.enum(['COMISION','OPERACION_NO_RECONOCIDA','SALDO','SERVICIO','DATOS_PERSONALES','GRUPO']),
  montoReclamado: MontoSchema.optional(),
  descripcion: z.string().min(20).max(4000),
  canalIngreso: z.enum(['APP','WEB','TELEFONO','PRESENCIAL','CORREO']),
}).strict()

export const SalidaCU52 = z.object({
  reclamoId: z.string().uuid(),
  codigo: z.string(),
  plazoRespuesta: z.string().datetime(),
  diasHabilesPlazo: z.number().int(),
  estado: z.enum(['INGRESADO','EN_ANALISIS','RESPONDIDO','CERRADO','ELEVADO']),
}).strict()

export const ErroresCU52 = {
  PUNTO_RECLAMO_INACTIVO: 'AP-CU52-01',
  PRORROGA_EXCEDIDA: 'AP-CU52-02',
  FALTA_REPARACION: 'AP-CU52-03',
  PLAZO_VENCIDO: 'AP-CU52-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `PUNTO_RECLAMO_INACTIVO` | El canal elegido no está habilitado |
| `PRORROGA_EXCEDIDA` | La prórroga supera el máximo sin comunicación al organismo (R-CON-03) |
| `FALTA_REPARACION` | Reclamo favorable con monto exige devolución asociada (R-CON-04) |
| `PLAZO_VENCIDO` | Se responde igual y escala a hallazgo |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularPlazoHabil` | Cinco días hábiles administrativos desde el ingreso; puro |
| Átomo | `evaluarProrroga` | Verifica límite y comunicación exigida |
| Molécula | `ReclamoRepositorio` | Alta, prórroga y respuesta |
| Molécula | `DevolucionRepositorio` | Reparación cuando el resultado es favorable |
| Organismo | `CU52AtenderReclamo` | Transacción: respuesta, reparación y cierre |
| Página | `POST /reclamos` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `reclamo.ingresado` | Código al cliente y reloj del plazo | Ninguno: canal público |
| `reclamo.respondido` | Reparación si corresponde y aviso | `RECLAMO_ATENDER` |
| `reclamo.vencido` | Hallazgo de auditoría | — |

## Interfaz

- **App:** *Ayuda → Reclamo*: código, plazo y estado siempre a la vista.
- **Backoffice:** Bandeja con vencimientos primero; sin reparación no se cierra un favorable.

## Restricciones aplicables

`R-CON-01` · `R-CON-02` · `R-CON-03` · `R-CON-04` · `R-CON-05` · `R-AUD-08`

## Evidencia que deja

[[reclamo_cliente]] · [[instancia_reclamo]] (si se eleva) ·
[[devolucion_comision]] o [[transaccion_billetera]] · [[reporte_regulatorio]]

## Criterios de aceptación

```gherkin
Dado un reclamo ingresado un lunes
Cuando se registra
Entonces plazo_respuesta queda guardado a 5 días hábiles y no se recalcula luego

Dado un reclamo que requiere prórroga
Cuando se comunica al cliente dentro de los 5 días
Entonces plazo_prorrogado_hasta no excede 10 días hábiles

Dado un reclamo con resultado FAVORABLE y monto reclamado de Bs 18
Cuando se intenta cerrar sin devolución asociada
Entonces el cierre se rechaza
```

## Ver también

[[CU-15 Emitir extracto y certificado de saldo]] · [[CU-19 Reembolsar un pago y atender una disputa]] · [[CU-27 Restringir al deudor e incluirlo en la lista interna]] · [[CU-33 Devolver comisión y emitir nota de crédito]] · [[CU-53 Elevar un reclamo a segunda instancia]] · [[CU-59 Mantener el calendario de días no hábiles]] · [[CU-76 Reseñar a un participante y moderar la reseña]] · [[CU-82 Procesar una respuesta entrante]] · [[Cumplimiento]]
