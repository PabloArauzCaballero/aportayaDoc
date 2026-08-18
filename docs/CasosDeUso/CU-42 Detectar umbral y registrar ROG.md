---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-42
criticidad: alta
actores: [Sistema, Funcionario responsable]
normas: [UIF — Instructivo EIF, art. 53 (modificado por R.A. UIF/050/2026)]
---

# CU-42 — Detectar umbral y registrar el reporte de operaciones generales (ROG)

> **Objetivo.** Registrar las operaciones que se reportan **por su naturaleza o su
> monto**, sin que medie sospecha, y distinguirlas nítidamente del reporte de
> operación sospechosa.

## Actores y disparador

- **Actor principal:** el motor de umbrales.
- **Disparadores:** retiros en efectivo en moneda extranjera; operaciones
  electrónicas; giros y remesas por orden electrónica; **transferencias desde
  billetera móvil**; operaciones con activos virtuales.

## Precondiciones

1. [[umbral_reporte_uif]] tiene las filas de `formulario` `ROG-01`..`ROG-04`
   vigentes.

## Flujo principal

1. Aplicada la operación, el motor evalúa los umbrales por `concepto_operacion`:
   - **ROG-01 / ROG-02** — retiros en efectivo en moneda extranjera: se reportan
     **todos**, sin umbral;
   - **ROG-03** — operación electrónica individual sobre umbral; acumulación de
     operaciones menores dentro de la ventana; giros y remesas por orden
     electrónica; **transferencias desde billetera móvil acumuladas**;
   - **ROG-04** — operaciones con activos virtuales, según catálogo.
2. Se aplica la misma mecánica de ventana y reinicio de
   [[CU-41 Detectar umbral y registrar formulario PCC-01]].
3. Se crea [[registro_operacion_relevante]] con el `formulario` correspondiente y
   `periodo_remision`.
4. El registro entra al envío mensual ([[CU-43 Remitir los reportes mensuales a la UIF]]).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El producto no ofrece activos virtuales | El catálogo ROG-04 queda inactivo (`activo=false`), no se elimina la estructura |
| 2a | La operación además dispara PCC-01 | Se generan **dos** registros: uno por formulario. No se fusionan |
| — | La operación se reversa | El registro no se borra: se agrega el reverso como operación propia y se documenta |
| 3a | Cambia el catálogo de la autoridad | Fila nueva con vigencia; los registros históricos mantienen su referencia |

## Postcondiciones

- Cada obligación de reporte tiene su propio registro, clasificado por formulario.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU42 = z.object({
  transaccionId: z.string().uuid(),
}).strict()

export const SalidaCU42 = z.object({
  registros: z.array(z.object({ registroId: z.string().uuid(), formulario: z.enum(['ROG-01','ROG-02','ROG-03','ROG-04']) })),
  periodoRemision: z.string(),
}).strict()

export const ErroresCU42 = {
  SIN_TIPO_DE_CAMBIO: 'AP-CU42-01',
  REGISTRO_DUPLICADO: 'AP-CU42-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_TIPO_DE_CAMBIO` | Falta cotización para convertir a dólares |
| `REGISTRO_DUPLICADO` | Ya existe el registro para esa transacción y umbral |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `clasificarConceptoRog` | Traduce el tipo de transacción al concepto del artículo 53; puro |
| Molécula | `UmbralUifRepositorio` | Umbrales ROG vigentes |
| Molécula | `OperacionRelevanteRepositorio` | Alta del registro |
| Organismo | `CU42RegistrarRog` | Se ejecuta en la transacción de la operación |
| Página | — | Sin endpoint: lo dispara el planificador o un evento |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `uif.operacion_general` | Inclusión en el reporte mensual | Interno |

## Interfaz

- **App:** Sin pantalla: el usuario no ve estos registros.
- **Backoffice:** Consulta por período y formulario, con exportación.

## Restricciones aplicables

`R-UIF-01` · `R-UIF-02` · `R-UIF-03` · `R-UIF-04` · `R-UIF-05` · `R-AUD-01`

## Evidencia que deja

[[registro_operacion_relevante]] · [[umbral_reporte_uif]] ·
[[transaccion_billetera]] · [[orden_retiro]]

## Criterios de aceptación

```gherkin
Dado un retiro en efectivo en moneda extranjera
Cuando se ejecuta
Entonces existe un registro_operacion_relevante con formulario ROG-01 sin importar el monto

Dadas transferencias desde billetera que acumulan USD 1.000 en 3 días
Cuando ocurre la que alcanza el umbral
Entonces existe un registro con formulario ROG-03 y es_acumulada = true

Dada una operación que dispara PCC-01 y ROG-03
Cuando se procesa
Entonces existen dos registros distintos
```

## Ver también

[[CU-12 Transferir saldo entre billeteras]] · [[CU-41 Detectar umbral y registrar formulario PCC-01]] · [[CU-43 Remitir los reportes mensuales a la UIF]]
