---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
codigo: CU-68
criticidad: alta
actores: [Usuario, Organizador, Sistema]
normas: [UIF conocimiento del cliente, no discriminación, gobernanza del grupo]
---

# CU-68 — Postular a un grupo y ser emparejado

> **Objetivo.** Que alguien sin conocidos pueda entrar a un pasanaku con gente de
> riesgo comparable, y que el criterio con el que se lo agrupa sea explicable —a él
> y al supervisor— en lugar de una caja negra.

## Actores y disparador

- **Actor principal:** usuario que quiere participar.
- **Disparadores:** postulación abierta a un grupo existente; solicitud de
  emparejamiento sin grupo elegido; cupo liberado por retiro o reemplazo.

## Precondiciones

1. El usuario tiene billetera abierta y [[debida_diligencia]] vigente del nivel que
   el grupo exige (`R-UIF-09`).
2. No tiene [[restriccion_usuario]] de tipo `SIN_GRUPOS_NUEVOS` vigente
   ([[CU-27 Restringir al deudor e incluirlo en la lista interna]]).
3. Existe [[criterio_emparejamiento]] vigente con sus pesos y su
   `reputacion_minima`.

## Flujo principal

1. **Postulación a un grupo concreto.** Se crea [[solicitud_ingreso]] con
   `grupo_id`, `usuario_id`, `cupos_solicitados`, `mensaje` y estado `PENDIENTE`.
2. Se calcula `puntaje_compatibilidad` con el [[criterio_emparejamiento]] vigente,
   ponderando reputación ([[reputacion_usuario]]), monto declarado
   ([[perfil_financiero]]), geografía e historial compartido con los integrantes.
   **Los pesos son datos con vigencia, no constantes en el código.**
3. Se evalúa el [[score_riesgo_incumplimiento]] del postulante para ese grupo. Si el
   grupo ya alcanzó `max_morosos_por_grupo`, la solicitud se rechaza con motivo:
   proteger a los que ya están es parte del servicio.
4. El organizador revisa (`revisada_por`, `fecha_resolucion`) y acepta o rechaza.
   Aceptada, **en la misma transacción**:
   - se asigna el [[cupo]] libre y se crea o activa el [[participante]];
   - se exige la [[aceptacion_reglamento]] antes de que el cupo quede firme;
   - se registra [[historial_estado_grupo]] si el grupo cambia de estado por
     completarse;
   - se emite `evento_dominio` `ingreso.aceptado`.
5. **Emparejamiento sin grupo.** El usuario crea una
   [[postulacion_emparejamiento]] con lo que busca: monto, periodicidad y cuándo
   podría cobrar. El motor arma [[propuesta_grupo]] con `puntaje_cohesion`,
   `riesgo_estimado` y `expira_en`, y liga a cada postulante por
   [[propuesta_postulacion]].
6. Cada postulante acepta o rechaza (`acepto`, `respondido_en`). Alcanzadas las
   `aceptaciones_recibidas` necesarias antes de `expira_en`, la propuesta se
   materializa en un [[grupo]] real ([[CU-20 Crear grupo y congelar tarifario]]) y se
   guarda `grupo_materializado_id`.
7. El usuario ve **por qué** se le propuso ese grupo: monto compatible, gente de su
   zona, riesgo similar. Nunca los datos de los demás, sí el criterio.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El postulante no alcanza `reputacion_minima` | Se rechaza indicando qué le falta y cómo mejorarlo; sin historial se ofrecen grupos de monto bajo, **no se lo excluye por ser nuevo** |
| 3a | El grupo alcanzó el tope de perfiles de riesgo alto | Rechazo con motivo; se sugieren grupos alternativos compatibles |
| 4a | El grupo se llena mientras la solicitud está pendiente | Queda en lista de espera con su puntaje; si se libera un cupo, es la primera candidata ([[CU-66 Reemplazar a un participante moroso]]) |
| 5a | La propuesta expira sin aceptaciones suficientes | Se disuelve sin efecto, los postulantes vuelven a la bolsa y se recompone con otro conjunto |
| 6a | Un postulante acepta y después se arrepiente antes de materializar | Puede retirar su aceptación hasta la materialización; después es [[CU-65 Retirarse de un grupo]] |
| 5b | Dos propuestas incluyen al mismo postulante | Solo puede aceptar una; la aceptación de una libera su postulación de las demás |
| — | El criterio de emparejamiento cambia | Las propuestas abiertas conservan el criterio con el que se armaron; el nuevo rige para las siguientes |
| — | Postulación con dos cupos en el mismo grupo | Permitida si el reglamento lo admite; cada cupo es una obligación independiente y así se le muestra |

## Postcondiciones

- Toda incorporación a un grupo tiene solicitud, evaluación, decisión con
  responsable y reglamento aceptado.
- Ningún grupo queda armado con una concentración de riesgo mayor a la que su
  criterio permite.

## Contrato · `openapi/grupos.yaml`

```ts
export const EntradaCU68 = z.object({
  grupoId: z.string().uuid(),
  cuposSolicitados: z.number().int().min(1).max(3),
  mensaje: z.string().max(300).nullable(),
}).strict()

export const EntradaEmparejarCU68 = z.object({
  montoAporte:  MontoSchema,
  periodicidad: z.enum(['SEMANAL','QUINCENAL','MENSUAL']),
  turnoPreferido: z.enum(['TEMPRANO','INDIFERENTE','TARDIO']),
  departamento: z.string().max(30),
}).strict()

export const SalidaCU68 = z.object({
  solicitudId: z.string().uuid().nullable(),
  propuestaId: z.string().uuid().nullable(),
  estado: z.enum(['PENDIENTE','ACEPTADA','RECHAZADA','EN_ESPERA','EXPIRADA']),
  puntajeCompatibilidad: z.string().nullable(),
  motivoLegible: z.string(),
  cupoAsignado: z.number().int().nullable(),
}).strict()

export const ErroresCU68 = {
  RESTRICCION_VIGENTE:     'AP-CU68-01',
  KYC_INSUFICIENTE:        'AP-CU68-02',
  REPUTACION_INSUFICIENTE: 'AP-CU68-03',
  SIN_CUPOS_LIBRES:        'AP-CU68-04',
  CONCENTRACION_DE_RIESGO: 'AP-CU68-05',
  SOLICITUD_DUPLICADA:     'AP-CU68-06',
  PROPUESTA_EXPIRADA:      'AP-CU68-07',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `RESTRICCION_VIGENTE` | Tiene restricción `SIN_GRUPOS_NUEVOS`; se informa el monto que la levanta |
| `KYC_INSUFICIENTE` | No alcanza el nivel de debida diligencia que el grupo exige (`R-UIF-09`) |
| `REPUTACION_INSUFICIENTE` | Por debajo de la `reputacion_minima` del criterio vigente |
| `SIN_CUPOS_LIBRES` | El grupo se completó; pasa a lista de espera |
| `CONCENTRACION_DE_RIESGO` | Superaría `max_morosos_por_grupo` |
| `SOLICITUD_DUPLICADA` | Ya tiene una solicitud pendiente para ese grupo (`R-GRP-14`) |
| `PROPUESTA_EXPIRADA` | Se acepta después de `expira_en` |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `puntajeCompatibilidad(perfil, grupo, criterio)` | Suma ponderada con los pesos vigentes; puro |
| Átomo | `explicarPuntaje(componentes)` | Traduce el número a motivos legibles; puro |
| Átomo | `concentracionDeRiesgo(participantes, candidato)` | Cuenta perfiles por nivel; puro |
| Molécula | `SolicitudIngresoRepositorio` · `PropuestaGrupoRepositorio` | Persistencia y unicidad |
| Molécula | `MotorDeEmparejamiento` | Arma conjuntos candidatos y los puntúa |
| Organismo | `CU68AceptarIngreso` · `CU68MaterializarPropuesta` | Transacción: cupo, participante, reglamento y evento |
| Página | `POST /grupos/:id/solicitudes` · `POST /emparejamiento` · `POST /propuestas/:id/respuesta` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `ingreso.solicitado` | Aviso al organizador con el puntaje y sus motivos | `PARTICIPANTE` |
| `ingreso.aceptado` | Alta del participante, reglamento y aviso al grupo | `GRUPO_ADMINISTRAR` |
| `propuesta.armada` | Aviso a los postulantes con el plazo de aceptación | — |
| `propuesta.materializada` | Creación del grupo y congelamiento del tarifario | — |
| — | Trabajo que expira propuestas y recompone conjuntos | — |

## Interfaz

- **App:** *Buscar grupo*: filtros por monto y periodicidad, y para cada grupo el
  motivo de la recomendación. En una propuesta de emparejamiento se ve cuántos
  aceptaron, cuánto falta y hasta cuándo — nunca la identidad de los otros antes de
  materializar.
- **Backoffice:** solicitudes por grupo con su puntaje y el desglose, y salud del
  motor de emparejamiento.

## Restricciones aplicables

`R-UIF-09` · `R-GRP-14` · `R-GRP-15` · `R-AUD-01` · `R-AUD-04` · `R-SEG-03`

## Evidencia que deja

[[solicitud_ingreso]] · [[postulacion_emparejamiento]] · [[propuesta_grupo]] ·
[[propuesta_postulacion]] · [[criterio_emparejamiento]] ·
[[score_riesgo_incumplimiento]] · [[perfil_financiero]] · [[cupo]] ·
[[participante]] · [[aceptacion_reglamento]] · [[historial_estado_grupo]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un usuario sin restricciones y con KYC suficiente
Cuando postula a un grupo con cupos libres
Entonces existe una solicitud_ingreso con puntaje_compatibilidad calculado
Y la respuesta incluye el motivo legible del puntaje

Dado un usuario con restricción SIN_GRUPOS_NUEVOS vigente
Cuando postula
Entonces se rechaza con RESTRICCION_VIGENTE indicando el monto que la levanta

Dada una propuesta de grupo que alcanza las aceptaciones antes de expirar
Cuando el último postulante acepta
Entonces se materializa un grupo y se guarda grupo_materializado_id

Dada una propuesta expirada
Cuando un postulante intenta aceptarla
Entonces se rechaza con PROPUESTA_EXPIRADA y vuelve a la bolsa
```

## Ver también

[[CU-20 Crear grupo y congelar tarifario]] · [[CU-27 Restringir al deudor e incluirlo en la lista interna]] · [[CU-64 Traspasar un cupo]] · [[CU-66 Reemplazar a un participante moroso]] · [[CU-69 Invitar a un contacto y registrar sus referencias]]
