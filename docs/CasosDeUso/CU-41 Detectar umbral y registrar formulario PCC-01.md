---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-41
criticidad: alta
actores: [Sistema, Usuario, Funcionario responsable]
normas: [UIF — Instructivo EIF, art. 52 (modificado por R.A. UIF/050/2026)]
---

# CU-41 — Detectar umbral y registrar el formulario PCC-01

> **Objetivo.** Detectar automáticamente cuándo una operación —o una acumulación de
> operaciones— obliga a pedir la declaración de origen y destino de fondos, y
> registrarla de forma reproducible.

## Actores y disparador

- **Actor principal:** el motor de umbrales, en línea.
- **Actores secundarios:** usuario (declara), funcionario responsable (remite).
- **Disparadores:** operación en efectivo, cambio de moneda, giro, remesa y —lo que
  aplica de lleno a este producto— **carga y retiro de billetera móvil**.

## Precondiciones

1. [[umbral_reporte_uif]] está sembrado con las filas vigentes (`formulario='PCC-01'`,
   por inciso), cada una con `base_normativa`.
2. Existe tipo de cambio del día para convertir a dólares.

## Flujo principal

1. Al aplicarse una operación, el motor busca los umbrales activos cuyo
   `concepto_operacion` coincide.
2. **Caso individual** (`es_acumulado=false`): si
   `monto_equivalente_usd >= umbral_usd`, corresponde el formulario.
3. **Caso acumulado** (`es_acumulado=true`):
   - se determina la ventana: desde la operación siguiente a la última que superó
     el umbral, hacia adelante, con máximo `ventana_dias_calendario`;
   - se suman los equivalentes en dólares de las operaciones de esa ventana;
   - si el acumulado alcanza el umbral, corresponde el formulario **por la última
     operación** que lo alcanza.
4. Se crea [[registro_operacion_relevante]] con: `umbral_reporte_id`, `formulario`,
   `es_acumulada`, `ventana_desde`/`ventana_hasta`,
   `operacion_inicio_ventana_id`, `monto_acumulado_ventana`,
   `tipo_cambio_aplicado`, `monto_equivalente_usd`, `umbral_aplicado_usd` y
   `periodo_remision`.
5. Se solicita al usuario la declaración: `origen_declarado` y `destino_declarado`
   (solo de la operación que alcanza el umbral). Si hay documento, se enlaza
   [[declaracion_origen_fondos]].
6. Se marca la operación como inicio de la ventana siguiente.
7. El registro entra al envío mensual ([[CU-43 Remitir los reportes mensuales a la UIF]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 5a | Imposibilidad operativa o técnica de obtener el formulario | Se registra `exento=false` con la justificación y se solicita igualmente origen y destino; el caso se documenta como anexo del manual ([[politica_interna]]) |
| — | Operación exenta (operativa propia entre entidades reguladas, pago con tarjeta, bonos sociales, servicios básicos, impuestos, tasas y regalías) | `exento=true` + `motivo_exencion`; **queda registrada igual**, no se omite |
| 3a | Cambia el umbral por norma nueva | Se cierra la vigencia de la fila anterior y se crea una nueva; las operaciones pasadas conservan `umbral_aplicado_usd` |
| 5b | El usuario se niega a declarar | Se registra la negativa; es un factor de riesgo que alimenta [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]] |

## Postcondiciones

- Toda operación que alcanzó umbral tiene su registro, con la ventana y el tipo de
  cambio que se usaron.
- El cálculo es reproducible dos años después sin recalcular nada.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU41 = z.object({
  transaccionId: z.string().uuid(),
}).strict()

export const SalidaCU41 = z.object({
  registros: z.array(z.object({ registroId: z.string().uuid(), formulario: z.string(), esAcumulada: z.boolean(), montoAcumuladoUsd: MontoSchema })),
  requiereDeclaracion: z.boolean(),
  periodoRemision: z.string(),
}).strict()

export const ErroresCU41 = {
  SIN_TIPO_DE_CAMBIO: 'AP-CU41-01',
  TITULAR_NO_IDENTIFICADO: 'AP-CU41-02',
  REGISTRO_DUPLICADO: 'AP-CU41-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_TIPO_DE_CAMBIO` | No hay cotización del día: el umbral no sería reproducible (R-UIF-04) |
| `TITULAR_NO_IDENTIFICADO` | La operación es de operativa propia y queda exenta |
| `REGISTRO_DUPLICADO` | Esa transacción ya registró ese umbral (R-UIF-13) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `acumularEnVentana` | Suma desde el reinicio de la ventana; puro y reproducible |
| Átomo | `convertirAUsd` | Aplica el tipo de cambio del día y lo devuelve para guardarlo |
| Molécula | `UmbralUifRepositorio` | Umbrales vigentes por concepto |
| Molécula | `OperacionRelevanteRepositorio` | Alta del registro con su ventana |
| Molécula | `TipoCambioRepositorio` | Cotización vigente a la fecha |
| Organismo | `CU41RegistrarPcc01` | Se ejecuta en la transacción de la operación |
| Página | — | Sin endpoint: lo dispara el planificador o un evento |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `uif.umbral_alcanzado` | Solicitud de declaración de origen y destino | Interno |
| `uif.declaracion_recibida` | Cierre del registro para el envío mensual | `PARTICIPANTE` |

## Interfaz

- **App:** Pide origen y destino solo cuando corresponde, explicando por qué.
- **Backoffice:** Bandeja de formularios del período con su estado.

## Restricciones aplicables

`R-UIF-01` · `R-UIF-02` · `R-UIF-03` · `R-UIF-04` · `R-UIF-05` · `R-AUD-01`

## Evidencia que deja

[[registro_operacion_relevante]] · [[declaracion_origen_fondos]] ·
[[umbral_reporte_uif]] · [[transaccion_billetera]]

## Criterios de aceptación

```gherkin
Dado el umbral de carga de billetera acumulada de USD 1.000 en 3 días
Y un usuario que carga USD 400, USD 300 y USD 350 en tres días consecutivos
Cuando se acredita la tercera carga
Entonces existe un registro_operacion_relevante con formulario PCC-01
Y monto_acumulado_ventana es USD 1.050
Y se declara origen y destino solo de la tercera operación

Dado que ya se alcanzó el umbral
Cuando ocurre la operación siguiente
Entonces esa operación inicia una ventana nueva

Dado un pago de servicios básicos
Cuando alcanza el umbral
Entonces el registro queda con exento = true y su motivo
```

## Ver también

[[CU-42 Detectar umbral y registrar ROG]] · [[CU-43 Remitir los reportes mensuales a la UIF]] · [[Cumplimiento]]
