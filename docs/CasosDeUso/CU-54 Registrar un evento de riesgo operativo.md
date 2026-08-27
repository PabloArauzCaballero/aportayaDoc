---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-54
criticidad: alta
actores: [Riesgos, Áreas operativas]
normas: [ASFI RNSF Libro 3 Título V — base de datos de eventos y central de riesgo operativo]
---

# CU-54 — Registrar un evento de riesgo operativo

> **Objetivo.** Ponerle número a lo que cuesta hacer las cosas mal, con las
> categorías y factores que exige la norma, y **cerrar el círculo con un plan de
> acción con responsable y plazo**.

## Actores y disparador

- **Actor principal:** unidad de gestión de riesgos.
- **Actores secundarios:** área donde ocurrió el evento.
- **Disparadores:** descuadre de custodia, reverso por error operativo,
  acreditación duplicada, caída con impacto monetario, fraude interno o externo,
  reclamo procedente por falla de servicio.

## Precondiciones

1. El hecho está identificado y tiene fecha de ocurrencia y de detección.

## Flujo principal

1. Se crea [[evento_riesgo_operativo]] (*append-only*) con:
   - `categoria_evento` entre las seis: fraude interno, fraude externo, relaciones
     laborales, clientes/productos/prácticas, daños a activos, fallas en sistemas;
   - `factor_riesgo` entre los cinco: procesos internos, personas, tecnología de
     información, eventos externos, infraestructura;
   - `linea_negocio`, `fecha_ocurrencia`, `fecha_deteccion`,
     `fecha_contabilizacion`, `perdida_bruta`, `recuperacion` (la neta es
     generada) y `causa_raiz`.
2. Se enlaza el origen: [[incidente_operativo]] (M9), [[descuadre_custodia]],
   [[reverso_transaccion]] o [[incidente_seguridad]].
3. Se crea [[plan_accion_riesgo]] con `responsable_id`, `fecha_compromiso` y
   evidencia esperada.
4. Se marca `reportado_central_riesgo_operativo` cuando entra en el envío al
   supervisor ([[reporte_regulatorio]]).
5. El comité de riesgos revisa los eventos del período y deja constancia en
   [[acta_comite]].

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Evento sin pérdida monetaria (casi-pérdida) | Se registra igual con `perdida_bruta=0`: la frecuencia también es información |
| 1b | Recuperación posterior (seguro, cobranza) | Se agrega `recuperacion`; la pérdida bruta **no se edita** |
| 3a | Plan vencido sin cierre | Escala a [[hallazgo_auditoria]] y se reporta al comité |
| — | El evento revela un control inexistente | Se crea [[control_interno]] nuevo y su [[prueba_control]] |

## Postcondiciones

- Existe una base de pérdidas consultable que permite decidir inversiones en
  control con números y no con opiniones.

## Contrato · `openapi/cumplimiento.yaml`

> **Vive en `cumplimiento`, no en `auditoria`.** Las cuatro tablas que este caso de
> uso escribe —[[evento_riesgo_operativo]], [[plan_accion_riesgo]],
> [[hallazgo_auditoria]] y [[acta_comite]]— están en el esquema `cumplimiento`, y
> `svc_auditoria` no tiene `GRANT` sobre él. Implementarlo allá habría exigido leer el
> esquema de otro servicio, que es el invariante 11. Donde la ficha y el modelo no
> coincidían, mandó el modelo: es el que rechaza.
>
> La ruta también cambió: `POST /cumplimiento/riesgos/eventos` en vez de
> `/riesgos/eventos`, porque `/riesgos` no es un prefijo reservado de este servicio.

```ts
export const EntradaCU54 = z.object({
  categoriaEvento: z.enum(['FRAUDE_INTERNO','FRAUDE_EXTERNO','RELACIONES_LABORALES','CLIENTES_PRODUCTOS_PRACTICAS','DANOS_ACTIVOS','FALLAS_SISTEMAS']),
  factorRiesgo: z.enum(['PROCESOS_INTERNOS','PERSONAS','TECNOLOGIA_INFORMACION','EVENTOS_EXTERNOS','INFRAESTRUCTURA']),
  lineaNegocio: z.string().max(40),
  perdidaBruta: MontoSchema,
  fechaOcurrencia: z.string().datetime(),
  fechaDeteccion: z.string().datetime(),
  descripcion: z.string().min(20),
}).strict()

export const SalidaCU54 = z.object({
  eventoId: z.string().uuid(),
  codigo: z.string(),
  perdidaNeta: MontoSchema,
  planAccionId: z.string().uuid().nullable(),
}).strict()

export const ErroresCU54 = {
  FECHAS_INCOHERENTES: 'AP-CU54-01',
  RECUPERACION_MAYOR_A_PERDIDA: 'AP-CU54-02',
  TAXONOMIA_INVALIDA: 'AP-CU54-03',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `FECHAS_INCOHERENTES` | La detección no puede ser anterior a la ocurrencia |
| `RECUPERACION_MAYOR_A_PERDIDA` | La recuperación no puede superar la pérdida bruta |
| `TAXONOMIA_INVALIDA` | Categoría o factor fuera de la taxonomía (R-RIS-01) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `clasificarEvento` | Valida la taxonomía y calcula la pérdida neta; puro |
| Molécula | `EventoRiesgoRepositorio` | Base de pérdidas, append-only |
| Molécula | `PlanAccionRepositorio` | Remediación con responsable y plazo |
| Organismo | `CU54RegistrarRiesgoOperativo` | Transacción: evento y plan de acción |
| Página | `POST /cumplimiento/riesgos/eventos` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `riesgo.evento_registrado` | Plan de acción y reporte a la central | `RESPONSABLE_RIESGOS` |
| `plan.vencido` | Hallazgo de auditoría | — |

## Interfaz

- **App:** Sin pantalla en la app.
- **Backoffice:** Base de pérdidas con filtros por categoría, factor y línea de negocio.

## Restricciones aplicables

`R-AUD-01` · `R-RIS-01` · `R-RIS-02` · `R-LIC-03`

## Evidencia que deja

[[evento_riesgo_operativo]] · [[plan_accion_riesgo]] · [[acta_comite]] ·
[[hallazgo_auditoria]] (si el plan vence)

## Criterios de aceptación

```gherkin
Dado un descuadre de custodia no explicado por Bs 1.240
Cuando se registra el evento
Entonces existe evento_riesgo_operativo con categoria_evento y factor_riesgo válidos
Y perdida_neta se calcula como bruta menos recuperación

Dado un intento de modificar perdida_bruta de un evento registrado
Cuando se ejecuta
Entonces la base de datos lo rechaza

Dado un plan de acción vencido
Cuando corre el control diario
Entonces existe un hallazgo_auditoria abierto
```

## Ver también

[[CU-14 Reversar una transacción]] · [[CU-19 Reembolsar un pago y atender una disputa]] · [[CU-23 Cubrir un incumplimiento con el fondo]] · [[CU-50 Conciliar la custodia y verificar el encaje]] · [[CU-55 Gestionar un incidente de seguridad]] · [[CU-56 Ejecutar una prueba de continuidad]] · [[CU-57 Operar un punto de atención y arquear el efectivo]] · [[CU-73 Verificar la cadena de transparencia]] · [[CU-93 Sancionar al organizador y resolver su apelación]] · [[CU-94 Elevar una decisión al comité de gobierno]]
