---
tags:
  - caso-uso
  - modulo/07-organizador-y-automatizacion
codigo: CU-91
criticidad: alta
actores: [Organizador, Legal, Sistema]
normas: [ASFI Consumidor Financiero, contratos de adhesión, Código de Comercio]
---

# CU-91 — Firmar y rescindir el contrato de organizador

> **Objetivo.** Que las obligaciones de quien administra grupos estén escritas,
> firmadas con evidencia oponible y versionadas — y que rescindir no deje a ningún
> grupo sin quien lo administre.

## Actores y disparador

- **Actor principal:** organizador habilitado.
- **Disparadores:** habilitación aprobada ([[CU-90 Postular a organizador y habilitarse]]);
  versión nueva del contrato; renuncia; rescisión por sanción
  ([[CU-93 Sancionar al organizador y resolver su apelación]]).

## Precondiciones

1. Existe [[organizador]] habilitado.
2. Existe la versión vigente del texto contractual con `contenido_hash`,
   `obligaciones` y `causales_rescision`.
3. El organizador tiene canal verificado para la evidencia de firma.

## Flujo principal

1. Se crea [[contrato_organizador]] con `organizador_id`, `version`,
   `contenido_hash`, `obligaciones`, `causales_rescision` y `vigente_desde`, sin
   firmar todavía.
2. Se le muestra el texto **completo** y se le pide firmar con
   [[token_verificacion]] enviado a su canal, ligado en `token_firma_id`. Se guardan
   `firmado_en`, el token consumido, la dirección de red y el aparato:
   la evidencia tiene que servir ante un tercero, no ante nosotros mismos.
3. **El hash del contenido se guarda con la firma** (`R-ORG-03`): si el texto cambia
   después, se ve que lo firmado era otra cosa. Firmar "el contrato" sin fijar cuál
   no es firmar nada.
4. Sin contrato firmado y vigente, el organizador **no puede crear grupos**
   (`R-ORG-02`). La restricción vive en la base, no en un `if` del backend.
5. **Versión nueva.** Se notifica con preaviso, se pide aceptación y **hasta que la
   firme rige la anterior**. Los grupos ya creados conservan las condiciones bajo las
   que se crearon, igual que el tarifario congelado.
6. **Rescisión.** Se escribe `rescindido_en` y `motivo_rescision`. Antes de que sea
   efectiva:
   - los grupos activos se reasignan a otro organizador o pasan a administración de
     la plataforma;
   - se liquidan las comisiones devengadas hasta la fecha;
   - se revocan sus roles ([[CU-08 Asignar y revocar roles de operador]]).
   **Un grupo nunca queda sin administrador**, y esa es la razón por la que la
   rescisión no es inmediata.
7. El contrato rescindido se conserva por el plazo de retención y sigue siendo la
   norma aplicable a los hechos ocurridos durante su vigencia.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El token de firma vence | Se emite otro; el contrato sigue sin firmar y sin efecto |
| 3a | Se intenta editar un contrato ya firmado | Imposible: se emite versión nueva (`R-ORG-03`) |
| 5a | No acepta la versión nueva en el plazo | Queda suspendido para grupos nuevos; los vigentes siguen bajo la versión anterior |
| 6a | Renuncia con grupos activos | La rescisión queda `EN_TRANSICION` hasta reasignar; se le informa exactamente qué falta |
| 6b | No hay organizador que reciba los grupos | Los administra la plataforma con un operador designado; el costo se registra |
| — | Rescisión por causal grave (fraude) | Los roles se revocan **de inmediato** y la administración pasa a la plataforma en el acto; la transición ordenada no aplica cuando el riesgo es el propio organizador |
| — | El organizador fallece o desaparece | La plataforma asume la administración con constancia y avisa a los grupos |
| — | Vuelve a postular tras una rescisión | Se evalúa como solicitud nueva, con el antecedente a la vista |

## Postcondiciones

- Todo organizador activo tiene contrato firmado, con el hash de lo que firmó.
- Ninguna rescisión deja grupos sin administrador ni comisiones sin liquidar.

## Contrato · `openapi/organizador.yaml`

```ts
export const EntradaFirmaCU91 = z.object({
  contratoId: z.string().uuid(),
  token: z.string().length(6),
  aceptaObligaciones: z.literal(true),
}).strict()

export const EntradaRescisionCU91 = z.object({
  contratoId: z.string().uuid(),
  motivo: z.string().min(20).max(300),
  causal: z.enum(['RENUNCIA','SANCION','INCUMPLIMIENTO','FRAUDE','FALLECIMIENTO']),
  organizadorReceptorId: z.string().uuid().nullable(),
}).strict()

export const SalidaCU91 = z.object({
  contratoId: z.string().uuid(),
  version: z.string(),
  contenidoHash: z.string().length(64),
  firmadoEn: z.string().datetime().nullable(),
  estado: z.enum(['PENDIENTE_FIRMA','VIGENTE','SUPERADO','EN_TRANSICION','RESCINDIDO']),
  puedeCrearGrupos: z.boolean(),
  gruposAReasignar: z.array(z.object({ grupoId: z.string().uuid(), nombre: z.string() })),
}).strict()

export const ErroresCU91 = {
  TOKEN_INVALIDO:        'AP-CU91-01',
  CONTRATO_YA_FIRMADO:   'AP-CU91-02',
  VERSION_SUPERADA:      'AP-CU91-03',
  GRUPOS_SIN_REASIGNAR:  'AP-CU91-04',
  COMISIONES_PENDIENTES: 'AP-CU91-05',
  ORGANIZADOR_NO_HABILITADO:'AP-CU91-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `TOKEN_INVALIDO` | Vencido o ya usado; se emite otro |
| `CONTRATO_YA_FIRMADO` | Reintento sobre un contrato firmado; se devuelve el existente |
| `VERSION_SUPERADA` | Se intenta firmar una versión que ya no es la vigente |
| `GRUPOS_SIN_REASIGNAR` | La rescisión no puede completarse con grupos activos sin destino |
| `COMISIONES_PENDIENTES` | Quedan devengos sin liquidar al organizador |
| `ORGANIZADOR_NO_HABILITADO` | Está suspendido o su habilitación caducó |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `hashContenido(texto)` | Hash canónico del texto contractual; puro |
| Átomo | `versionAplicable(contratos, fecha)` | Cuál regía a una fecha dada; puro |
| Molécula | `ContratoOrganizadorRepositorio` | Versionado y unicidad de vigente |
| Molécula | `ReasignadorDeGrupos` | Traslada la administración con constancia |
| Organismo | `CU91FirmarContrato` · `CU91RescindirContrato` | Transacción: firma, roles, reasignación y evento |
| Página | `POST /organizadores/contratos/:id/firma` · `POST /organizadores/contratos/:id/rescision` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `contrato_organizador.firmado` | Habilita crear grupos y notifica | Sesión del organizador |
| `contrato_organizador.rescindido` | Reasignación, liquidación y revocación de roles | `ORGANIZADORES_ADMINISTRAR` |
| `contrato_organizador.version_publicada` | Preaviso y pedido de aceptación | `LEGAL_ADMINISTRAR` |
| — | Trabajo que caduca firmas pendientes y controla transiciones abiertas | — |

## Interfaz

- **App:** el contrato completo antes de firmar, con las obligaciones y las causales
  de rescisión destacadas; y en *Mi cuenta de organizador*, qué versión firmó y
  cuándo.
- **Backoffice:** contratos por organizador con su versión y hash, y el tablero de
  rescisiones en transición con los grupos pendientes de reasignar.

## Restricciones aplicables

`R-ORG-02` · `R-ORG-03` · `R-CON-06` · `R-AUD-01` · `R-AUD-04` · `R-AUD-08`

## Evidencia que deja

[[contrato_organizador]] · [[token_verificacion]] · [[organizador]] ·
[[asignacion_rol]] · [[devengo_comision]] · [[bitacora_evento]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dado un organizador habilitado sin contrato firmado
Cuando intenta crear un grupo
Entonces la base lo impide con CONTRATO_SIN_FIRMAR

Dado un contrato firmado
Cuando se modifica el texto contractual
Entonces se crea una versión nueva y el hash firmado sigue apuntando al texto original

Dada una rescisión con grupos activos
Cuando se intenta completar sin reasignarlos
Entonces se rechaza con GRUPOS_SIN_REASIGNAR

Dada una rescisión por causal de fraude
Cuando se registra
Entonces los roles se revocan de inmediato y la plataforma asume la administración
```

## Ver también

[[CU-05 Aceptar contrato de adhesión y tarifario]] · [[CU-08 Asignar y revocar roles de operador]] · [[CU-90 Postular a organizador y habilitarse]] · [[CU-92 Evaluar el desempeño del organizador]] · [[CU-93 Sancionar al organizador y resolver su apelación]]
