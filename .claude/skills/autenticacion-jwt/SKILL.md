---
name: autenticacion-jwt
description: "Autenticar y autorizar en la API de AportaYa: guard global default-deny, JWT en bearer o cookie, refresh rotado y revocable, contraseñas y PIN con Argon2id, rate limit de login y MFA, y cómo el token se convierte en contexto de sesión para RLS. Úsala al crear cualquier endpoint, al tocar login, refresh, logout, recuperación o permisos."
---

# Autenticación y autorización de la API

Esta skill cubre el **borde HTTP**. Quién es la persona y qué nivel de conocimiento
del cliente tiene lo maneja `kyc-onboarding`; qué puede ver una vez dentro lo decide
`seguridad-sesion-rls`.

## Default-deny

- Guard global: **todo endpoint exige autenticación** salvo que esté marcado como
  público con un decorador explícito.
- Un endpoint nuevo sin decisión consciente queda protegido, no abierto.
- Autenticación y autorización son cosas distintas: `401` es "no sé quién sos",
  `403` es "sé quién sos y no podés".
- Se valida el actor **y su estado**: cuenta bloqueada, billetera cerrada, dispositivo
  no reconocido o versión de credenciales revocada ⇒ el token vale nada.

## Del token al contexto de sesión

El token no protege datos por sí solo. Lo que protege es la política de fila, y para
eso el borde traduce:

```
JWT válido → { usuarioId, rol, dispositivoId } → conContexto → SET LOCAL app.usuario_id / app.rol
```

Sin esa traducción, la consulta corre sin contexto y las políticas no aplican
([[ADR-021 Sesión, RLS y pooling]]). Un endpoint que consulta sin `conContexto` es
un defecto de seguridad, no de estilo.

## JWT

| Regla | Detalle |
| --- | --- |
| Access token corto | Minutos, no horas |
| Refresh rotado | Cada uso emite uno nuevo e invalida el anterior |
| Reutilización de refresh | Se detecta y **revoca toda la familia**: es señal de robo |
| Carga mínima | `sub`, `rol`, `dispositivo`, versión de credenciales. Nada de datos personales |
| `issuer` y `audience` | Explícitos y validados |
| Algoritmo | Lista blanca explícita; nunca aceptar el que declare el token |
| Rotación de claves | Con `kid`, sin invalidar sesiones vivas innecesariamente |
| Nunca en query params ni en logs | Ni completo ni truncado de forma reversible |

**App móvil**: bearer con almacenamiento seguro del dispositivo. **Backoffice**:
cookie `httpOnly`, `Secure`, `SameSite` estricto, con protección CSRF y CORS por lista
blanca. Son dos productos con superficies distintas; no se unifica por comodidad.

## El operador no es un usuario más

Para quien tiene un rol de ámbito `GLOBAL` las reglas de arriba son el piso, no el
techo ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]):

| Regla | Quién la hace cumplir |
| --- | --- |
| Segundo factor en **todo** acceso; el dispositivo confiable **no exime** | `R-SEG-10` |
| El factor es **TOTP**; `SMS` y `WHATSAPP` se rechazan | `R-SEG-10` |
| Recuperar la clave exige verificación asistida **y** aprobación de otra identidad | aplicación (hueco S-7) |
| Cambiar la credencial revoca **todas** las sesiones, la confianza de los dispositivos y los refrescos | `R-SEG-11` |
| Toda acción irreversible o de lectura de terceros exige reautenticación por paso | `R-SEG-12` |

No hace falta que las recuerdes al escribir el endpoint: la base rechaza la fila. Lo
que sí tenés que hacer es **traducir ese rechazo** al código de error del CU
(`FACTOR_NO_ENROLADO`, `FACTOR_NO_ADMISIBLE`, `APROBACION_REQUERIDA`) y no dejar que
salga crudo (`errores-api`).

## Contraseñas, PIN y códigos

- **Argon2id** con parámetros configurables; nunca hash rápido de propósito general.
- El mismo tratamiento para el PIN de la billetera que para la contraseña.
- El error de login **no revela** si falló el usuario o la credencial.
- Códigos OTP: de un solo uso, con vencimiento corto, invalidados al usarse, y con
  límite de intentos por código.
- Cambio de credencial ⇒ se incrementa la versión y **caen las sesiones abiertas**,
  salvo la que hizo el cambio.

## Rate limit y abuso

| Endpoint | Límite |
| --- | --- |
| Login | Por usuario **y** por origen, con retroceso creciente |
| Recuperación de credencial | Estricto; respuesta idéntica exista o no la cuenta |
| Verificación MFA / OTP | Por sesión y por usuario |
| Operaciones de dinero | Límite propio, además de los límites operativos del catálogo |

El bloqueo por abuso se registra como evento y es consultable: un usuario que reclama
"no puedo entrar" se responde con evidencia.

## Autorización

- **RBAC** para el rol general; políticas por atributo cuando el dominio lo exige
  (ser miembro del grupo, ser el titular de la billetera, ser el oficial asignado).
- Se verifica **contra el recurso concreto**, no solo contra el rol: que alguien sea
  participante no lo autoriza sobre *cualquier* grupo.
- Nunca se confía en un identificador del cliente para determinar propiedad.
- La segregación de funciones del modelo se respeta: quien registra no aprueba.
- Toda regla de autorización tiene **prueba negativa**; el camino feliz no prueba nada
  de seguridad.

## Sesiones y dispositivos

Registro de dispositivo de confianza y MFA vienen de CU-04: el token lleva el
dispositivo, y una operación sensible desde un dispositivo no reconocido exige
verificación adicional. Cerrar sesión revoca del lado del servidor, no solo borra el
token del cliente.

## Antipatrones

- Guard opcional "porque este endpoint es interno".
- Verificar permisos en el controlador y no sobre el recurso.
- Refresh token de larga vida sin rotación ni revocación.
- Devolver mensajes distintos para usuario inexistente y credencial incorrecta.
- Meter el rol en el token y no volver a verificar el estado de la cuenta.
- Confiar en que la interfaz oculta el botón.

## Ver también

`seguridad-sesion-rls` · `roles-y-accesos` · `seguridad-aplicacion` · `kyc-onboarding` · `errores-api` ·
`contratos-api` · `back-spring` · `observabilidad` ·
`docs/Arquitectura/ADR-010 Autenticación y sesión.md`
