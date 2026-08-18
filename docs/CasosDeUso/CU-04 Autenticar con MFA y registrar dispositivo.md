---
tags:
  - caso-uso
  - modulo/01-identidad-usuarios-y-seguridad
codigo: CU-04
criticidad: alta
actores: [Usuario, Sistema]
normas: [ASFI Seguridad de la Información, ISO/IEC 27001 A.5.15-A.5.18]
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

## Flujo principal

1. Se registra el intento en [[intento_autenticacion]] **antes** de conocer el
   resultado (así los fallidos también quedan).
2. Se valida la credencial contra [[credencial_acceso]] (hash con *pepper*, nunca
   la contraseña en claro).
3. Se identifica el [[dispositivo]] por huella; si es nuevo, se marca
   `es_confiable=false` y se exige factor adicional.
4. Se emite un desafío al [[factor_mfa]] activo; el código viaja como
   [[token_verificacion]] regido por su [[politica_token]] (vigencia, intentos
   máximos, longitud). Cada validación fallida se registra en
   [[intento_validacion_token]].
5. Verificado el factor, se abre [[sesion]] con IP, agente y expiración.
6. Para operaciones sensibles se repite el desafío y se guarda la referencia del
   factor usado en la operación (por ejemplo `orden_retiro.mfa_verificado`).

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | N intentos fallidos consecutivos | Se crea [[bloqueo_cuenta]] con motivo y vencimiento; se notifica al titular |
| 4a | Fuerza bruta sobre el token | La política corta por `intentos_maximos`; se emite alerta de seguridad |
| 3a | Dispositivo nuevo + retiro inmediato | [[evaluacion_antifraude]] eleva el puntaje y puede exigir revisión manual (`R-BIL-09`) |
| 5a | Sesión expirada o revocada | Toda operación en curso se rechaza; no hay continuidad silenciosa |

## Postcondiciones

- Toda operación sensible tiene sesión, dispositivo y factor identificables.
- Los intentos fallidos son analizables sin depender de logs de aplicación.

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
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `CREDENCIAL_INVALIDA` | Usuario o credencial no coinciden |
| `CUENTA_BLOQUEADA` | Hay bloqueo vigente por intentos fallidos |
| `FACTOR_REQUERIDO` | Dispositivo nuevo u operación sensible |
| `TOKEN_VENCIDO` | El código expiró según su política |
| `DEMASIADOS_INTENTOS` | Se agotaron los intentos del token |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `evaluarRiesgoDeAcceso` | Combina dispositivo, geo e historial; puro |
| Átomo | `politicaDeToken` | Vigencia, longitud e intentos según propósito |
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
- **Backoffice:** Auditoría de accesos por usuario, con dispositivos e IP.

## Restricciones aplicables

`R-SEG-01` · `R-SEG-02` · `R-SEG-03` · `R-SEG-09` · `R-BIL-09` · `R-AUD-02` ·
`R-AUD-09`

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
```

## Ver también

[[CU-08 Asignar y revocar roles de operador]] · [[CU-09 Cambiar credenciales y solicitar la baja]] · [[CU-11 Retirar saldo]] · [[CU-55 Gestionar un incidente de seguridad]] · [[Restricciones]]
