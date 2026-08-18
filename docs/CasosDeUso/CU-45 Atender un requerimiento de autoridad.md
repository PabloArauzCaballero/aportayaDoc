---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-45
criticidad: alta
actores: [Legal, Oficial de cumplimiento]
normas: [UIF, requerimientos judiciales y fiscales, secreto financiero]
---

# CU-45 — Atender un requerimiento de autoridad

> **Objetivo.** Responder oficios en plazo, entregando exactamente lo pedido —ni
> más ni menos— y dejando registro de qué se entregó, a quién y con qué respaldo.

## Actores y disparador

- **Actor principal:** área legal.
- **Actor secundario:** oficial de cumplimiento; áreas que aportan información.
- **Disparador:** recepción de un oficio de juzgado, fiscalía, unidad de
  inteligencia financiera, supervisor o administración tributaria.

## Precondiciones

1. El oficio está digitalizado y su autenticidad verificada.

## Flujo principal

1. Se crea [[requerimiento_autoridad]] con `numero_oficio` único,
   `fecha_recepcion`, **`plazo_respuesta` calculado y guardado**, `alcance`,
   `documento_url` y `hash_documento`.
2. Se identifica al `usuario_afectado_id` y se delimita el alcance exacto de lo
   solicitado (períodos, tipos de operación).
3. Se extrae la información. **Cada consulta a datos del afectado queda en
   [[registro_acceso_datos]]** con la justificación = número de oficio (`R-SEG-02`).
4. Si el oficio ordena inmovilizar fondos → [[CU-17 Bloquear saldo por orden de autoridad]].
5. Se responde por el canal indicado; se guardan `respuesta_url`,
   `respondido_en` y `respondido_por`.
6. Se conserva todo el expediente por el plazo legal.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El alcance es ambiguo o excesivo | Se pide aclaración por escrito y se registra; el plazo se gestiona con la autoridad |
| 5a | El plazo vence sin responder | Queda vencido y visible; escala a [[hallazgo_auditoria]] y potencialmente a [[observacion_regulatoria]] |
| 3a | Se solicita información de un no cliente | Se responde formalmente que no existe relación, sin exponer datos de terceros |
| — | El oficio impone reserva | No se notifica al titular; el sistema no dispara comunicación en este flujo |

## Postcondiciones

- Existe trazabilidad completa: qué pidieron, qué se entregó, quién lo consultó y
  cuándo se respondió.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU45 = z.object({
  autoridad: z.enum(['FISCALIA','UIF','ASFI','JUZGADO','SIN','POLICIA']),
  numeroOficio: z.string().max(60),
  plazoRespuesta: z.string().datetime(),
  usuarioAfectadoId: z.string().uuid().optional(),
  alcance: z.string().min(10).max(300),
  documentoUrl: z.string().url(),
  hashDocumento: z.string().length(64),
}).strict()

export const SalidaCU45 = z.object({
  requerimientoId: z.string().uuid(),
  bloqueoSaldoId: z.string().uuid().nullable(),
  accesosRegistrados: z.number().int(),
  respuestaUrl: z.string().url().nullable(),
}).strict()

export const ErroresCU45 = {
  OFICIO_DUPLICADO: 'AP-CU45-01',
  SIN_DOCUMENTO: 'AP-CU45-02',
  ALCANCE_AMBIGUO: 'AP-CU45-03',
  PLAZO_VENCIDO: 'AP-CU45-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `OFICIO_DUPLICADO` | Ese número ya fue registrado |
| `SIN_DOCUMENTO` | No se actúa sin el oficio y su hash |
| `ALCANCE_AMBIGUO` | Se pide aclaración antes de entregar información |
| `PLAZO_VENCIDO` | Se responde igual y se abre hallazgo |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `delimitarAlcance` | Traduce el pedido a un conjunto acotado de datos; puro |
| Molécula | `RequerimientoRepositorio` | Expediente del oficio |
| Molécula | `AccesoDatosRepositorio` | Registra cada consulta con su justificación |
| Organismo | `CU45AtenderRequerimiento` | Transacción: registro, extracción y bloqueo si lo ordena |
| Página | `POST /cumplimiento/requerimientos` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `requerimiento.recibido` | Plazo en el tablero y asignación a legal | `OFICIAL_CUMPLIMIENTO` |
| `requerimiento.respondido` | Archivo de la respuesta con su hash | — |

## Interfaz

- **App:** Sin pantalla; si hay bloqueo, el titular ve el número de oficio.
- **Backoffice:** Bandeja de oficios con plazo, alcance y respuesta archivada.

## Restricciones aplicables

`R-SEG-02` · `R-BIL-14` · `R-AUD-08` · `R-UIF-08`

## Evidencia que deja

[[requerimiento_autoridad]] · [[registro_acceso_datos]] · [[bloqueo_saldo]] (si
aplica) · [[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un oficio con plazo de 5 días
Cuando se registra
Entonces plazo_respuesta queda guardado y no se recalcula después

Dada la extracción de información para el oficio
Cuando un operador consulta los datos del afectado
Entonces existe un registro_acceso_datos con el número de oficio como justificación

Dado un oficio con plazo vencido sin respuesta
Cuando corre el control diario
Entonces existe un hallazgo_auditoria abierto
```

## Ver también

[[CU-17 Bloquear saldo por orden de autoridad]] · [[CU-43 Remitir los reportes mensuales a la UIF]] · [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]] · [[CU-53 Elevar un reclamo a segunda instancia]] · [[CU-58 Definir, programar y exportar un reporte]]
