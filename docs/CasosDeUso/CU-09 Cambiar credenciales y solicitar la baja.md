---
tags:
  - caso-uso
  - modulo/01-identidad-usuarios-y-seguridad
codigo: CU-09
criticidad: alta
actores: [Usuario, Soporte, Responsable de seguridad, Sistema]
normas: [ASFI Seguridad de la Información, ASFI Consumidor Financiero, Protección de datos, ISO/IEC 27001 A.5.17, ISO/IEC 27001 A.8.2]
---

# CU-09 — Cambiar credenciales y solicitar la baja

> **Objetivo.** Que el usuario pueda cambiar su clave, recuperarla si la perdió y
> pedir la baja de la cuenta, sin que ninguno de esos tres caminos se convierta en
> una forma de tomarle la cuenta a otro ni de escaparse de una deuda.

## Actores y disparador

- **Actor principal:** usuario titular.
- **Disparadores:** cambio voluntario; olvido de clave; sospecha de compromiso;
  vencimiento de la política de rotación para operadores; decisión de dar de baja.

## Precondiciones

1. Existe [[credencial_acceso]] vigente para el usuario.
2. Para el cambio con clave conocida: sesión activa y verificada.
3. Para la recuperación: canal verificado en [[canal_vinculado]] y
   [[politica_token]] con vencimiento y usos definidos.
4. Para la recuperación **de un operador** —usuario con [[asignacion_rol]] vigente sobre
   un [[rol]] de ámbito `GLOBAL`—: existe otra identidad con el permiso
   `SEGURIDAD_ACCESO_RESTABLECER` disponible para aprobar
   ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]).

## Flujo principal

1. **Cambio con clave conocida.** Se pide la clave actual y la nueva. Se valida la
   nueva contra la política —longitud, no reutilización, no derivada del documento
   ni del teléfono— y se compara contra [[historial_credencial]]: **las últimas N
   claves no se pueden repetir**.
2. **En la misma transacción**:
   - la clave anterior pasa a [[historial_credencial]] con `hash_contrasena` y
     `reemplazada_en`;
   - se actualiza [[credencial_acceso]] con el hash nuevo;
   - se cierran **todas** las [[sesion]] salvo la que hizo el cambio;
   - se registra en [[bitacora_evento]] y se emite `evento_dominio`
     `credencial.cambiada`.
3. Se notifica al usuario por **todos** sus canales verificados, con el aparato y la
   hora: si no fue él, tiene que enterarse por una vía que el atacante no controle.
4. **Recuperación.** Se emite [[token_verificacion]] de un solo uso y vencimiento
   corto al canal verificado. Cada intento se registra en
   [[intento_validacion_token]]; agotados los intentos, el token muere.
5. Tras recuperar, la cuenta entra en **ventana de enfriamiento**: durante las horas
   que fija [[politica_billetera]] no se puede retirar ni cambiar el instrumento de
   cobro. Recuperar el acceso y vaciar la billetera en el mismo minuto es el patrón
   de fraude más común, y esta ventana es lo que lo corta.
6. **Baja de cuenta.** Se crea [[solicitud_baja]] con `motivo` y `solicitada_en`. El
   sistema evalúa obligaciones abiertas y escribe `bloqueada_por_obligaciones`:
   - **sin obligaciones** → se fija `fecha_efectiva` y se encadena con
     [[CU-16 Cerrar billetera y devolver saldo]];
   - **con obligaciones** → se rechaza motivadamente, enumerando cuáles: grupos
     activos, deuda viva, retenciones o bloqueos (`R-BIL-13`).
7. La baja **no borra datos**: dispara la evaluación de retención y anonimización de
   [[CU-07 Ejercer derechos sobre datos personales]], que decide qué se conserva por
   obligación legal y qué se seudonimiza.

### 8 · Recuperación de un operador — nunca por autoservicio

El camino de los pasos 4 y 5 sirve para el titular de una billetera. Para quien tiene un
rol de ámbito `GLOBAL` no alcanza, y el motivo es de una línea: **quien tomó el canal de
recuperación es exactamente quien va a usarlo**. Tomar el correo de un analista de
cumplimiento no debería costar más que tomar el correo de un analista de cumplimiento.

El restablecimiento exige **tres cosas y las tres quedan escritas**:

| # | Qué | Dónde queda |
| :-: | --- | --- |
| 1 | Token de un solo uso al canal verificado | [[token_verificacion]] `proposito='RECUPERACION_CONTRASENA'` + [[intento_validacion_token]] |
| 2 | **Verificación asistida**: prueba de vida y documento, igual que el flujo 4b | [[bitacora_evento]] con el operador que la condujo |
| 3 | **Aprobación de otra identidad** con `SEGURIDAD_ACCESO_RESTABLECER` | [[bitacora_evento]] con aprobador, motivo y hora |

Y **en la misma transacción** en que se escribe el hash nuevo, `R-SEG-11` corta todo lo
que colgaba de la credencial vieja:

- **todas** las [[sesion]] quedan revocadas con motivo —sin la excepción «salvo la que hizo
  el cambio» del paso 2, porque el caso que importa es que quien la hizo sea el atacante—;
- todos los [[dispositivo]] pierden `es_confiable`;
- los [[token_verificacion]] de refresco emitidos se invalidan.

Recuperar la contraseña **no reactiva el segundo factor**: son dos secretos distintos y se
restablecen por caminos distintos. Un atacante que consiguió la contraseña sigue sin el
TOTP, y `R-SEG-10` le cierra la sesión antes de abrirla.

### 9 · Reenrolamiento del segundo factor — a cuatro ojos y con enfriamiento

Perder el teléfono no puede costar el mismo trámite que olvidar la clave, porque el factor
es justamente lo que quedó en pie cuando la contraseña cayó. Dar de baja un [[factor_mfa]]
de operador y enrolar otro exige:

1. **otra identidad** con `SEGURIDAD_FACTOR_REINSCRIBIR` —nunca el propio titular—;
2. **causal escrita** y verificación asistida;
3. **enfriamiento**: durante el plazo que fija la política, el operador entra y trabaja,
   pero **no ejecuta acciones con `permiso.requiere_mfa`** —autorizar, aprobar, ejecutar,
   reversar, publicar, enviar, cerrar, leer datos de terceros—. El plazo se **guarda al
   inicio**, no se recalcula al consultar (`plazos-habiles`).

Es la ventana de enfriamiento del paso 5, aplicada del lado que puede mover plata ajena.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | La clave nueva coincide con una del historial | Rechazo `CLAVE_REUTILIZADA`; el historial existe justamente para esto |
| 4a | Se piden muchos tokens de recuperación seguidos | Límite por ventana y espera creciente; se registra como [[intento_autenticacion]] fallido y puede abrir [[incidente_seguridad]] |
| 4b | El canal de recuperación ya no es del usuario | La recuperación no procede por autoservicio: pasa a verificación asistida con prueba de vida y documento, y queda en [[bitacora_evento]] |
| 5a | Intenta retirar durante el enfriamiento | Rechazo con el tiempo restante explicado; **no se ofrece atajo**, ni siquiera por soporte |
| 6a | Pide la baja con un grupo en curso | Rechazo con la lista de grupos y la fecha estimada de cierre de cada uno |
| 6b | Pide la baja con saldo a favor | La baja se acepta pero exige elegir destino del saldo antes de la fecha efectiva |
| — | Rotación obligatoria de clave de operador | El sistema fuerza el cambio al iniciar sesión y no deja operar hasta completarlo |
| — | Compromiso confirmado de la cuenta | Se fuerza el cambio, se cierran sesiones y dispositivos, y se abre [[incidente_seguridad]] ([[CU-55 Gestionar un incidente de seguridad]]) |
| 8a | Un **operador** intenta recuperar por autoservicio | Rechazo `RECUPERACION_ASISTIDA_REQUERIDA`: el token solo habilita el trámite, no lo completa |
| 8b | La aprobación la intenta el propio titular | Rechazo `APROBACION_REQUERIDA`; la segregación no admite que nadie se restablezca a sí mismo (`R-SEG-07`, `R-SEG-11`) |
| 8c | El operador cambió su clave y quiere seguir en la sesión que la cambió | No se puede: `R-SEG-11` revoca **todas**. Vuelve a entrar con clave y TOTP |
| 9a | Reenrolamiento del factor sin segunda identidad | No procede; queda el pedido registrado con quién lo pidió |
| 9b | Operador en enfriamiento intenta autorizar un desembolso | Rechazo con el tiempo restante; **no hay atajo**, tampoco por el responsable de seguridad |

## Postcondiciones

- Toda clave que alguna vez rigió está en el historial: se puede probar que no se
  reutilizó, sin poder reconstruir ninguna.
- Ninguna baja deja obligaciones huérfanas ni destruye información con retención
  legal vigente.
- Ningún restablecimiento de operador ocurre sin una segunda persona registrada, y
  ninguno deja viva una sesión, un dispositivo confiable ni un refresco de la credencial
  anterior.

## Contrato · `openapi/identidad.yaml`

```ts
export const EntradaCambioCU09 = z.object({
  claveActual: z.string().min(8).max(128),
  claveNueva:  z.string().min(10).max(128),
}).strict()

export const EntradaRecuperacionCU09 = z.object({
  canal:  z.enum(['SMS', 'CORREO']),
  token:  z.string().length(6),
  claveNueva: z.string().min(10).max(128),
}).strict()

// El operador no completa su recuperación con el token: lo abre. La cierra otra
// identidad con SEGURIDAD_ACCESO_RESTABLECER, y el caso de verificación asistida
// que la respalda queda referenciado.
export const EntradaRecuperacionOperadorCU09 = z.object({
  token:              z.string().length(8),
  claveNueva:         z.string().min(12).max(128),
  verificacionAsistidaId: z.string().uuid(),
}).strict()

export const EntradaAprobacionRestablecimientoCU09 = z.object({
  solicitudId: z.string().uuid(),
  motivo:      z.string().min(10).max(160),
}).strict()

export const EntradaBajaCU09 = z.object({
  motivo:  z.string().min(5).max(160),
  destinoSaldo: z.object({
    tipo: z.enum(['CUENTA_BANCARIA', 'PUNTO_ATENCION']),
    referenciaId: z.string().uuid(),
  }).nullable(),
}).strict()

export const SalidaCU09 = z.object({
  sesionesCerradas:  z.number().int(),
  enfriamientoHasta: z.string().datetime().nullable(),
  solicitudBajaId:   z.string().uuid().nullable(),
  bloqueadaPorObligaciones: z.boolean(),
  obligacionesAbiertas: z.array(z.object({
    tipo: z.string(), referenciaId: z.string().uuid(), detalle: z.string(),
  })),
}).strict()

export const ErroresCU09 = {
  CREDENCIAL_INVALIDA:   'AP-CU09-01',
  CLAVE_DEBIL:           'AP-CU09-02',
  CLAVE_REUTILIZADA:     'AP-CU09-03',
  TOKEN_INVALIDO:        'AP-CU09-04',
  DEMASIADOS_INTENTOS:   'AP-CU09-05',
  BAJA_CON_OBLIGACIONES: 'AP-CU09-06',
  SALDO_SIN_DESTINO:     'AP-CU09-07',
  RECUPERACION_ASISTIDA_REQUERIDA: 'AP-CU09-08',
  APROBACION_REQUERIDA:            'AP-CU09-09',
  FACTOR_EN_ENFRIAMIENTO:          'AP-CU09-10',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `CREDENCIAL_INVALIDA` | La clave actual no coincide; el mensaje **no distingue** si el usuario existe |
| `CLAVE_DEBIL` | No cumple la política de complejidad o deriva de datos del titular |
| `CLAVE_REUTILIZADA` | Coincide con alguna de [[historial_credencial]] dentro de la ventana |
| `TOKEN_INVALIDO` | Vencido, ya usado o de otro canal |
| `DEMASIADOS_INTENTOS` | Se agotaron los intentos de la ventana; se responde con el tiempo de espera |
| `BAJA_CON_OBLIGACIONES` | Hay grupos activos, deuda, retenciones o bloqueos (`R-BIL-13`) |
| `SALDO_SIN_DESTINO` | Pide la baja con saldo a favor y no indicó a dónde va |
| `RECUPERACION_ASISTIDA_REQUERIDA` | Un operador intentó completar la recuperación por autoservicio |
| `APROBACION_REQUERIDA` | Falta la aprobación de otra identidad con `SEGURIDAD_ACCESO_RESTABLECER`, o la intentó el titular |
| `FACTOR_EN_ENFRIAMIENTO` | El operador reenroló su factor y la acción exige `requiere_mfa`; se responde con el tiempo restante |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `evaluarPoliticaDeClave(clave, datosTitular)` | Complejidad y derivación de datos personales; puro |
| Átomo | `calcularEnfriamiento(politica, ahora)` | Devuelve hasta cuándo se restringe; puro |
| Molécula | `CredencialRepositorio` | Hash con Argon2id, historial y comparación |
| Molécula | `TokenRecuperacionRepositorio` | Emisión, validación e intentos |
| Molécula | `EvaluadorDeObligaciones` | Enumera lo que impide la baja, con detalle legible |
| Átomo | `esOperador(rolesVigentes)` | El mismo de CU-04: ámbito `GLOBAL` vigente; puro |
| Molécula | `AprobacionRestablecimiento` | Verifica permiso, identidad distinta y deja el rastro |
| Organismo | `CU09RestablecerOperador` | Transacción: hash nuevo, corte de sesiones (`R-SEG-11`) y bitácora |
| Organismo | `CU09CambiarCredencial` · `CU09SolicitarBaja` | Transacción: escribe, cierra sesiones, bitácora y evento |
| Página | `POST /cuenta/clave` · `POST /cuenta/recuperacion` · `POST /cuenta/baja` · `POST /cuenta/recuperacion/operador` · `POST /cuenta/recuperacion/{id}/aprobacion` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `credencial.cambiada` | Aviso por todos los canales verificados | Sesión del titular |
| `credencial.recuperada` | Ventana de enfriamiento y aviso reforzado | — |
| `baja.solicitada` | Evaluación de retención y encadenamiento con el cierre de billetera | Sesión del titular |
| — | Trabajo diario que ejecuta las bajas con `fecha_efectiva` cumplida | — |

## Interfaz

- **App:** *Cuenta → Seguridad*: cambio de clave con medidor de fortaleza,
  recuperación por el canal verificado y, al fondo y sin ocultarla, la opción de dar
  de baja con la lista de lo que falta resolver.
- **Backoffice:** `acceso/recuperacion` para el operador que perdió la clave, y la
  bandeja de **recuperaciones asistidas** donde otra identidad aprueba o rechaza
  ([[Flujo de pantallas · backoffice administrador]] §2.0); tablero de bajas pendientes
  con su bloqueo.

## Restricciones aplicables

`R-SEG-02` · `R-SEG-03` · `R-SEG-07` · `R-SEG-10` · `R-SEG-11` · `R-BIL-13` · `R-AUD-01` · `R-AUD-04` · `R-CON-05`

## Evidencia que deja

[[credencial_acceso]] · [[historial_credencial]] · [[solicitud_baja]] ·
[[token_verificacion]] · [[intento_validacion_token]] · [[intento_autenticacion]] ·
[[sesion]] · [[bitacora_evento]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un usuario con dos sesiones abiertas
Cuando cambia su clave desde una de ellas
Entonces la otra sesión deja de ser válida
Y existe una fila en historial_credencial con la clave anterior

Dado un usuario que intenta poner una clave que ya usó
Cuando la envía
Entonces se rechaza con CLAVE_REUTILIZADA

Dado un usuario que recuperó su clave hace una hora
Cuando intenta retirar saldo
Entonces se rechaza indicando el tiempo restante del enfriamiento

Dado un usuario con un grupo activo
Cuando solicita la baja
Entonces la solicitud_baja queda con bloqueada_por_obligaciones en true
Y la respuesta enumera el grupo que lo impide

Dado un operador con rol de ámbito GLOBAL vigente
Cuando valida el token de recuperación y envía la clave nueva sin aprobación de otro
Entonces se rechaza con APROBACION_REQUERIDA
Y su credencial no cambió

Dado un operador con dos sesiones abiertas y su dispositivo marcado confiable
Cuando su credencial se restablece
Entonces ninguna sesion queda sin revocar
Y ningún dispositivo suyo queda con es_confiable en true
Y el motivo de revocación cita R-SEG-11

Dado un participante con dos sesiones abiertas
Cuando cambia su clave desde una de ellas
Entonces esa sesión sigue viva
Y el corte total de R-SEG-11 no se le aplica

Dado un operador que reenroló su segundo factor hace una hora
Cuando intenta autorizar un desembolso
Entonces se rechaza con FACTOR_EN_ENFRIAMIENTO indicando el tiempo restante
```

## Ver también

[[CU-04 Autenticar con MFA y registrar dispositivo]] · [[CU-07 Ejercer derechos sobre datos personales]] · [[CU-08 Asignar y revocar roles de operador]] · [[CU-16 Cerrar billetera y devolver saldo]] · [[CU-55 Gestionar un incidente de seguridad]] · [[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] · [[Seguridad]]
