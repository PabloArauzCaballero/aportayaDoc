---
tags:
  - caso-uso
  - modulo/09-auditoria-reportes-y-cumplimiento
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-07
criticidad: media
actores: [Titular de los datos, Responsable de datos]
normas: [Protección de datos, ISO/IEC 27701, ASFI ETF]
---

# CU-07 — Ejercer derechos sobre datos personales

> **Objetivo.** Atender acceso, rectificación, oposición y supresión **sin romper
> la obligación de conservar** información financiera por diez años.

## Actores y disparador

- **Actor principal:** titular de los datos.
- **Actor secundario:** responsable de protección de datos.
- **Disparador:** solicitud del titular por cualquier canal.

## Precondiciones

1. La identidad del solicitante está verificada (no basta el correo: se exige
   factor del [[CU-04 Autenticar con MFA y registrar dispositivo]]).

## Flujo principal

1. Se crea [[solicitud_datos_personales]] con `tipo`, `descripcion` y
   **`fecha_limite_legal` calculada y guardada**.
2. Se identifican los activos afectados consultando [[activo_informacion]] donde
   `contiene_datos_personales=true`, y los terceros involucrados en
   [[contrato_tercero]] con `accede_a_datos_personales=true`.
3. Según el tipo:
   - **Acceso** → se genera el paquete de datos; el acto queda en
     [[registro_acceso_datos]] con justificación.
   - **Rectificación** → se corrigen los datos y queda la traza en
     [[bitacora_evento]] (valor anterior y nuevo).
   - **Oposición** → se revoca el [[consentimiento]] de la finalidad concreta y se
     agrega a [[lista_supresion]] si es marketing.
   - **Supresión** → paso 4.
4. Para supresión se evalúa [[expediente_cliente]]`.retencion_hasta` y
   [[politica_retencion]]:
   - si la retención legal sigue corriendo, **no se borra**: se crea
     [[proceso_anonimizacion]] con `estrategia` y `datos_retenidos_por_ley`,
     seudonimizando lo que no es exigible;
   - si ya venció, se ejecuta la supresión y se deja constancia del acto.
5. Se responde al titular dentro del plazo y se cierra la solicitud.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 4a | El titular tiene saldo, deuda o grupo activo | La supresión se rechaza motivadamente: hay relación vigente; se ofrece cerrar la cuenta primero ([[CU-16 Cerrar billetera y devolver saldo]]) |
| 4b | Existe [[requerimiento_autoridad]] o [[caso_investigacion_lft]] abierto | Se suspende la supresión y se documenta el motivo legal |
| 5a | Se vence el plazo | La solicitud aparece como vencida en tablero y escala como [[hallazgo_auditoria]] |
| 3a | El paquete de acceso arrastraría datos de terceros | Se entrega redactado: los movimientos con contraparte muestran el alias, nunca el documento ni el teléfono del otro |
| — | Solicitudes repetidas del mismo titular sobre lo mismo | Se responde con la entrega anterior y su fecha; el derecho no se agota, pero tampoco se convierte en un canal de denegación de servicio |

## Postcondiciones

- Toda solicitud tiene fecha límite guardada, respuesta y trazabilidad.
- Nunca se destruye información que la ley obliga a conservar.

## Contrato · `openapi/auditoria.yaml`

```ts
export const EntradaCU07 = z.object({
  usuarioId: z.string().uuid(),
  tipo:      z.enum(['ACCESO','RECTIFICACION','OPOSICION','SUPRESION']),
  descripcion: z.string().min(10).max(1000),
}).strict()

export const SalidaCU07 = z.object({
  solicitudId:      z.string().uuid(),
  fechaLimiteLegal: z.string().datetime(),
  resultado:        z.enum(['ATENDIDA','PARCIAL','RECHAZADA','EN_PROCESO']),
  datosRetenidosPorLey: z.array(z.string()),
}).strict()

export const ErroresCU07 = {
  IDENTIDAD_NO_VERIFICADA: 'AP-CU07-01',
  RELACION_VIGENTE: 'AP-CU07-02',
  RETENCION_LEGAL_VIGENTE: 'AP-CU07-03',
  REQUERIMIENTO_ABIERTO: 'AP-CU07-04',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `IDENTIDAD_NO_VERIFICADA` | El solicitante no acreditó ser el titular |
| `RELACION_VIGENTE` | Tiene saldo, deuda o grupo activo: primero se cierra |
| `RETENCION_LEGAL_VIGENTE` | La conservación obligatoria impide borrar (R-SEG-06) |
| `REQUERIMIENTO_ABIERTO` | Hay un oficio o caso que suspende la supresión |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `calcularPlazoLegal` | Fecha límite según tipo; se guarda, no se recalcula |
| Átomo | `clasificarDatosRetenibles` | Qué se conserva por ley y qué se seudonimiza |
| Molécula | `SolicitudDatosRepositorio` | Alta y seguimiento |
| Molécula | `AnonimizacionRepositorio` | Ejecuta la estrategia elegida |
| Organismo | `CU07AtenderDerechosDeDatos` | Transacción: resolución y ejecución de la estrategia |
| Página | `POST /usuarios/:id/solicitudes-datos` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `datos.solicitud_recibida` | Arranca el reloj del plazo legal | `PARTICIPANTE` |
| `datos.solicitud_atendida` | Entrega del paquete o constancia de supresión | `SOPORTE` |

## Interfaz

- **App:** *Mis datos*: descargar, corregir u oponerse, con el plazo de respuesta a la vista.
- **Backoffice:** Cola de solicitudes con su vencimiento; las vencidas escalan a hallazgo.

## Restricciones aplicables

`R-SEG-02` · `R-SEG-06` · `R-AUD-08` · `R-CON-05`

## Evidencia que deja

[[solicitud_datos_personales]] · [[proceso_anonimizacion]] ·
[[registro_acceso_datos]] · [[consentimiento]] · [[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un titular sin relación vigente y con retención vencida
Cuando solicita supresión
Entonces se ejecuta el proceso_anonimizacion y queda constancia

Dado un titular cuya retencion_hasta es futura
Cuando solicita supresión
Entonces sus datos se seudonimizan
Y datos_retenidos_por_ley enumera lo conservado

Dado una solicitud de acceso
Cuando un operador genera el paquete
Entonces existe un registro_acceso_datos con justificación
```

## Ver también

[[CU-09 Cambiar credenciales y solicitar la baja]] · [[CU-16 Cerrar billetera y devolver saldo]] · [[CU-55 Gestionar un incidente de seguridad]] · [[CU-75 Emitir un certificado de reputación verificable]] · [[Cumplimiento]] · [[Restricciones]]
