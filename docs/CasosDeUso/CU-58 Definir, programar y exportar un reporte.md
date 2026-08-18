---
tags:
  - caso-uso
  - modulo/09-auditoria-reportes-y-cumplimiento
codigo: CU-58
criticidad: media
actores: [Analista, Oficial de cumplimiento, Auditoría, Sistema]
normas: [Protección de datos, ASFI conservación de información, segregación de funciones]
---

# CU-58 — Definir, programar y exportar un reporte

> **Objetivo.** Que sacar información del sistema sea una operación con permiso,
> huella y vencimiento — y no una consulta suelta que alguien corre contra la base
> de producción y manda por correo.

## Actores y disparador

- **Actor principal:** analista o auditor con permiso sobre el reporte.
- **Disparadores:** pedido puntual; programación periódica; requerimiento de
  auditoría o de autoridad ([[CU-45 Atender un requerimiento de autoridad]]).

## Precondiciones

1. Existe [[definicion_reporte]] activa, con `consulta_base`,
   `parametros_esperados`, `columnas`, `permiso_requerido` y
   `contiene_datos_sensibles`.
2. Quien ejecuta tiene el permiso que la definición exige
   ([[CU-08 Asignar y revocar roles de operador]]).
3. La consulta corre contra la **réplica de solo lectura**: un reporte pesado nunca
   compite con el camino del dinero.

## Flujo principal

1. Se crea [[ejecucion_reporte]] con `definicion_id`, `solicitado_por`,
   `parametros` y estado `EN_COLA`. **Los parámetros se validan contra
   `parametros_esperados`**: no se acepta parámetro libre que termine dentro de la
   consulta.
2. La consulta se ejecuta con la sesión del solicitante, de modo que las políticas
   de fila siguen rigiendo (`R-SEG-03`). Un reporte no es una puerta trasera al RLS.
3. Al terminar se guardan `filas_generadas`, `duracion_ms` y **`hash_resultado`**:
   dos ejecuciones con los mismos parámetros y los mismos datos dan el mismo hash, y
   eso permite probar que un reporte entregado no fue alterado después.
4. Si la definición tiene `contiene_datos_sensibles = true`, se escribe
   [[registro_acceso_datos]] con la justificación del solicitante (`R-SEG-02`).
5. La exportación crea [[exportacion_reporte]] con `formato`, `url_archivo`,
   `hash_archivo`, `tamano_bytes`, `esta_cifrado` y **`expira_en`**: los archivos
   con datos personales se cifran y **caducan**; un enlace eterno es una fuga futura.
6. Cada descarga incrementa `descargas` y queda registrada. Superado el tope, el
   enlace se invalida y hay que pedirlo de nuevo, con nueva justificación.
7. **Programación.** [[programacion_reporte]] define `expresion_cron`,
   `parametros_fijos`, `destinatarios`, `canal_entrega` y `formato`, y actualiza
   `ultima_ejecucion_en` y `proxima_ejecucion_en`. El trabajo toma la programación
   con bloqueo, para que dos réplicas no manden el mismo reporte dos veces.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | Parámetro fuera de lo esperado | Rechazo `PARAMETRO_INVALIDO`; nunca se concatena texto del usuario en la consulta |
| 2a | La consulta excede el tiempo máximo | Se cancela y queda `FALLIDA` con el motivo; se sugiere acotar el rango. **La base de producción no se cuelga por un reporte** |
| 3a | Reporte vacío | Se entrega igual, con cero filas: "no hubo" es una respuesta, y para los regulatorios es obligatoria (`R-UIF-06`) |
| 5a | Se intenta exportar un reporte sensible sin cifrado | Rechazo: `esta_cifrado` es obligatorio cuando la definición lo marca |
| 5b | El archivo expiró | El enlace deja de servir; se vuelve a ejecutar, dejando nueva huella |
| 7a | La ejecución programada falla | Se reintenta con espera creciente; agotado, se avisa a los destinatarios de que **no** llegó, en vez de que el silencio parezca normalidad |
| — | Cambio de la definición | Se versiona: las ejecuciones viejas conservan con qué definición se produjeron |
| — | Pedido de auditoría de "quién sacó qué" | Se responde con las ejecuciones, sus solicitantes, sus parámetros y sus descargas |

## Postcondiciones

- Toda extracción de datos tiene solicitante, parámetros, huella y hash.
- Ningún archivo con datos personales queda accesible sin cifrado ni sin fecha de
  caducidad.

## Contrato · `openapi/auditoria.yaml`

```ts
export const EntradaCU58 = z.object({
  definicionId: z.string().uuid(),
  parametros:   z.record(z.unknown()),
  formato:      z.enum(['CSV','XLSX','PDF','JSON']).nullable(),
  justificacion: z.string().max(300).nullable(),   // obligatoria si es sensible
}).strict()

export const EntradaProgramarCU58 = z.object({
  definicionId:  z.string().uuid(),
  expresionCron: z.string().max(40),
  parametrosFijos: z.record(z.unknown()),
  destinatarios: z.array(z.string().email()).min(1).max(20),
  canalEntrega:  z.enum(['CORREO','SFTP','DESCARGA']),
  formato:       z.enum(['CSV','XLSX','PDF','JSON']),
}).strict()

export const SalidaCU58 = z.object({
  ejecucionId:   z.string().uuid(),
  estado:        z.enum(['EN_COLA','EJECUTANDO','LISTA','FALLIDA','CANCELADA']),
  filasGeneradas: z.number().int(),
  hashResultado: z.string().length(64).nullable(),
  exportacion: z.object({
    url: z.string().url(), hashArchivo: z.string().length(64),
    estaCifrado: z.boolean(), expiraEn: z.string().datetime(),
  }).nullable(),
}).strict()

export const ErroresCU58 = {
  SIN_PERMISO:            'AP-CU58-01',
  PARAMETRO_INVALIDO:     'AP-CU58-02',
  JUSTIFICACION_REQUERIDA:'AP-CU58-03',
  TIEMPO_EXCEDIDO:        'AP-CU58-04',
  EXPORTACION_VENCIDA:    'AP-CU58-05',
  TOPE_DESCARGAS:         'AP-CU58-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_PERMISO` | El solicitante no tiene el `permiso_requerido` de la definición |
| `PARAMETRO_INVALIDO` | No coincide con `parametros_esperados` en nombre, tipo o rango |
| `JUSTIFICACION_REQUERIDA` | El reporte contiene datos sensibles y no se justificó (`R-SEG-02`) |
| `TIEMPO_EXCEDIDO` | La consulta superó el tiempo máximo y se canceló |
| `EXPORTACION_VENCIDA` | Se intenta descargar pasado `expira_en` |
| `TOPE_DESCARGAS` | Se agotaron las descargas permitidas del archivo |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `validarParametros(esperados, recibidos)` | Nombre, tipo y rango; puro |
| Átomo | `hashResultado(filas, columnas)` | Hash canónico y estable del resultado; puro |
| Molécula | `DefinicionReporteRepositorio` · `EjecucionRepositorio` | Persistencia y versionado |
| Molécula | `EjecutorDeConsulta` | Corre en la réplica de lectura, con tiempo máximo y sesión del solicitante |
| Molécula | `ExportadorCifrado` | Genera el archivo, lo cifra y le pone caducidad |
| Organismo | `CU58EjecutarReporte` | Orquesta ejecución, huella, exportación y evento |
| Página | `POST /reportes/:id/ejecuciones` · `GET /exportaciones/:id` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `reporte.ejecutado` | Exportación y entrega por el canal configurado | El de la definición |
| `reporte.descargado` | Registro de acceso a datos si es sensible | — |
| `reporte.fallido` | Aviso explícito a los destinatarios | — |
| — | Trabajo cron por programación, con bloqueo entre réplicas; y trabajo de purga de exportaciones vencidas | — |

## Interfaz

- **App:** sin pantalla. El usuario final ve extractos por
  [[CU-15 Emitir extracto y certificado de saldo]], que es otra cosa.
- **Backoffice:** *Reportes*: catálogo con el permiso que exige cada uno, historial
  de ejecuciones con quién y cuándo, y las programaciones con su próxima corrida.

## Restricciones aplicables

`R-SEG-02` · `R-SEG-03` · `R-AUD-01` · `R-AUD-08` · `R-UIF-06` · `R-CON-05`

## Evidencia que deja

[[definicion_reporte]] · [[ejecucion_reporte]] · [[exportacion_reporte]] ·
[[programacion_reporte]] · [[registro_acceso_datos]] · [[bitacora_evento]] ·
`evento_dominio`

## Criterios de aceptación

```gherkin
Dada una definición con permiso requerido que el solicitante no tiene
Cuando intenta ejecutarla
Entonces se rechaza con SIN_PERMISO y queda registrado el intento

Dada una definición con contiene_datos_sensibles en true
Cuando se ejecuta sin justificación
Entonces se rechaza con JUSTIFICACION_REQUERIDA

Dada una exportación con datos personales
Cuando se genera el archivo
Entonces esta_cifrado es true y expira_en no es nulo

Dada una misma ejecución con los mismos parámetros y datos
Cuando se repite
Entonces hash_resultado coincide con el de la primera
```

## Ver también

[[CU-15 Emitir extracto y certificado de saldo]] · [[CU-43 Remitir los reportes mensuales a la UIF]] · [[CU-45 Atender un requerimiento de autoridad]] · [[CU-98 Publicar el tablero de indicadores]]
