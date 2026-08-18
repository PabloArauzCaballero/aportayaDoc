---
tags:
  - caso-uso
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-06
criticidad: media
actores: [Sistema, Analista de cumplimiento]
normas: [UIF EBR]
---

# CU-06 — Revisión periódica de conocimiento del cliente

> **Objetivo.** Que el conocimiento del cliente **se venza y se renueve**, con una
> frecuencia proporcional a su riesgo, y que el atraso sea visible antes de que
> alguien pregunte.

## Actores y disparador

- **Actor principal:** proceso programado diario.
- **Actor secundario:** analista de cumplimiento.
- **Disparadores:** `calificacion_riesgo_cliente.proxima_revision <= hoy`;
  desvío del perfil transaccional; cambio relevante declarado por el cliente.

## Precondiciones

1. El usuario tiene calificación de riesgo vigente con `periodicidad_revision_meses`.

## Flujo principal

1. El proceso crea [[revision_periodica_kyc]] con `fecha_programada` para cada
   usuario cuya revisión vence en la ventana.
2. Se recalcula el [[perfil_transaccional]] observado del período y se compara con
   el declarado; el resultado se escribe en [[desvio_perfil]] con
   `desvio_porcentual` y `severidad`.
3. Se re-cotejan listas restrictivas y se revalida la condición PEP
   ([[CU-03 Declaración PEP y beneficiario final]]).
4. Se recalculan los [[factor_riesgo_evaluado]] con la [[matriz_riesgo_lft]]
   vigente.
5. Según el resultado:
   - **sin cambios** → se cierra la revisión con `resultado='RATIFICADA'` y se
     programa la siguiente;
   - **sube el riesgo** → nueva [[calificacion_riesgo_cliente]] y exigencia de
     [[debida_diligencia]] superior ([[CU-02 Elevar nivel de debida diligencia]]);
   - **desvío severo** → se genera [[alerta_monitoreo_lft]] y puede abrirse
     [[caso_investigacion_lft]].
6. Se actualiza [[expediente_cliente]] (`completitud_porcentaje`,
   `ultima_actualizacion`).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | La revisión vence sin ejecutarse | Queda `estado='VENCIDA'` y visible en tablero; la cuenta pasa a `LIMITADA` tras el plazo de gracia definido en política |
| 2a | El cliente no responde el pedido de actualización | Se restringe el alza de posición; se documenta el intento de contacto |
| 5a | El desvío tiene justificación válida | Se registra en `desvio_perfil.justificacion` y se actualiza el perfil declarado |
| 4a | La matriz de riesgo cambió entre dos revisiones | Se califica con la matriz vigente **a la fecha de la revisión** y se guarda cuál se usó: dos años después hay que poder explicar con qué regla se lo calificó |
| — | Cuenta inactiva más allá del plazo de política o titular fallecido | No se programa revisión: se cierra el ciclo con motivo, en vez de acumular vencimientos falsos que ensucian el tablero |

## Postcondiciones

- Ningún cliente activo queda sin revisión más allá de su periodicidad sin que eso
  sea visible y tenga consecuencia operativa.

## Contrato · `openapi/cumplimiento.yaml`

```ts
export const EntradaCU06 = z.object({
  usuarioId: z.string().uuid(),
  motivo:    z.enum(['PROGRAMADA','DESVIO','CAMBIO_DECLARADO','RECLAMO']),
}).strict()

export const SalidaCU06 = z.object({
  revisionId: z.string().uuid(),
  resultado:  z.enum(['RATIFICADA','ELEVADA','OBSERVADA','ESCALADA']),
  desvioPorcentual: z.string().nullable(),
  proximaRevision:  z.string().date(),
}).strict()

export const ErroresCU06 = {
  SIN_CALIFICACION_VIGENTE: 'AP-CU06-01',
  PERFIL_NO_DECLARADO: 'AP-CU06-02',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_CALIFICACION_VIGENTE` | El usuario no tiene calificación de riesgo |
| `PERFIL_NO_DECLARADO` | Falta el perfil transaccional declarado |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `compararPerfil` | Declarado contra observado; devuelve desvío y severidad; puro |
| Átomo | `periodicidadPorRiesgo` | Meses hasta la próxima revisión |
| Molécula | `PerfilTransaccionalRepositorio` | Perfil declarado y observado |
| Molécula | `RevisionKycRepositorio` | Agenda y resultado |
| Organismo | `CU06RevisarConocimiento` | Trabajo programado, idempotente por usuario y período |
| Página | — | Sin endpoint: lo dispara el planificador o un evento |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `kyc.revisado` | Reprograma la siguiente revisión | Interno |
| `perfil.desviado` | Alerta de monitoreo si la severidad lo amerita | — |

## Interfaz

- **App:** Aviso cuando toca actualizar datos, con lo que falta y por qué se pide.
- **Backoffice:** Tablero de revisiones vencidas y por vencer, ordenado por riesgo.

## Restricciones aplicables

`R-UIF-09` · `R-UIF-11` · `R-LIM-01` · `R-AUD-04`

## Evidencia que deja

[[revision_periodica_kyc]] · [[desvio_perfil]] · [[factor_riesgo_evaluado]] ·
[[calificacion_riesgo_cliente]] · [[expediente_cliente]]

## Criterios de aceptación

```gherkin
Dado un cliente de riesgo ALTO con periodicidad de 6 meses
Cuando pasan 6 meses desde su última calificación
Entonces existe una revision_periodica_kyc programada

Dado un cliente cuyo monto observado supera en 300% al declarado
Cuando corre la revisión
Entonces existe un desvio_perfil con severidad alta
Y se genera una alerta_monitoreo_lft

Dado una revisión vencida y no ejecutada
Cuando pasa el plazo de gracia
Entonces la cuenta_billetera queda en estado LIMITADA
```

## Ver también

[[CU-02 Elevar nivel de debida diligencia]] · [[CU-03 Declaración PEP y beneficiario final]] · [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]]
