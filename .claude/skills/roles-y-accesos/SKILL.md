---
name: roles-y-accesos
description: "Administrar quién puede hacer qué en AportaYa: catálogo de roles y permisos, ámbito de plataforma o de grupo, asignaciones con vigencia, revocación que cierra sesiones, segregación de funciones y accesos de emergencia. Úsala al crear un permiso nuevo, al dar de alta o baja a un operador, al decidir qué permiso exige un endpoint, o cuando alguien pida 'dame acceso a esto un rato'."
---

# Administrar roles y accesos

`autenticacion-jwt` responde **quién sos**. Esta skill responde **qué podés hacer**.
Son dos problemas distintos y se rompen por separado.

```
usuario → asignacion_rol (vigente) → rol → rol_permiso → permiso → guard del endpoint
```

## Reglas duras

1. **El permiso efectivo se calcula, no se guarda.** Es la unión de los permisos de
   los roles **vigentes**: ni revocados (`revocada_en IS NULL`) ni vencidos
   (`vigente_hasta`). Nunca hay una columna `es_admin`.
2. **Nadie se asigna un rol a sí mismo** (`R-SEG-07`). Lo impide la base.
3. **Una asignación viva por usuario, rol y ámbito** (`R-SEG-08`).
4. **Revocar cierra sesiones.** Un permiso revocado con la sesión viva no está
   revocado. Se invalidan las [[sesion]] del usuario en la misma transacción.
5. **Nada se borra.** Se escribe `revocada_en` y `motivo_revocacion` (`R-AUD-01`).
6. **Denegar por omisión.** Endpoint sin permiso declarado = endpoint cerrado. El
   guard global rechaza lo que no declara nada.

## Ámbito

| Ámbito | `ambito_id` | Ejemplo |
| --- | --- | --- |
| `PLATAFORMA` | **debe ser NULL** | analista de cumplimiento, tesorería |
| `GRUPO` | **obligatorio** | organizador de ese grupo, moderador de ese grupo |

Un rol de grupo sin `ambito_id` sería un rol de plataforma disfrazado: la base lo
rechaza con `ck_asignacion_ambito_completo`.

## Nombre del permiso

```
<RECURSO>_<ACCION>      BILLETERA_OPERAR · CUMPLIMIENTO_CASOS · TESORERIA_OPERAR
```

`recurso` y `accion` van además en columnas propias de [[permiso]], para poder
listar "todo lo que toca la billetera" sin parsear cadenas.

`requiere_mfa = true` en los permisos que autorizan movimiento de dinero, cambios
de límites, acceso a datos personales masivos y administración de accesos.

## Segregación de funciones

La tabla de pares incompatibles es **explícita y está en el código**, no en la
cabeza de nadie:

| No pueden convivir | Por qué |
| --- | --- |
| autorizar entrega · ejecutar desembolso | quien autoriza no ejecuta (`R-SEG-04`) |
| aplicar sanción · resolver su apelación | debido proceso (`R-ORG-05`) |
| solicitar reembolso · aprobarlo | doble firma sobre dinero |
| aprobar factura de proveedor · autorizar su pago | cuatro ojos sobre el egreso (`R-CTB-05`) |
| ser anunciante · moderar piezas creativas | quien publica no se autoaprueba (`R-PUB-05`) |
| administrar accesos · ser oficial de cumplimiento | quien vigila no se amplía permisos |
| operar tesorería · designarse oficial de cumplimiento | `R-UIF-12` y control interno |

Se verifica al **asignar**, no al usar: descubrir la incompatibilidad cuando alguien
ya hizo la operación es tarde.

## Accesos temporales

```ts
// cobertura de vacaciones, consultoría, investigación puntual
{ vigenteHasta: '2026-09-01T00:00:00Z', justificacion: 'cobertura licencia J.P.' }
```

Un acceso **sin** `vigente_hasta` es una decisión: la interfaz la hace confirmar. El
trabajo diario vence las cumplidas y manda el informe de accesos vivos.

El acceso de emergencia fuera de horario se otorga por horas y queda marcado para
revisión obligatoria del oficial de cumplimiento. No existe un "modo dios".

## En el código

```ts
@Permisos('CUMPLIMIENTO_CASOS')          // declarativo, en el controlador
@AmbitoGrupo('grupoId')                  // exige que el permiso sea de ese grupo
async resolverCaso(...) { ... }
```

El guard resuelve los permisos efectivos **una vez por petición** desde el contexto
de sesión, no por cada consulta. El token no lleva los permisos adentro: lleva el
identificador de sesión, y los permisos se leen. Un token de una hora con permisos
adentro es una revocación que tarda una hora.

## Al agregar un permiso

1. Agregar la fila a `seeders/minimos/10-roles-y-permisos.json` con `recurso`,
   `accion`, `descripcion` y `requiere_mfa` (skill `semillas-catalogos`).
2. Asociarlo a los roles que corresponda en el mismo archivo.
3. Declararlo en el endpoint.
4. Si abre acceso a datos personales, revisar `R-SEG-02`: el acceso se registra con
   justificación.
5. Agregar el par incompatible a la tabla de segregación si aplica.

## Qué no hacer

- No cachear permisos más allá de la vida de la sesión.
- No agregar un permiso `*` ni un rol que los tenga todos. Si hace falta para operar,
  el problema es la granularidad, no la falta de un comodín.
- No filtrar por rol en el frontend y confiar en eso. El backoffice esconde botones;
  la API es la que decide.
- No revocar dejando la sesión abierta.
- No usar el rol para filtrar filas: eso es RLS (skill `seguridad-sesion-rls`).

## Ver también

- [[CU-08 Asignar y revocar roles de operador]] · [[CU-09 Cambiar credenciales y solicitar la baja]] ·
  [[CU-49 Designar al oficial de cumplimiento y capacitar]]
- `R-SEG-04` (quien autoriza no ejecuta) · `R-SEG-07` (nadie se otorga un rol a sí
  mismo) · `R-SEG-08` (una asignación viva por usuario, rol y ámbito) · `R-SEG-10`
  (operador sin TOTP no abre sesión) · `R-SEG-12` (toda decisión irreversible exige
  segundo factor), en [[Restricciones]]. **`R-SEG-04` y `R-SEG-07` son reglas
  distintas**: confundirlas produce la prueba equivocada
- El ámbito de un rol es `GLOBAL`, `GRUPO` u `ORGANIZACION` — no existe `PLATAFORMA`
- Un permiso que un CU exige y el catálogo no tiene es un **hueco** ([[Seguridad]] §7
  S-8): se declara, no se inventa ni se cuelga de otro más amplio
- [[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] · [[Seguridad]]
- Skills: `autenticacion-jwt`, `seguridad-sesion-rls`, `seguridad-aplicacion`,
  `back-spring`, `semillas-catalogos`
