---
tags:
  - caso-uso
  - modulo/01-identidad-usuarios-y-seguridad
codigo: CU-04
criticidad: alta
actores: [Usuario, Sistema]
normas: [ASFI Seguridad de la Información, ISO/IEC 27001 A.5.15-A.5.18, ISO/IEC 27001 A.8.2, ISO/IEC 27001 A.8.5]
---

# CU-04 — Autenticar con MFA y registrar dispositivo

> **Objetivo.** Que cada acceso y cada operación sensible quede atada a una
> persona, un dispositivo y un factor, de forma que "yo no hice esa operación"
> tenga respuesta.

## Actores y disparador

- **Actor principal:** usuario.
- **Disparadores:** inicio de sesión; operación que supera
  `politica_billetera.requiere_mfa_desde`; alta de instrumento de fondeo; cambio
  de credencial.

## Precondiciones

1. El usuario existe y no tiene [[bloqueo_cuenta]] vigente.
2. **Si el usuario tiene un rol operativo vigente** —cualquier [[asignacion_rol]] sin
   revocar sobre un [[rol]] de ámbito `GLOBAL`— tiene además un [[factor_mfa]] de tipo
   `TOTP` **activo y confirmado**. Sin eso no hay sesión posible: lo impide `R-SEG-10`,
   no el código ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]).

## Flujo principal

1. Se registra el intento en [[intento_autenticacion]] **antes** de conocer el
   resultado (así los fallidos también quedan).
2. Se valida la credencial contra [[credencial_acceso]] (hash con *pepper*, nunca
   la contraseña en claro).
3. Se identifica el [[dispositivo]] por huella; si es nuevo, se marca
   `es_confiable=false` y se exige factor adicional.
   **El dispositivo de confianza no exime al operador**: para quien tiene rol de ámbito
   `GLOBAL` el segundo factor se exige en **todo** acceso, en su equipo de siempre y en
   cualquier otro. Lo que para el participante es una comodidad razonable —arriesga lo
   suyo— para el operador convierte el robo del equipo en el robo del rol.
4. Se emite un desafío al [[factor_mfa]] activo; el código viaja como
   [[token_verificacion]] regido por su [[politica_token]] (vigencia, intentos
   máximos, longitud). Cada validación fallida se registra en
   [[intento_validacion_token]].
5. Verificado el factor, se abre [[sesion]] con IP, agente y expiración.
6. Para operaciones sensibles se repite el desafío y se guarda la referencia del
   factor usado en la operación (por ejemplo `orden_retiro.mfa_verificado`). «Sensible»
   no es criterio de quien programa: es `permiso.requiere_mfa`, y `R-SEG-12` garantiza
   que esté puesto en toda acción de decisión irreversible (`AUTORIZAR`, `APROBAR`,
   `EJECUTAR`, `REVERSAR`, `PUBLICAR`, `ENVIAR`, `CERRAR`) y en la lectura de datos de
   terceros (`LEER_TERCEROS`).
7. **Cierre del lado del servidor.** Cerrar sesión revoca [[sesion]] y su familia de
   refresco; no alcanza con borrar el token del cliente. En el backoffice, además, la
   sesión caduca por **inactividad**: una pantalla abierta en un escritorio compartido no
   es una sesión viva.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | N intentos fallidos consecutivos | Se crea [[bloqueo_cuenta]] con motivo y vencimiento; se notifica al titular |
| 4a | Fuerza bruta sobre el token | La política corta por `intentos_maximos`; se emite alerta de seguridad |
| 3a | Dispositivo nuevo + retiro inmediato | [[evaluacion_antifraude]] eleva el puntaje y puede exigir revisión manual (`R-BIL-09`) |
| 5a | Sesión expirada o revocada | Toda operación en curso se rechaza; no hay continuidad silenciosa |
| 3b | **Operador** en un dispositivo ya marcado confiable | Se le exige el factor igual; `es_confiable` no habilita ningún atajo (`R-SEG-10`) |
| 4c | **Operador sin TOTP enrolado** | No entra: `FACTOR_NO_ENROLADO`. Se lo deriva al enrolamiento, que cierra [[CU-08 Asignar y revocar roles de operador]]. Una asignación de rol sin factor deja al operador afuera, y eso es lo correcto |
| 4d | Alguien intenta enrolar `SMS` o `WHATSAPP` como factor de un operador | Rechazo `FACTOR_NO_ADMISIBLE`: canales apagados ([[ADR-035 Canales por defecto]]) y expuestos al intercambio de SIM (`R-SEG-10`) |
| 6b | Acción con `permiso.requiere_mfa` sin desafío vigente | Se pide reautenticación por paso; la vigencia del desafío es corta y está atada a la operación, no a la sesión |

## Postcondiciones

- Toda operación sensible tiene sesión, dispositivo y factor identificables.
- Los intentos fallidos son analizables sin depender de logs de aplicación.
- **Ninguna sesión de operador existe sin dos factores.** No es una promesa del guard:
  es una fila que la base no deja insertar.

## Contrato · `openapi/identidad.yaml`

```ts
export const EntradaCU04 = z.object({
  telefonoE164: z.string(),
  credencial:   z.string().min(8).optional(),
  huellaDispositivo: z.string().max(128),
  factor:       z.object({ tipo: z.enum(['OTP','BIOMETRIA','TOTP']), valor: z.string() }).optional(),
}).strict()

export const SalidaCU04 = z.object({
  sesionId:   z.string().uuid(),
  expiraEn:   z.string().datetime(),
  requiereFactorAdicional: z.boolean(),
  dispositivoConfiable:    z.boolean(),
}).strict()

export const ErroresCU04 = {
  CREDENCIAL_INVALIDA: 'AP-CU04-01',
  CUENTA_BLOQUEADA: 'AP-CU04-02',
  FACTOR_REQUERIDO: 'AP-CU04-03',
  TOKEN_VENCIDO: 'AP-CU04-04',
  DEMASIADOS_INTENTOS: 'AP-CU04-05',
  FACTOR_NO_ENROLADO: 'AP-CU04-06',
  FACTOR_NO_ADMISIBLE: 'AP-CU04-07',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `CREDENCIAL_INVALIDA` | Usuario o credencial no coinciden |
| `CUENTA_BLOQUEADA` | Hay bloqueo vigente por intentos fallidos |
| `FACTOR_REQUERIDO` | Dispositivo nuevo u operación sensible |
| `TOKEN_VENCIDO` | El código expiró según su política |
| `DEMASIADOS_INTENTOS` | Se agotaron los intentos del token |
| `FACTOR_NO_ENROLADO` | El usuario tiene rol operativo y no tiene TOTP confirmado (`R-SEG-10`) |
| `FACTOR_NO_ADMISIBLE` | Se intentó usar `SMS` o `WHATSAPP` como factor de un operador (`R-SEG-10`) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `evaluarRiesgoDeAcceso` | Combina dispositivo, geo e historial; puro |
| Átomo | `politicaDeToken` | Vigencia, longitud e intentos según propósito |
| Átomo | `esOperador(rolesVigentes)` | Verdadero si alguno es de ámbito `GLOBAL`; puro, sin consultar nada |
| Átomo | `exigeSegundoFactor(perfil, dispositivo, permiso)` | Para operador devuelve siempre `true`; puro |
| Molécula | `CredencialRepositorio` | Hash con pepper, nunca la credencial en claro |
| Molécula | `SesionRepositorio` | Alta y revocación de sesiones |
| Molécula | `TokenAdaptador` | Emisión y validación del segundo factor |
| Organismo | `CU04Autenticar` | Registra el intento, valida y abre sesión en una transacción |
| Página | `POST /sesiones` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `sesion.iniciada` | Registro de dispositivo y notificación si es nuevo | Ninguno: ruta pública |
| `acceso.bloqueado` | Bloqueo de cuenta y aviso al titular | — |

## Interfaz

- **App:** Ingreso con teléfono y PIN o biometría; el dispositivo nuevo pide siempre segundo factor.
- **Backoffice:** ingreso en dos pasos —credencial y TOTP— en `acceso/ingreso` y
  `acceso/desafio` ([[Flujo de pantallas · backoffice administrador]] §2.0), sin atajo
  biométrico y sin «recordar este equipo». Y la auditoría de accesos por usuario, con
  dispositivos e IP.

## Restricciones aplicables

`R-SEG-01` · `R-SEG-02` · `R-SEG-03` · `R-SEG-09` · `R-SEG-10` · `R-SEG-12` ·
`R-BIL-09` · `R-AUD-02` · `R-AUD-09`

## Evidencia que deja

[[intento_autenticacion]] · [[sesion]] · [[dispositivo]] · [[factor_mfa]] ·
[[token_verificacion]] · [[intento_validacion_token]] · [[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un usuario con MFA activo
Cuando inicia sesión desde un dispositivo desconocido
Entonces se le exige un factor adicional
Y queda registrado el dispositivo con es_confiable = false

Dado un retiro que supera el umbral de MFA
Cuando el usuario no completa el desafío
Entonces la orden_retiro no se crea

Dado cinco intentos fallidos consecutivos
Cuando ocurre el sexto
Entonces existe un bloqueo_cuenta vigente para ese usuario

Dado un operador con rol de ámbito GLOBAL vigente y su dispositivo marcado confiable
Cuando inicia sesión
Entonces se le exige el segundo factor igual
Y no existe camino que lo omita

Dado un operador cuyo factor TOTP está desactivado
Cuando se intenta abrir su sesion
Entonces la transacción falla por R-SEG-10
Y la respuesta es FACTOR_NO_ENROLADO

Dado un operador
Cuando se intenta enrolarle un factor de tipo SMS
Entonces la transacción falla por R-SEG-10
Y para un participante el mismo factor SMS se acepta

Dado un permiso cuya acción es APROBAR
Cuando se lo intenta crear con requiere_mfa en false
Entonces la restricción ck_permiso_decision_exige_mfa lo rechaza
```

## Ver también

[[CU-08 Asignar y revocar roles de operador]] · [[CU-09 Cambiar credenciales y solicitar la baja]] · [[CU-11 Retirar saldo]] · [[CU-55 Gestionar un incidente de seguridad]] · [[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] · [[Seguridad]] · [[Restricciones]]
