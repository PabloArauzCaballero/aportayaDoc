---
tags:
  - caso-uso
  - modulo/07-organizador-y-automatizacion
codigo: CU-90
criticidad: alta
actores: [Usuario, Oficial de cumplimiento, Sistema]
normas: [UIF debida diligencia reforzada, ASFI Consumidor Financiero, gobernanza]
---

# CU-90 — Postular a organizador y habilitarse

> **Objetivo.** Que administrar el dinero de un grupo sea un privilegio que se gana
> cumpliendo requisitos medibles, y no algo que se obtiene por apretar un botón.

## Actores y disparador

- **Actor principal:** usuario que quiere organizar.
- **Disparadores:** postulación voluntaria; invitación de la plataforma a un
  participante destacado; renovación de una habilitación vencida.

## Precondiciones

1. El usuario tiene [[debida_diligencia]] **reforzada** vigente: quien maneja el
   dinero de otros se conoce mejor que quien maneja el propio.
2. Existen [[requisito_habilitacion]] activos con `tipo`, `valor_minimo`,
   `es_obligatorio` y `nivel_requerido`.
3. No tiene [[restriccion_usuario]] de tipo `SIN_ORGANIZAR` vigente
   ([[CU-27 Restringir al deudor e incluirlo en la lista interna]]).

## Flujo principal

1. Se crea [[solicitud_organizador]] con `motivacion`, `experiencia_declarada`,
   `puntaje_reputacion_al_solicitar` **congelado al momento de solicitar** y estado
   `PENDIENTE`.
2. Se evalúa cada [[requisito_habilitacion]] activo contra los datos del usuario:
   antigüedad, grupos completados como participante, reputación mínima, ausencia de
   incumplimientos en la ventana, KYC reforzado (`kyc_reforzado_id`). El resultado
   se muestra como **lista de cumplidos y faltantes**, no como un sí o un no.
3. Los requisitos `es_obligatorio = true` que no se cumplen bloquean; los demás
   determinan el `nivel_requerido` alcanzado, que limita cuántos grupos y de qué
   monto podrá administrar.
4. Se exige la [[capacitacion_organizador]] del módulo obligatorio, con
   `puntaje_evaluacion`, `aprobada` y `vigente_hasta`. **Una capacitación vencida
   suspende la habilitación**, no la elimina.
5. Revisada la solicitud (`revisada_por`, `fecha_resolucion`), si se aprueba **en la
   misma transacción**:
   - se crea el [[organizador]] con su nivel;
   - se le asigna el rol correspondiente
     ([[CU-08 Asignar y revocar roles de operador]]);
   - se encadena la firma del contrato
     ([[CU-91 Firmar y rescindir el contrato de organizador]]), sin la cual **no
     puede crear grupos**;
   - se emite `evento_dominio` `organizador.habilitado`.
6. Si se rechaza, `motivo_rechazo` explica exactamente qué faltó y **cuándo puede
   volver a postular**. Un rechazo sin camino de vuelta es una expulsión encubierta.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | Le falta un requisito obligatorio | Rechazo con la lista de faltantes y qué hacer para cumplirlos |
| 2b | Cambian los requisitos entre la solicitud y la resolución | Se evalúa con los vigentes **al momento de solicitar**: no se mueve el arco con la pelota en el aire |
| 4a | No aprueba la capacitación | Puede repetirla según la política; la solicitud queda en espera, no se rechaza |
| 4b | La capacitación vence estando habilitado | Pasa a `SUSPENDIDO`: no crea grupos nuevos, pero **sigue administrando los que tiene** — dejar grupos huérfanos sería peor |
| 5a | Se aprueba y no firma el contrato | Queda habilitado sin poder operar; a los N días la aprobación caduca |
| — | Postulación de alguien con incumplimiento en curso | Rechazo automático; puede volver cuando se resuelva y pase la ventana |
| — | Renovación periódica | Se reevalúan requisitos y capacitación; el desempeño medido ([[CU-92 Evaluar el desempeño del organizador]]) pesa en la renovación |
| — | Organizador que pide bajar de nivel | Se acepta sin penalización: reconocer que uno no da abasto es una buena señal, no una mala |

## Postcondiciones

- Todo organizador activo cumplió requisitos verificables, se capacitó y firmó
  contrato.
- Se puede reconstruir, para cualquier fecha, con qué requisitos se lo habilitó.

## Contrato · `openapi/organizador.yaml`

```ts
export const EntradaCU90 = z.object({
  motivacion: z.string().min(50).max(2000),
  experienciaDeclarada: z.string().max(2000),
  nivelSolicitado: z.enum(['BASICO','INTERMEDIO','AVANZADO']),
}).strict()

export const SalidaCU90 = z.object({
  solicitudId: z.string().uuid(),
  estado: z.enum(['PENDIENTE','APROBADA','RECHAZADA','EN_CAPACITACION','CADUCADA']),
  requisitos: z.array(z.object({
    codigo: z.string(), descripcion: z.string(),
    esObligatorio: z.boolean(), cumple: z.boolean(),
    valorActual: z.string(), valorMinimo: z.string(),
  })),
  nivelAlcanzado: z.enum(['BASICO','INTERMEDIO','AVANZADO']).nullable(),
  capacitacionPendiente: z.boolean(),
  motivoRechazo: z.string().nullable(),
  puedeReintentarDesde: z.string().date().nullable(),
}).strict()

export const ErroresCU90 = {
  KYC_INSUFICIENTE:       'AP-CU90-01',
  REQUISITO_OBLIGATORIO_FALTANTE:'AP-CU90-02',
  RESTRICCION_VIGENTE:    'AP-CU90-03',
  SOLICITUD_DUPLICADA:    'AP-CU90-04',
  CAPACITACION_PENDIENTE: 'AP-CU90-05',
  CONTRATO_SIN_FIRMAR:    'AP-CU90-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `KYC_INSUFICIENTE` | No tiene debida diligencia reforzada vigente (`R-UIF-09`) |
| `REQUISITO_OBLIGATORIO_FALTANTE` | Falta al menos un requisito obligatorio; se enumeran todos |
| `RESTRICCION_VIGENTE` | Tiene restricción `SIN_ORGANIZAR` |
| `SOLICITUD_DUPLICADA` | Ya tiene una solicitud pendiente (`R-ORG-01`) |
| `CAPACITACION_PENDIENTE` | Aprobado por requisitos pero sin capacitación aprobada |
| `CONTRATO_SIN_FIRMAR` | Intenta crear un grupo sin contrato firmado (`R-ORG-02`) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `evaluarRequisitos(requisitos, hechos)` | Devuelve cumple/no por requisito con valores; puro |
| Átomo | `nivelAlcanzado(requisitosCumplidos)` | Traduce a nivel; puro |
| Molécula | `SolicitudOrganizadorRepositorio` | Persistencia y unicidad de pendiente |
| Molécula | `CapacitacionOrganizadorRepositorio` | Módulos, vigencia y aprobación |
| Organismo | `CU90ResolverSolicitud` | Transacción: organizador, rol, contrato pendiente y evento |
| Página | `POST /organizadores/solicitudes` · `POST /organizadores/solicitudes/:id/resolucion` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `organizador.solicitado` | Evaluación automática de requisitos y aviso al revisor | Sesión del titular |
| `organizador.habilitado` | Asignación de rol y pedido de firma del contrato | `ORGANIZADORES_ADMINISTRAR` |
| `organizador.suspendido` | Bloqueo de creación de grupos, sin tocar los vigentes | — |
| — | Trabajo que vence capacitaciones y caduca aprobaciones sin contrato | — |

## Interfaz

- **App:** *Quiero organizar*: la lista de requisitos con su estado y cuánto falta
  para cada uno, **antes** de postular. Nadie debería enterarse de que no califica
  después de escribir una carta de motivación.
- **Backoffice:** bandeja de solicitudes con la evaluación automática ya hecha y el
  historial del postulante.

## Restricciones aplicables

`R-UIF-09` · `R-ORG-01` · `R-ORG-02` · `R-SEG-04` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[solicitud_organizador]] · [[requisito_habilitacion]] ·
[[capacitacion_organizador]] · [[organizador]] · [[asignacion_rol]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un usuario con KYC reforzado que cumple todos los requisitos obligatorios
Cuando postula y aprueba la capacitación
Entonces se crea el organizador con su nivel
Y queda pendiente la firma del contrato

Dado un usuario al que le falta un requisito obligatorio
Cuando postula
Entonces se rechaza enumerando todos los requisitos con su estado

Dado un organizador cuya capacitación vence
Cuando corre el control diario
Entonces queda SUSPENDIDO y no puede crear grupos nuevos
Y sigue administrando los grupos que ya tenía

Dado un cambio de requisitos posterior a una solicitud pendiente
Cuando se la resuelve
Entonces se evalúa con los requisitos vigentes al momento de solicitar
```

## Ver también

[[CU-08 Asignar y revocar roles de operador]] · [[CU-20 Crear grupo y congelar tarifario]] · [[CU-91 Firmar y rescindir el contrato de organizador]] · [[CU-92 Evaluar el desempeño del organizador]]
