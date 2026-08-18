---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-49
criticidad: alta
actores: [Directorio, Oficial de cumplimiento, Recursos humanos, ASFI, UIF]
normas: [UIF designación de oficial de cumplimiento, ASFI Res. 540/2025, capacitación anual obligatoria]
---

# CU-49 — Designar al oficial de cumplimiento y capacitar

> **Objetivo.** Que siempre haya una persona designada y comunicada al regulador
> como responsable del cumplimiento, con suplente, y que la capacitación anual de
> todo el personal sea un dato verificable y no una promesa.

## Actores y disparador

- **Actor principal:** directorio, que designa.
- **Disparadores:** inicio de operaciones; renuncia, licencia o baja del titular;
  vencimiento del período; observación del supervisor; cierre del período anual de
  capacitación.

## Precondiciones

1. La persona designada cumple los requisitos de idoneidad y no tiene
   incompatibilidades con funciones operativas (`R-SEG-04`): quien vigila no ejecuta.
2. Existe el acta del directorio que respalda la designación (`R-LIC-03`).
3. Existe [[designacion_regulatoria]] como registro del acto y su comunicación.

## Flujo principal

1. Se crea [[oficial_cumplimiento]] con `usuario_id`, `tipo` —`TITULAR` o
   `SUPLENTE`—, `fecha_designacion` y `acta_designacion`. **Nunca hay dos titulares
   activos a la vez** (`R-UIF-12`).
2. Se le asignan los roles de cumplimiento por
   [[CU-08 Asignar y revocar roles de operador]]; la designación sin permisos
   efectivos es un papel, y los permisos sin designación son un riesgo.
3. Se comunica al regulador dentro del plazo y se guarda
   `comunicada_al_regulador_en`. **El plazo se calcula al designar y se controla**:
   una designación no comunicada es tan observable como no tener oficial.
4. Al cesar, se escribe `fecha_baja` y `activo = false` **en la misma transacción**
   en que se activa el reemplazo: la función no puede quedar vacante ni un día. Si
   solo hay suplente, este asume y queda registrado.
5. **Capacitación.** Cada [[capacitacion_cumplimiento]] registra `usuario_id`,
   `tema`, `modalidad`, `horas`, `fecha`, `calificacion`, `aprobada`,
   `evidencia_url` y el `periodo` anual al que imputa.
6. Un control mensual compara el personal activo contra las capacitaciones aprobadas
   del período y devuelve **la lista nominal de quién falta**, no un porcentaje.
   Personal nuevo tiene su plazo propio desde el alta.
7. Al cerrar el período anual se consolida la evidencia para el reporte al
   supervisor ([[CU-43 Remitir los reportes mensuales a la UIF]] y el anual de
   capacitación), con horas totales, aprobados y pendientes.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Se intenta designar a alguien con funciones operativas de tesorería | Rechazo `INCOMPATIBILIDAD_DE_FUNCIONES` (`R-SEG-04`) |
| 1b | Se intenta activar un segundo titular | Rechazo por la restricción de unicidad; primero se da de baja al vigente |
| 3a | Vence el plazo de comunicación al regulador | Aparece como [[hallazgo_auditoria]] y en el tablero de vencimientos; no se puede cerrar sin evidencia del envío |
| 4a | El titular renuncia sin suplente designado | **Alerta crítica**: el suplente es obligatorio precisamente para este caso; se eleva al directorio de inmediato |
| 6a | Un empleado no aprueba la capacitación | Se reprograma dentro del período; si el período cierra sin aprobar, es un hallazgo nominal |
| 6b | Alta de personal a fin de año | Su plazo corre desde el alta, no desde enero; el control lo contempla y no lo reporta como incumplido |
| — | Capacitación dictada por tercero | Se archiva la constancia con `evidencia_url` y su hash; sirve igual, siempre que quede la evidencia |
| — | El oficial es a su vez el que aprueba sus propias capacitaciones | No corresponde: la evidencia de su capacitación la valida el directorio |

## Postcondiciones

- Existe, para toda fecha, un oficial titular activo identificable y comunicado.
- La cobertura de capacitación del período es una lista nominal, con evidencia por
  persona.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU49 = z.object({
  usuarioId:        z.string().uuid(),
  tipo:             z.enum(['TITULAR','SUPLENTE']),
  fechaDesignacion: z.string().date(),
  actaDesignacion:  z.string().max(80),
  reemplazaA:       z.string().uuid().nullable(),
}).strict()

export const EntradaCapacitacionCU49 = z.object({
  usuarioId:   z.string().uuid(),
  tema:        z.string().max(120),
  modalidad:   z.enum(['PRESENCIAL','VIRTUAL','AUTOESTUDIO']),
  horas:       z.string(),                  // decimal como cadena
  fecha:       z.string().date(),
  calificacion: z.string().nullable(),
  evidenciaUrl: z.string().url().nullable(),
}).strict()

export const SalidaCU49 = z.object({
  oficialId: z.string().uuid(),
  activo:    z.boolean(),
  plazoComunicacionHasta: z.string().date(),
  suplenteDesignado: z.boolean(),
  coberturaCapacitacion: z.object({
    periodo: z.string().length(4),
    personalActivo: z.number().int(),
    aprobados: z.number().int(),
    pendientes: z.array(z.object({ usuarioId: z.string().uuid(), nombre: z.string() })),
  }),
}).strict()

export const ErroresCU49 = {
  TITULAR_YA_ACTIVO:            'AP-CU49-01',
  INCOMPATIBILIDAD_DE_FUNCIONES:'AP-CU49-02',
  SIN_ACTA:                     'AP-CU49-03',
  SIN_SUPLENTE:                 'AP-CU49-04',
  COMUNICACION_VENCIDA:         'AP-CU49-05',
  CAPACITACION_DUPLICADA:       'AP-CU49-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `TITULAR_YA_ACTIVO` | Ya hay un titular activo; primero la baja (`R-UIF-12`) |
| `INCOMPATIBILIDAD_DE_FUNCIONES` | El designado tiene roles operativos incompatibles (`R-SEG-04`) |
| `SIN_ACTA` | Falta el respaldo del directorio (`R-LIC-03`) |
| `SIN_SUPLENTE` | Se da de baja al titular sin suplente que asuma |
| `COMUNICACION_VENCIDA` | Se cierra el trámite sin evidencia de comunicación en plazo |
| `CAPACITACION_DUPLICADA` | Misma persona, tema y período ya registrados |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `tieneIncompatibilidad(rolesActuales)` | Cruza contra la tabla de pares incompatibles; puro |
| Átomo | `coberturaDePeriodo(personal, capacitaciones, periodo)` | Devuelve la lista nominal de pendientes; puro |
| Molécula | `OficialCumplimientoRepositorio` | Unicidad de titular activo y baja encadenada |
| Molécula | `CapacitacionRepositorio` | Persistencia y consulta por período |
| Organismo | `CU49Designar` · `CU49RegistrarCapacitacion` | Transacción: designación, roles, plazo y evento |
| Página | `POST /cumplimiento/oficiales` · `POST /cumplimiento/capacitaciones` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `oficial.designado` | Asignación de roles y trabajo con el plazo de comunicación | `GOBIERNO_ADMINISTRAR` |
| `oficial.cesado` | Activación del reemplazo y revocación de accesos | `GOBIERNO_ADMINISTRAR` |
| `capacitacion.registrada` | Recálculo de cobertura del período | `CUMPLIMIENTO_ADMINISTRAR` |
| — | Control mensual de cobertura y de vencimientos de comunicación | — |

## Interfaz

- **App:** sin pantalla.
- **Backoffice:** *Gobierno → Cumplimiento*: quién es el titular y el suplente desde
  cuándo, el estado de la comunicación al regulador, y la cobertura de capacitación
  con la lista de quiénes faltan.

## Restricciones aplicables

`R-SEG-04` · `R-LIC-03` · `R-UIF-12` · `R-AUD-01` · `R-AUD-04` · `R-AUD-08`

## Evidencia que deja

[[oficial_cumplimiento]] · [[capacitacion_cumplimiento]] ·
[[designacion_regulatoria]] · [[acta_comite]] · [[asignacion_rol]] ·
[[hallazgo_auditoria]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un titular activo
Cuando se intenta designar otro titular sin dar de baja al primero
Entonces se rechaza con TITULAR_YA_ACTIVO

Dada la baja del titular con suplente designado
Cuando se ejecuta
Entonces el suplente queda activo en la misma transacción
Y no existe ningún día sin oficial activo

Dado un empleado activo sin capacitación aprobada del período
Cuando corre el control mensual
Entonces aparece por nombre en la lista de pendientes

Dado un empleado dado de alta en noviembre
Cuando corre el control de diciembre
Entonces no figura como incumplido porque su plazo corre desde el alta
```

## Ver también

[[CU-08 Asignar y revocar roles de operador]] · [[CU-43 Remitir los reportes mensuales a la UIF]] · [[CU-46 Verificar el alcance de la licencia]] · [[CU-47 Evaluar el riesgo del producto antes de lanzarlo]] · [[CU-94 Elevar una decisión al comité de gobierno]]
