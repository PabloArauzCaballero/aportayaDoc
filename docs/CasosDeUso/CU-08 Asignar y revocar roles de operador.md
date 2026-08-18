---
tags:
  - caso-uso
  - modulo/01-identidad-usuarios-y-seguridad
codigo: CU-08
criticidad: alta
actores: [Administrador de plataforma, Oficial de cumplimiento, Sistema]
normas: [ASFI Seguridad de la Información, ASFI control interno, segregación de funciones]
---

# CU-08 — Asignar y revocar roles de operador

> **Objetivo.** Que cada operador tenga exactamente los permisos que su función
> exige, por el tiempo que la exige, y que quitarle el acceso sea tan rápido y tan
> auditable como dárselo.

## Actores y disparador

- **Actor principal:** administrador de plataforma.
- **Disparadores:** alta de un empleado; cambio de función; licencia o baja;
  hallazgo de la revisión periódica de accesos; orden del oficial de cumplimiento.

## Precondiciones

1. Existe el [[rol]] con `codigo`, `ambito` y su conjunto de [[permiso]] en
   [[rol_permiso]]; los roles `es_sistema = true` no se editan desde la interfaz.
2. Quien otorga tiene a su vez el permiso de administración de accesos y **no es**
   el destinatario de la asignación (`R-SEG-04`).
3. El destinatario tiene [[usuario]] activo y MFA configurado si alguno de los
   permisos del rol lleva `requiere_mfa = true`.

## Flujo principal

1. Se elige el [[rol]] y el `ambito` de la asignación: `PLATAFORMA` para funciones
   internas, `GRUPO` cuando el rol solo aplica a un grupo concreto —en ese caso
   `ambito_id` guarda el `grupo_id` y **sin él la asignación se rechaza**.
2. Se fija `vigente_hasta` cuando el acceso es temporal: cobertura de vacaciones,
   consultoría externa, investigación puntual. Un acceso sin fecha de fin es una
   decisión, no un descuido, y así se pide confirmarla.
3. **En la misma transacción**:
   - se crea [[asignacion_rol]] con `usuario_id`, `rol_id`, `ambito`, `ambito_id`,
     `otorgada_por` y `otorgada_en`;
   - se registra en [[bitacora_evento]] quién otorgó qué a quién y con qué
     justificación;
   - se emite `evento_dominio` `rol.asignado`.
4. El permiso efectivo se calcula como la unión de los [[permiso]] de los roles
   **vigentes** del usuario: ni revocados, ni vencidos. La aplicación no cachea ese
   cálculo más allá de la vida del token de sesión.
5. Al revocar se escribe `revocada_en` y `motivo_revocacion`; **la fila no se
   borra** (`R-AUD-01`). Las [[sesion]] activas del usuario se invalidan en el acto:
   revocar un permiso y dejar viva la sesión que lo usaba no es revocar nada.
6. Un trabajo diario vence las asignaciones cuyo `vigente_hasta` pasó y notifica al
   administrador la lista de accesos que caducaron.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 1a | El rol es de ámbito `GRUPO` y no se indica `ambito_id` | Rechazo `AMBITO_INCOMPLETO`: un rol de grupo sin grupo sería un rol de plataforma disfrazado |
| 3a | La combinación de roles rompe la segregación de funciones | Rechazo `INCOMPATIBILIDAD_DE_FUNCIONES`: nadie autoriza y ejecuta el mismo tipo de operación (`R-SEG-04`) |
| 3b | El rol incluye permisos con `requiere_mfa` y el usuario no tiene MFA | Se otorga en estado inactivo hasta que configure el segundo factor; el permiso no rige antes |
| 5a | Se revoca el último rol con permiso de administración | Rechazo: la plataforma nunca queda sin quien administre accesos |
| — | Baja del empleado | Se revocan **todas** sus asignaciones en un acto, se cierran sus sesiones y se conserva el histórico completo |
| — | Acceso de emergencia fuera de horario | Se otorga con `vigente_hasta` de horas y queda marcado para revisión obligatoria del oficial de cumplimiento |

## Postcondiciones

- El permiso efectivo de cualquier operador es reconstruible a cualquier fecha
  pasada a partir de las asignaciones y sus vigencias.
- Ningún acceso queda vivo sin alguien que lo haya otorgado con nombre y fecha.

## Contrato · `openapi/identidad.yaml`

```ts
export const EntradaCU08 = z.object({
  usuarioId:     z.string().uuid(),
  rolId:         z.string().uuid(),
  ambito:        z.enum(['PLATAFORMA', 'GRUPO']),
  ambitoId:      z.string().uuid().nullable(),
  vigenteHasta:  z.string().datetime().nullable(),
  justificacion: z.string().min(10).max(200),
}).strict()

export const EntradaRevocarCU08 = z.object({
  asignacionId:       z.string().uuid(),
  motivoRevocacion:   z.string().min(10).max(120),
  cerrarSesiones:     z.boolean().default(true),
}).strict()

export const SalidaCU08 = z.object({
  asignacionId:      z.string().uuid(),
  permisosEfectivos: z.array(z.string()),
  requiereMfa:       z.boolean(),
  vigenteHasta:      z.string().datetime().nullable(),
}).strict()

export const ErroresCU08 = {
  ROL_INEXISTENTE:              'AP-CU08-01',
  AMBITO_INCOMPLETO:            'AP-CU08-02',
  INCOMPATIBILIDAD_DE_FUNCIONES:'AP-CU08-03',
  AUTOASIGNACION:               'AP-CU08-04',
  SIN_MFA_CONFIGURADO:          'AP-CU08-05',
  ULTIMO_ADMINISTRADOR:         'AP-CU08-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `ROL_INEXISTENTE` | El `rolId` no existe o el rol fue dado de baja |
| `AMBITO_INCOMPLETO` | Ámbito `GRUPO` sin `ambitoId` |
| `INCOMPATIBILIDAD_DE_FUNCIONES` | La combinación resultante viola la segregación de funciones (`R-SEG-04`) |
| `AUTOASIGNACION` | Quien otorga es el destinatario: **nadie se amplía sus propios permisos** |
| `SIN_MFA_CONFIGURADO` | El rol exige MFA y el usuario no lo tiene; la asignación queda inactiva |
| `ULTIMO_ADMINISTRADOR` | La revocación dejaría la plataforma sin administrador de accesos |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `permisosEfectivos(asignaciones, ahora)` | Une los permisos de los roles vigentes a una fecha; puro |
| Átomo | `violaSegregacion(permisos)` | Detecta pares autorizar/ejecutar incompatibles; puro y con tabla de pares explícita |
| Molécula | `RolRepositorio` · `AsignacionRolRepositorio` | Persistencia y consulta por usuario y vigencia |
| Molécula | `InvalidadorDeSesiones` | Cierra las sesiones vivas del usuario afectado |
| Organismo | `CU08AsignarRol` · `CU08RevocarRol` | Transacción: valida, escribe, bitácora, evento e invalidación |
| Página | `POST /accesos/asignaciones` · `DELETE /accesos/asignaciones/:id` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `rol.asignado` | Aviso al destinatario y al oficial de cumplimiento | `ACCESOS_ADMINISTRAR` |
| `rol.revocado` | Invalidación de sesiones y aviso | `ACCESOS_ADMINISTRAR` |
| `rol.vencido` | Trabajo diario de caducidad; informe semanal de accesos vivos | — |

## Interfaz

- **App:** sin pantalla. Los roles de operador son internos.
- **Backoffice:** *Accesos*: matriz de usuarios por rol, con vigencia, quién otorgó
  y un botón de revocación inmediata; y el informe de accesos que vencen esta semana.

## Restricciones aplicables

`R-SEG-04` · `R-SEG-07` · `R-SEG-08` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[asignacion_rol]] · [[rol]] · [[permiso]] · [[rol_permiso]] · [[bitacora_evento]] ·
`evento_dominio` · [[sesion]]

## Criterios de aceptación

```gherkin
Dado un rol de ámbito PLATAFORMA con permisos de cumplimiento
Cuando el administrador se lo asigna a un analista con MFA configurado
Entonces existe una asignacion_rol vigente
Y sus permisos efectivos incluyen los del rol

Dado un usuario que ya tiene el permiso de autorizar entregas
Cuando se le intenta asignar el rol que ejecuta entregas
Entonces se rechaza con INCOMPATIBILIDAD_DE_FUNCIONES

Dada una asignación vigente con una sesión abierta
Cuando se la revoca
Entonces la asignación queda con revocada_en y motivo
Y la sesión del usuario deja de ser válida

Dada una asignación con vigente_hasta en el pasado
Cuando corre el trabajo diario de caducidad
Entonces deja de contar en los permisos efectivos sin que nadie la borre
```

## Ver también

[[CU-04 Autenticar con MFA y registrar dispositivo]] · [[CU-09 Cambiar credenciales y solicitar la baja]] · [[CU-49 Designar al oficial de cumplimiento y capacitar]] · [[CU-55 Gestionar un incidente de seguridad]] · [[CU-90 Postular a organizador y habilitarse]] · [[CU-91 Firmar y rescindir el contrato de organizador]]
