---
tags:
  - arquitectura
  - adr
titulo: "ADR-010 — Autenticación y sesión"
estado: superada por ADR-024
fecha: 2026-08-13
---

# ADR-010 — Autenticación y sesión

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-024 Autenticación y sesión distribuida]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

Dos clientes con superficies de ataque distintas —la app del participante y el
backoffice de cumplimiento— consumen la misma API. El modelo exige MFA y **registro
de dispositivo de confianza** (CU-04), y la protección real de los datos la dan las
políticas de fila, que necesitan `app.usuario_id` y `app.rol` en la conexión
([[ADR-007 Sesión, RLS y pooling]]).

El riesgo concreto: un endpoint nuevo que nadie recuerda proteger, y un token que
nunca caduca en un teléfono perdido.

## Decisión

**Guard global default-deny con JWT, y el token traducido a contexto de sesión en
cada transacción.**

- Todo endpoint exige autenticación salvo marca explícita de público.
- **App**: bearer en almacenamiento seguro del dispositivo.
  **Backoffice**: cookie `httpOnly` + `Secure` + `SameSite` estricto, con CSRF y CORS
  por lista blanca.
- Access token de minutos; **refresh rotado** en cada uso, con detección de
  reutilización que revoca la familia completa.
- Credenciales y PIN con **Argon2id**; versión de credenciales en el token para poder
  invalidar sesiones al cambiarla.
- El borde traduce: `JWT → {usuarioId, rol, dispositivoId} → SET LOCAL`.

## Motivo

**Porque el default decide la seguridad real.** Con guard opt-in, la superficie
expuesta crece con cada endpoint que alguien olvida marcar; con default-deny, el
olvido produce un `401`, que es un bug visible y barato.

**Porque las dos superficies no se protegen igual.** El navegador tiene CSRF y el
teléfono no; el teléfono tiene almacenamiento seguro y el navegador no. Unificar por
comodidad debilita una de las dos.

**Porque el token sin contexto no protege nada.** Autenticar y después consultar sin
`SET LOCAL` deja los datos a merced de un `WHERE` que alguien puede olvidar. La
traducción a contexto es lo que convierte la autenticación en autorización efectiva.

**Porque un refresh de larga vida sin rotación es una llave permanente.** Con
rotación, el robo se detecta al primer uso duplicado.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Sesión con estado en servidor para ambos clientes** | Obliga a almacenamiento compartido y complica el escalado horizontal sin ventaja sobre refresh rotado y revocable. |
| **Cookie para todo** | En la app significa manejar cookies fuera del navegador, con peor ergonomía y sin ganancia. |
| **Bearer para todo** | En el backoffice deja el token accesible a scripts del navegador. |
| **Proveedor externo de identidad (IdP)** | Razonable a futuro; hoy agrega dependencia crítica y saca el KYC del sistema que debe evidenciarlo. Volver a evaluar si aparece federación con un banco. |
| **Guard opt-in** | Un endpoint sin marcar queda abierto. Inaceptable con dinero. |

## Consecuencias

**A favor**

- Un endpoint nuevo nace protegido.
- El robo de refresh se detecta y se corta.
- Cambiar una credencial cierra sesiones abiertas.

**En contra**

- Dos configuraciones de cliente que mantener y probar por separado.
- La rotación de refresh exige manejar carreras: dos peticiones simultáneas con el
  mismo refresh no pueden invalidar la sesión legítima. Se resuelve con ventana de
  gracia corta y prueba explícita.
- Revocar exige estado en base: el token deja de ser puramente autocontenido.

## Cómo se verifica

- [ ] Prueba: endpoint nuevo sin decorador ⇒ `401`.
- [ ] Prueba: refresh reutilizado ⇒ familia revocada.
- [ ] Prueba: cambio de credencial ⇒ las sesiones previas dejan de servir.
- [ ] Prueba negativa de permisos sobre el recurso concreto, no solo por rol.
- [ ] Ningún token en logs, trazas ni parámetros de consulta.

## Ver también

[[ADR-007 Sesión, RLS y pooling]] · `autenticacion-jwt` · `roles-y-accesos` ·
`kyc-onboarding` · [[Flujo de una transacción]]
