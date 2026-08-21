---
tags:
  - arquitectura
  - adr
  - seguridad
titulo: "ADR-038 — Acceso administrativo: segundo factor obligatorio y recuperación asistida"
estado: aceptada
fecha: 2026-08-19
---

# ADR-038 — Acceso administrativo · segundo factor y recuperación asistida

> Precisa, para los **operadores del backoffice**, lo que
> [[ADR-024 Autenticación y sesión distribuida]] resolvió para cualquier usuario, y
> corrige la línea suelta de [[Flujo funcional · usuario administrador]] §1 que decía
> *«MFA obligatorio»* sin decir quién lo hace cumplir.

## Contexto

Un participante que pierde su cuenta pierde **su** plata. Un operador que pierde su
cuenta entrega la base de clientes entera, la bandeja de reclamos, el tarifario, la
cola de desembolsos o la facultad de reportar a la UIF. No es el mismo riesgo y hasta
hoy tenía el mismo tratamiento.

Lo que había:

| Dónde | Qué decía | Qué faltaba |
| --- | --- | --- |
| [[CU-04 Autenticar con MFA y registrar dispositivo]] | El segundo factor se exige en dispositivo nuevo o en operación sensible | Un operador en su equipo de siempre entra **con un solo factor** |
| `permiso.requiere_mfa` | El factor se exige **por permiso** | Cubre la acción, no el acceso: leer la base de clientes no necesita permiso marcado |
| [[CU-09 Cambiar credenciales y solicitar la baja]] | Recuperación por autoservicio al canal verificado | Tomar el correo de un operador = tomar su rol, sin que intervenga nadie |
| [[Flujo funcional · usuario administrador]] §1 | «MFA obligatorio (escritorio, sin atajo biométrico)» | Una frase en un documento de recorrido: sin restricción, sin política, sin prueba |
| `politica_token` (semilla `SEGUNDO_FACTOR`) | Canales `SMS,WHATSAPP,APP_AUTENTICADORA` | SMS y WhatsApp están **apagados** por [[ADR-035 Canales por defecto]], y el intercambio de SIM es el vector más barato contra un operador |

La norma no deja margen: ASFI —Reglamento para la Gestión de Seguridad de la
Información, al que remite el de riesgo operativo— exige **autenticación fuerte y
trazabilidad de sesión** para el acceso privilegiado, e ISO/IEC 27001:2022 lo pide en
**A.5.15** (control de acceso), **A.5.17** (información de autenticación), **A.8.2**
(derechos de acceso privilegiado) y **A.8.5** (autenticación segura).

## Decisión

**Ningún acceso al backoffice existe sin dos factores, el segundo factor del operador
es una aplicación autenticadora, y su recuperación nunca es autoservicio. Lo hace
cumplir la base de datos, no el guard ni la pantalla.**

### 1 · Dos factores en **todo** acceso, sin excepción de dispositivo

El dispositivo de confianza (`dispositivo.es_confiable`) **no exime al operador**. Lo
que para el participante es una comodidad razonable —CU-04 paso 3— para quien tiene un
rol de ámbito `GLOBAL` es la puerta que borra el segundo factor de la ecuación.

- Se abre [[sesion]] solo si el usuario tiene un [[factor_mfa]] **activo y confirmado**
  y el desafío de esta sesión se validó. Lo verifica `R-SEG-10`.
- La sesión del backoffice caduca por **inactividad**, no solo por vencimiento: una
  pantalla abierta en un escritorio compartido no es una sesión viva.

### 2 · El factor del operador es TOTP, no un mensaje

| Factor | Participante | Operador |
| --- | :-: | :-: |
| `TOTP` (aplicación autenticadora) | ✅ | ✅ **obligatorio como principal** |
| `RESPALDO` (códigos de un solo uso) | ✅ | ✅ para no quedar afuera |
| `SMS` · `WHATSAPP` | ✅ mientras el canal esté contratado | ❌ **nunca** |

Coherente con [[ADR-035 Canales por defecto]]: los canales que están apagados no pueden
sostener el acceso privilegiado de nadie. Y el intercambio de SIM no se defiende con
una política de contraseñas.

### 3 · Reautenticación por paso para lo que `requiere_mfa`

Entrar con dos factores no autoriza para siempre. Un permiso con
`permiso.requiere_mfa = true` exige un desafío **nuevo**, de vigencia corta, atado a la
operación concreta. La segregación de funciones de
[[CU-08 Asignar y revocar roles de operador]] se apoya en esto: el que autoriza y el
que ejecuta prueban ser ellos **en el momento**, no al empezar el turno.

### 4 · La recuperación de un operador la aprueba otra persona

La contraseña de un operador se recupera con **tres cosas**, no con una:

1. token de [[token_verificacion]] con propósito `RECUPERACION_CONTRASENA` al canal
   verificado;
2. **verificación asistida** —prueba de vida y documento— porque el canal pudo caer con
   la cuenta;
3. **aprobación de una identidad distinta** con permiso `SEGURIDAD_ACCESO_RESTABLECER`,
   que no puede ser el propio titular.

Lo tercero **hoy lo sostiene la aplicación, no el motor**, y hay que decirlo: ninguna
tabla guarda al aprobador de un restablecimiento —`token_verificacion` no tiene
`aprobado_por`—, así que la base no puede rechazar una autoaprobación como sí rechaza la
autoasignación de rol (`R-SEG-07`). La aprobación queda en [[bitacora_evento]]: alcanza
para auditar, no para impedir. Es un hueco declarado, no un olvido.

Recuperar la contraseña **no reactiva el segundo factor**: son dos secretos y se
restablecen por caminos separados. Y el cambio **revoca todas** las sesiones y quita la
confianza de todos los dispositivos del operador, sin la excepción «salvo la que hizo el
cambio» que sí tiene el participante.

### 5 · El reenrolamiento del factor es un procedimiento aparte, a cuatro ojos

Perder el teléfono no puede costar el mismo trámite que olvidar la clave. Dar de baja un
`factor_mfa` de un operador y enrolar otro exige otra identidad, causal escrita y
**enfriamiento**: durante ese plazo la cuenta entra, pero no ejecuta acciones marcadas
`requiere_mfa`. Es el mismo razonamiento de la ventana de enfriamiento de CU-09 §5,
aplicado al lado que puede mover plata ajena.

### 6 · Lo hace cumplir la base

| Restricción | Qué impide |
| --- | --- |
| `R-SEG-10` | Abrir sesión de un usuario con rol operativo vigente sin factor MFA confirmado |
| `R-SEG-11` | Que el cambio de credencial de un operador deje viva una sesión, un dispositivo confiable o un refresco emitido |
| `R-SEG-12` | Que un rol de ámbito `GLOBAL` reciba un permiso de escritura sin `requiere_mfa` |

La UI puede esconder el botón y el guard puede olvidarse; el motor no.

## Motivo

- **El daño no es simétrico.** El control se dimensiona por lo que protege, y un
  operador protege datos y dinero de terceros (ISO/IEC 27001 A.8.2).
- **El dispositivo de confianza es un factor prestado.** Confiar en el equipo convierte
  el robo del equipo en el robo del rol.
- **El canal de recuperación pertenece al mismo atacante.** Quien toma el correo toma la
  recuperación; por eso la recuperación privilegiada tiene que salir de ese canal y pasar
  por otra persona.
- **Una regla que vive en un documento de recorrido no es una regla.** Vive en una
  restricción con prueba de rechazo, o no existe (`restriccion`,
  `definicion-de-terminado`).

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| SMS o WhatsApp como segundo factor del operador | Intercambio de SIM y dependencia de un canal que [[ADR-035 Canales por defecto]] tiene apagado |
| MFA solo en operaciones sensibles (lo que ya hacía `permiso.requiere_mfa`) | Leer la base de clientes no lleva permiso marcado y es la mitad del daño posible |
| Dispositivo de confianza también para operadores | Convierte el robo del equipo en el robo del rol; es exactamente el atajo que este ADR cierra |
| Recuperación por autoservicio con token más largo | Un token más largo no arregla que el canal esté comprometido: el problema no es la entropía |
| Federar el acceso con un proveedor de identidad externo | Fuera de alcance hoy y agrega un tercero crítico ([[ADR-033 Puertos y adaptadores]]); se reevalúa cuando exista contrato y evaluación de tercero |
| Llave física (FIDO2/WebAuthn) obligatoria | Es lo más fuerte, pero exige comprar y repartir llaves. Queda como camino de mejora sobre TOTP, no como requisito de arranque |

## Consecuencias

- **Alta de operador con un paso más**: [[CU-08 Asignar y revocar roles de operador]] no
  termina en la asignación; termina cuando el operador enroló su TOTP y guardó sus
  códigos de respaldo. Una asignación sin factor deja al operador sin poder entrar —y eso
  es lo correcto, no un defecto.
- **Soporte deja de poder «desbloquear rápido»**: no hay atajo, y el que pida uno queda
  registrado pidiéndolo.
- **Dos permisos nuevos** en el catálogo: `SEGURIDAD_ACCESO_RESTABLECER` y
  `SEGURIDAD_FACTOR_REINSCRIBIR`, ninguno de los cuales cuelga del rol que lo usa sobre
  sí mismo.
- **Hueco declarado — el aprobador no tiene dónde vivir.** `token_verificacion` no guarda
  `aprobado_por`, así que la regla «no te restablecés a vos mismo» la sostiene la
  aplicación y la prueba la bitácora. Cerrarla en el motor exige una columna nueva:
  cambio de modelo (`boveda-modelo` + ADR), no decisión de implementación.
- **Hueco declarado — el enfriamiento del reenrolamiento tampoco.** El plazo de §5 no
  tiene columna donde guardarse; se declara junto con el anterior o se parametriza como
  catálogo (`semillas-catalogos`). Lo que **no** se hace es escribir el número dentro del
  código.
- **Hueco declarado — política de token sin perfil.** `politica_token` tiene una fila por
  `proposito` y `vigente_desde`: **no puede** expresar «para operadores, 8 minutos y solo
  aplicación autenticadora; para participantes, 15 minutos». Endurecer la fila global
  castigaría al participante. Se declara como hueco: la diferenciación exige una columna
  nueva y eso es cambio de modelo (`boveda-modelo` + ADR), no decisión de implementación.
  Mientras tanto, **el límite del operador lo impone `R-SEG-10`/`R-SEG-11`**, que no
  dependen de esa tabla.

## Cómo se verifica

```bash
python3 scripts/verificar_boveda.py        # el ADR está en el índice y los CU citan las restricciones
python3 scripts/generar_ddl.py             # R-SEG-10/11/12 llegan a sql/40_reglas/restricciones.sql
python3 scripts/verificar_seguridad.py     # el estándar de seguridad, sobre el repositorio
psql -f sql/aplicar.sql                    # las tres restricciones aplican sobre PostgreSQL 16 real
```

Y en pruebas, la evidencia que no admite interpretación:

- **Prueba de rechazo de `R-SEG-10`**: insertar una `sesion` de un usuario con
  `asignacion_rol` vigente de ámbito `GLOBAL` y sin `factor_mfa` confirmado → la
  transacción falla.
- **Prueba de rechazo de `R-SEG-11`**: cambiar el `hash_contrasena` de un operador con
  dos sesiones abiertas y un dispositivo confiable → quedan cero sesiones vivas, cero
  dispositivos confiables y el motivo citando la restricción; hacer lo mismo sobre un
  participante **no** dispara el corte.
- **Prueba de rechazo de `R-SEG-12`**: asociar a un rol `GLOBAL` un permiso de acción
  `ESCRIBIR` con `requiere_mfa = false` → falla.
- Los `gherkin` de [[CU-04 Autenticar con MFA y registrar dispositivo]] y
  [[CU-09 Cambiar credenciales y solicitar la baja]] para el camino del operador.

## Ver también

[[ADR-024 Autenticación y sesión distribuida]] · [[ADR-030 Revocación de sesión y validación de respaldo]] ·
[[ADR-035 Canales por defecto]] · [[Seguridad]] · [[Restricciones]] ·
[[CU-04 Autenticar con MFA y registrar dispositivo]] · [[CU-08 Asignar y revocar roles de operador]] ·
[[CU-09 Cambiar credenciales y solicitar la baja]] · [[Flujo funcional · usuario administrador]] ·
`autenticacion-jwt` · `roles-y-accesos` · `seguridad-aplicacion`
