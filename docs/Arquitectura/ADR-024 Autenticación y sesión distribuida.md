---
tags:
  - arquitectura
  - adr
titulo: "ADR-024 — Autenticación y sesión entre catorce servicios"
estado: aceptada
fecha: 2026-08-16
---

# ADR-024 — Autenticación y sesión distribuida

> Supera a [[ADR-010 Autenticación y sesión]], que resolvía la autenticación dentro
> de un único proceso de API.

## Contexto

La decisión anterior tenía un solo verificador de credenciales y un solo lugar donde
mirar si una sesión seguía viva. Con catorce servicios ([[ADR-014 Arquitectura de servicios]]) hay catorce puntos donde hay que decidir si esta petición está
autorizada, y trece de ellos no tienen acceso a las tablas de identidad
([[ADR-017 Propiedad de datos por servicio]]).

Lo que hay que conservar está en la bóveda y en las restricciones: refresco rotado y
revocable (`R-SEG-09`), MFA y dispositivo de confianza (CU-04), contraseñas y PIN con
Argon2id, límite de tasa en autenticación, y denegación por omisión.

## Decisión

**`identidad` es el único emisor. Los otros trece son servidores de recurso que
validan la firma localmente contra un JWKS publicado.**

### Los dos tokens

| Token | Vida | Dónde vive | Qué lleva |
| --- | --- | --- | --- |
| **Acceso** — JWT firmado con RS256 | Corta (15 min) | Memoria del cliente / cabecera `Authorization` | `sub`, `rol`, `permisos`, `nivel_diligencia`, `dispositivo`, `exp`, `jti` |
| **Refresco** — opaco, aleatorio | Larga (30 días) | Cookie `HttpOnly`, `Secure`, `SameSite=Strict` | Nada. Es una referencia a una fila |

**El de acceso se valida sin consultar a nadie**: firma asimétrica, clave pública en
`/.well-known/jwks.json` de `identidad`, cacheada por cada servicio. Es lo que
permite que trece servicios autoricen sin poder leer la tabla de sesiones.

**El de refresco se valida siempre contra la base**, en `identidad`, y **se rota en
cada uso**. Reusar uno ya rotado revoca la familia entera y todas sus sesiones
(`R-SEG-09`). Un refresco es un secreto de un solo uso, no una credencial de larga
vida.

### El precio del JWT, y cómo se paga

Un token firmado no se puede revocar: sigue siendo válido hasta que vence. Con 15
minutos de vida, esa es la ventana de exposición de una sesión revocada. Se acota
así:

| Situación | Respuesta |
| --- | --- |
| Cierre de sesión normal | Se revoca el refresco. El acceso muere solo en ≤ 15 min |
| **Revocación urgente** (cuenta bloqueada, orden de autoridad, fraude) | `identidad` publica el `jti` en un tema de Kafka y cada servicio lo mantiene en una lista de rechazo en memoria, con TTL igual al del token |
| Cambio de contraseña o de rol | Igual que la revocación urgente |

La lista de rechazo es **pequeña y efímera por diseño** —solo tokens vivos revocados—
así que no es un almacén de sesiones disfrazado. Un servicio que arranca y todavía no
recibió el tema falla del lado seguro: pide validación a `identidad` hasta estar al
día.

### La autorización es local y por omisión deniega

Cada servicio declara el permiso que exige cada endpoint. Sin permiso declarado, la
ruta **no se sirve**: no es que quede abierta, es que el arranque falla. Los permisos
viajan en el token; el catálogo de qué permiso exige cada cosa vive en el servicio
que expone la operación, porque es el único que sabe qué está protegiendo.

Las **únicas rutas sin sesión** son los prefijos `/publico` y `/verificar` de
`transparencia`. Que tengan un solo dueño es lo que permite que la prueba de barrido
«toda ruta exige sesión» tenga exactamente una excepción declarada en lugar de una
lista que crece sola.

### Lo que no cambia

Argon2id para contraseña y PIN con parámetros por configuración; MFA y registro de
dispositivo según CU-04; límite de tasa en login por identidad y por IP; y el
contexto de sesión que llega hasta la política de fila
([[ADR-021 Sesión, RLS y pooling]]).

## Motivo

**La validación local es el requisito que decide todo lo demás.** Trece servicios no
pueden preguntarle a `identidad` en cada petición: sería un fallo único y un salto de
red por operación. La firma asimétrica es la única forma de autorizar sin consultar.

**Un JWT corto más una lista de rechazo por evento** es el punto medio honesto entre
«no se puede revocar nunca» y «hay que consultar siempre». La ventana es de minutos y
está acotada por escrito, que es distinto de estar ignorada.

**El refresco opaco y rotado** es lo que hace que el robo de un token de larga vida
sea detectable: el segundo uso delata al primero.

**RS256 y no HS256** porque con clave simétrica los catorce servicios tendrían que
conocer el secreto de firma, y entonces cualquiera de ellos podría emitir tokens.
Con clave asimétrica solo `identidad` firma.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Sesión con estado consultada en cada petición** | Revocación inmediata, y un salto de red y un fallo único por cada operación de cada servicio. |
| **JWT con HS256** | Obligaría a repartir el secreto de firma entre catorce servicios: cualquiera podría emitir un token de administrador. |
| **Tokens opacos validados por introspección** (OAuth 2 clásico) | Revocación inmediata y una llamada a `identidad` por petición. Es la alternativa que vuelve si la ventana de 15 minutos resulta inaceptable para el supervisor. |
| **El gateway autentica y los servicios confían** | Cómodo, y convierte la red interna en perímetro de confianza: quien la alcance suplanta a cualquiera. Rechazado también en [[ADR-021 Sesión, RLS y pooling]]. |
| **Keycloak u otro proveedor de identidad externo** | Resuelve bien esto y agrega un sistema entero que operar, además de sacar de la bóveda un modelo de identidad que ya está especificado con KYC, niveles de diligencia y PEP. |
| **Tokens de acceso largos** | Elimina el refresco y hace la revocación imposible. |

## Consecuencias

**A favor**

- Trece servicios autorizan sin acceder a las tablas de identidad ni a la red.
- El robo de un refresco es detectable por la rotación.
- Un servicio comprometido no puede emitir tokens.

**En contra, y hay que asumirlo**

- **Una sesión revocada sigue siendo válida hasta 15 minutos**, salvo que se use la
  revocación urgente. Está escrito, acotado y es un riesgo aceptado, no un descuido.
- **La rotación de la clave de firma tiene que llegar a catorce cachés de JWKS.** Se
  hace con dos claves activas y un período de solapamiento.
- El token crece con los permisos. Si crece demasiado, se pasa a permisos por
  referencia y se paga una consulta; se mide antes de decidir.
- `identidad` es un fallo único para *iniciar sesión*, aunque no para *usar* el
  sistema: los tokens vigentes siguen funcionando si `identidad` se cae. Es una
  propiedad valiosa y hay que probarla.

## Cómo se verifica

- [ ] Toda ruta registrada responde `401` sin token y `403` con rol insuficiente.
      Excepción única: los prefijos `/publico` y `/verificar`.
- [ ] Un endpoint sin permiso declarado **impide el arranque** del servicio.
- [ ] Un token firmado con otra clave es rechazado por los catorce.
- [ ] Reusar un refresco ya rotado revoca la familia y todas sus sesiones.
- [ ] Un `jti` revocado por evento deja de ser aceptado en menos de un segundo.
- [ ] Con `identidad` caído, un token vigente sigue siendo aceptado por los demás.
- [ ] Límite de tasa activo en login y en operaciones de dinero.

## Ver también

[[ADR-010 Autenticación y sesión]] · [[ADR-014 Arquitectura de servicios]] · [[ADR-021 Sesión, RLS y pooling]] · [[ADR-022 Comunicación entre servicios]] · [[Cumplimiento]] · [[_Arquitectura]]
