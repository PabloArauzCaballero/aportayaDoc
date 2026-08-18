---
tags:
  - caso-uso
  - modulo/02-grupos-cupos-turnos-y-gobernanza
  - modulo/11-tarifas-comisiones-impuestos-y-facturacion
codigo: CU-20
criticidad: alta
actores: [Organizador, Participantes]
normas: [ASFI transparencia y consumidor financiero]
---

# CU-20 — Crear grupo y congelar tarifario

> **Objetivo.** Que el grupo nazca con su cuenta propia y con **el precio pactado
> congelado**: un aumento posterior de comisiones no puede reescribir el costo de
> un juego ya aceptado.

## Actores y disparador

- **Actor principal:** organizador (humano o digital).
- **Disparador:** creación de un grupo de pasanaku.

## Precondiciones

1. El organizador está habilitado ([[organizador]] con contrato vigente) o el grupo
   es autogestionado.
2. Existe [[tarifario]] `VIGENTE` publicado.
3. La licencia habilita el servicio ([[CU-46 Verificar el alcance de la licencia]]).

## Flujo principal

1. Se crea [[grupo]] y su [[configuracion_grupo]] (monto de aporte, periodicidad,
   número de cupos, política de mora).
2. Se resuelve el tarifario aplicable con [[asignacion_tarifario]] (usuario >
   grupo > segmento > global).
3. **En la misma transacción**:
   - se crea [[tarifa_congelada_grupo]] con `snapshot_conceptos` (JSON con los
     conceptos completos) y `hash_snapshot`;
   - se crea la [[cuenta_billetera]] de `tipo='GRUPO'`, cuyo titular es el grupo y
     **nunca el organizador** (`R-GRP-04`);
   - se crea la [[cuenta_contable]] espejo del grupo.
4. Se redacta el [[reglamento_grupo]] y cada participante lo firma
   ([[aceptacion_reglamento]]), viendo el costo que le corresponde.
5. Se crean [[participante]] y [[cupo]]; se generan [[periodo]] y [[turno]].
6. Se emite `evento_dominio` `GRUPO_CREADO`.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | No hay tarifario vigente | No se puede crear el grupo: el precio no puede quedar indefinido |
| 3a | Ya existe tarifa congelada para el grupo | Rechazo por unicidad (`R-TAR-07`) |
| 4a | Un participante no acepta el reglamento | No se le asigna cupo |
| — | Se publica un tarifario nuevo mientras el grupo corre | El grupo sigue con su snapshot hasta `vigente_hasta_ciclo_nro`; el nuevo aplica a grupos nuevos |

## Postcondiciones

- El grupo tiene cuenta propia y precio congelado con hash verificable.
- Cada participante aceptó el reglamento y conoce el costo.

## Contrato · `openapi/grupos.yaml`

```ts
export const EntradaCU20 = z.object({
  claveIdempotencia: z.string().uuid(),
  nombre: z.string().min(3).max(80),
  montoAporte: MontoSchema,
  periodicidad: z.enum(['SEMANAL','QUINCENAL','MENSUAL','BIMENSUAL']),
  cupos: z.number().int().min(2).max(40),
  diaCobro: z.number().int().min(1).max(28),
  modalidadTurnos: z.enum(['SORTEO_ALEATORIO','ORDEN_DE_INGRESO','ACUERDO_MANUAL']),
}).strict()

export const SalidaCU20 = z.object({
  grupoId: z.string().uuid(),
  codigoPublico: z.string(),
  cuentaBilleteraGrupoId: z.string().uuid(),
  tarifaCongelada: z.object({ tarifarioVersion: z.number().int(), hash: z.string().length(64) }),
}).strict()

export const ErroresCU20 = {
  SIN_TARIFARIO_VIGENTE: 'AP-CU20-01',
  ORGANIZADOR_NO_HABILITADO: 'AP-CU20-02',
  TARIFA_YA_CONGELADA: 'AP-CU20-03',
  SERVICIO_NO_AUTORIZADO: 'AP-CU20-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_TARIFARIO_VIGENTE` | No hay tarifario publicado que congelar (R-CON-07) |
| `ORGANIZADOR_NO_HABILITADO` | El organizador no está habilitado o superó su límite |
| `TARIFA_YA_CONGELADA` | El grupo ya tiene su snapshot (R-TAR-07) |
| `SERVICIO_NO_AUTORIZADO` | La licencia no habilita grupos (R-LIC-01) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `congelarConceptos` | Serializa el tarifario y calcula su hash; puro |
| Átomo | `proyectarCalendario` | Genera períodos y fechas desde la periodicidad |
| Molécula | `GrupoRepositorio` | Alta del grupo y su configuración |
| Molécula | `TarifaCongeladaRepositorio` | Snapshot con hash |
| Molécula | `CuentaBilleteraRepositorio` | Cuenta cuyo titular es el grupo |
| Organismo | `CU20CrearGrupo` | Transacción: grupo, configuración, cuenta, snapshot y calendario |
| Página | `POST /grupos` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `grupo.creado` | Invitaciones y apertura del primer período | `GRUPO_CREAR` |
| `tarifa.congelada` | Publicación del costo en la ficha del grupo | — |

## Interfaz

- **App:** Asistente de creación que muestra el costo total del ciclo antes de confirmar.
- **Backoffice:** Alta de grupos por organizador, con su límite de grupos simultáneos.

## Restricciones aplicables

`R-TAR-07` · `R-GRP-04` · `R-BIL-04` · `R-BIL-05` · `R-CON-07` · `R-LIC-01`

## Evidencia que deja

[[grupo]] · [[tarifa_congelada_grupo]] · [[cuenta_billetera]] ·
[[aceptacion_reglamento]] · [[acuerdo]] (si el precio se somete a votación)

## Criterios de aceptación

```gherkin
Dado un tarifario vigente versión 3
Cuando se crea un grupo
Entonces existe tarifa_congelada_grupo con tarifario_id de la versión 3 y hash_snapshot

Dado un grupo con tarifa congelada de la versión 3
Cuando se publica la versión 4 con comisión mayor
Entonces las entregas de ese grupo siguen calculándose con la versión 3

Dada la cuenta de un grupo
Cuando se consulta su titular
Entonces grupo_id no es nulo y usuario_id es nulo
```

## Ver también

[[CU-22 Liquidar y entregar el fondo]] · [[CU-34 Publicar un tarifario nuevo con preaviso]] · [[CU-36 Segmentar comercialmente y aplicar precio diferenciado]] · [[CU-60 Sortear los turnos]] · [[CU-68 Postular a un grupo y ser emparejado]] · [[CU-90 Postular a organizador y habilitarse]]
