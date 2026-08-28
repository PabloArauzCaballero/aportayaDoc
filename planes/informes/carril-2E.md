---
tags:
  - plan
  - informe
  - carril
titulo: "Carril 2E — 07_organizador"
ola: 2
fase: 2
modulo: 07_organizador_automatizacion
rama: pablo/feature/carril-2E-organizador
estado: en curso
---

# Carril 2E — organizador

**Fase** 2 · **Casos de uso** 90, 91, 92, 93, 95, 96 · **Máquina** mac

## Casos de uso

| CU | Contrato | Dominio | Infra | Aplicación | HTTP | Pruebas | Gate |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| CU-90 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-91 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-92 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-93 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-95 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-96 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |

CU-94 pertenece a `cumplimiento` según su propia cabecera de contrato; no es de este
carril.

## Lo que la bóveda no decía y hubo que resolver

### 1 · Una apelación abierta no se puede guardar

`ck_apelacion_org_resuelta` exige `estado = 'PENDIENTE'` **o** que `resuelta_en`,
`resuelta_por` y `resolucion` estén los tres presentes. Y `PENDIENTE` **no está** entre
los estados que admite `ck_apelacion_sancion_org_estado` (ACEPTADA, DESISTIDA,
EN_REVISION, PRESENTADA, RECHAZADA).

Las dos restricciones juntas hacen imposible registrar una apelación sin resolver. No
es una interpretación: cualquier `INSERT` con estado `PRESENTADA` y los campos de
resolución nulos es rechazado por la base.

Manda la DDL. Mientras la apelación está abierta, lo que la registra es el estado
`APELADA` de la sanción —con lo que no se puede dar por cumplida— y el evento de
dominio con el argumento y las evidencias. La fila se escribe entera al resolverla.
El debido proceso se sostiene; lo que no se puede es leer la apelación pendiente desde
esa tabla.

### 2 · El nivel es un límite de plata ajena, no una medalla

`NivelDeOrganizador.admiteMoverseA` deja subir un escalón por vez y bajar sin límite.
No estaba en la bóveda; es la lectura de que el nivel define
`limite_monto_administrado`. Subir dos escalones de golpe entrega un límite que esa
persona nunca sostuvo, y el historial que probaría que puede sostenerlo es justamente
el que no tiene.

### 3 · Los CHECK que no pueden dispararse

Dos casos encontrados de paso, ambos en `sql/40_reglas`:

- `ck_ejecucion_tarea_error` exige `mensaje_error` cuando `resultado = 'FALLO'`, pero
  `ck_ejecucion_tarea_resultado` sólo admite `ERROR`, `EXITO` y `PARCIAL`. El CHECK
  **nunca se dispara**: una ejecución con `ERROR` y sin mensaje entra sin problema.
- El de `apelacion_sancion_org`, arriba.

Un CHECK que no puede dispararse es peor que ninguno: parece que la regla está.

## Huecos declarados

| # | Qué falta o diverge | Dónde | Qué se hizo |
| :-: | --- | --- | --- |
| H-1 | El CU-90 pide que la habilitación quede «pendiente la firma del contrato». No hay estado para eso: `ck_organizador_estado` tiene `CAPACITACION_PENDIENTE` pero no uno de contrato | `sql/` | El organizador nace en `CAPACITACION_PENDIENTE`, y `puedeCrearGrupos` exige contrato firmado vigente. El efecto es el mismo; el estado no lo dice |
| H-2 | El CU-90 pide evaluar «con los requisitos vigentes **al momento de solicitar**». `requisito_habilitacion` **no versiona sus valores**: el mínimo se sobrescribe y no queda contra qué comparar | ídem | La prueba deja escrito el comportamiento real: hoy manda la vara de hoy, y una solicitud puede caer por un cambio posterior a su presentación. Lo único que sí se guarda del momento de solicitar es `puntaje_reputacion_al_solicitar`, que es la mitad del dato |
| H-3 | El CU-92 pide marcar «representatividad baja». `evaluacion_desempeno` no tiene esa columna | ídem | No se inventó. La representatividad se lee de la **cantidad de métricas desglosadas**, que sí está guardada: una evaluación de una métrica se distingue de una de cinco |
| H-4 | El CU-93 pide el estado `PROPUESTA` y una `fecha_limite_descargo`. Ninguno de los dos existe | ídem | La sanción nace `VIGENTE` y su plazo se calcula desde `vigente_desde` —que sí está guardado— y viaja en el evento. El plazo no se recalcula después, que es lo que R-CON-01 protege |
| H-5 | El CU-95 nombra la acción `PROPONER_ENTREGA`. `ck_regla_automatizacion_accion` no la admite; la equivalente es `EJECUTAR_ENTREGA` | ídem | Manda la DDL. Y el efecto que el criterio pide se cumple **de forma más fuerte**: en vez de forzar la bandera en silencio, la regla se rechaza si viene sin confirmación |
| H-6 | El CU-95 pide rechazar `AMBITO_AJENO`. `regla_automatizacion` **no tiene columna de ámbito ni de grupo**: las reglas son globales y el grupo entra al programar la tarea | ídem | No hay ámbito ajeno que rechazar porque no hay ámbito. La prueba verifica que la columna no existe, en vez de simular un rechazo |
| H-7 | **Una apelación abierta no se puede persistir.** Ver §1 | `sql/40_reglas/` | Declarado y resuelto como se describe arriba. Corregirlo es un micro-PR al troncal |
| H-8 | El CU-96 pide el estado `CADUCADA`. `ck_tarea_automatizada_estado` no lo admite; el equivalente es `CANCELADA` | `sql/` | Manda la DDL. Lo que no se negocia del criterio —que **no se ejecute**— sí se cumple, y tiene su prueba |
| H-9 | `ck_ejecucion_tarea_error` referencia el resultado `FALLO`, que el enum no admite. El CHECK nunca dispara | `sql/40_reglas/` | Declarado. Es un micro-PR al troncal |
| H-10 | Los nombres de varios `AP-CU9n-nn` no describen el caso que el código terminó ocupando | `openapi/organizador.yaml` | Se conservan los **números**, que son los que la bóveda ata a cada rechazo, y el contrato documenta qué significa cada uno en la implementación. Renombrarlos es tocar `docs/`, que es troncal |
| H-11 | Ningún trabajo programado está cableado | `trabajos/` vacío | No se cableó. Candidatos: evaluación mensual, control de capacitación vencida, motor de tareas |

## Supuestos declarados

1. **Un requisito obligatorio sin dato no se da por cumplido** (invariante 9). El CU no
   dice qué hacer cuando falta la medición. Habilitar «porque probablemente cumple» es
   entregarle el fondo de un grupo entero a alguien sobre una suposición.
2. **Los faltantes se informan todos juntos**, obligatorios y no. Enterarse de a uno
   por vez, en rechazos sucesivos, no le sirve a nadie.
3. **Una suspensión no le quita los grupos que ya administra.** Dejarlos sin
   administrador de un día para el otro le hace más daño a los participantes que al
   sancionado. La reasignación es otro caso de uso.
4. **El silencio del comité favorece al apelante.** Es la única lectura que no premia
   la demora de quien sanciona.
5. **La lista de acciones sensibles** (`EJECUTAR_ENTREGA`, `APLICAR_MORA`,
   `ESCALAR_COBRANZA`, `LIQUIDAR_PERIODO`) la puso este carril. La bóveda dice
   «acciones sensibles» sin enumerarlas. La línea es: mueve plata ajena o toca la
   reputación de alguien.

## Fronteras transaccionales respondidas

### CU-90 · Postular y habilitarse
1. **Todo junto o nada:** la solicitud y su evento; después, la resolución y el alta
   del organizador.
2. **Fuera del commit:** la verificación de identidad, que llega resuelta.
3. **Clave de idempotencia:** el usuario. Una postulación pendiente por persona.
4. **Qué se bloquea:** `uq_solicitud_organizador_pendiente`, y `FOR UPDATE` sobre el
   organizador al habilitar.
5. **Si el proceso muere tras el commit:** la solicitud existe y el reintento la
   devuelve.

### CU-91 · Firmar y rescindir
1. **Todo junto o nada:** cada acto —emitir, firmar, rescindir— con su evento.
2. **Fuera del commit:** la revocación de roles, que hace `identidad`.
3. **Clave de idempotencia:** `firmado_en IS NULL` y `rescindido_en IS NULL` hacen de
   barrera.
4. **Qué se bloquea:** esas dos condiciones en el `UPDATE`.
5. **Si el proceso muere tras el commit:** el contrato quedó firmado; refirmar no
   sobrescribe la evidencia.

### CU-92 · Evaluar
1. **Todo junto o nada:** la evaluación, sus métricas y el cambio de nivel si baja.
2. **Fuera del commit:** el cálculo de las métricas, que llega hecho.
3. **Clave de idempotencia:** `(organizador, periodo)`.
4. **Qué se bloquea:** la fila del organizador, con `FOR UPDATE`, más versión optimista
   al mover el nivel.
5. **Si el proceso muere tras el commit:** la evaluación existe; la siguiente corrida
   la devuelve.

### CU-93 · Sancionar y apelar
1. **Todo junto o nada:** la sanción, el estado del organizador y el evento.
2. **Fuera del commit:** la reasignación de grupos.
3. **Clave de idempotencia:** el estado de la sanción — `VIGENTE → APELADA → resuelta`.
4. **Qué se bloquea:** `uq_apelacion_por_sancion` y los `WHERE` sobre el estado.
5. **Si el proceso muere tras el commit:** el estado dice en qué punto quedó.

### CU-95 · Definir la regla
1. **Todo junto o nada:** la regla y su evento.
2. **Fuera del commit:** nada.
3. **Clave de idempotencia:** el código, único.
4. **Qué se bloquea:** `uq_regla_automatizacion_prioridad` al encender.
5. **Si el proceso muere tras el commit:** la regla quedó inactiva, que es su estado
   inicial correcto.

### CU-96 · Programar y ejecutar
1. **Todo junto o nada:** la tarea y su evento; después, la ejecución y el estado.
2. **Fuera del commit:** la acción real, que ejecuta el servicio dueño del efecto
   (invariante 11).
3. **Clave de idempotencia:** determinista — regla, grupo y minuto programado.
4. **Qué se bloquea:** `FOR UPDATE` sobre la tarea, más `uq_tarea_automatizada_clave`.
5. **Si el proceso muere tras el commit:** la tarea tiene su estado y sus intentos
   contados.

## Piezas declaradas por nivel

| Pieza | Nivel | CU | Estado |
| --- | --- | :-: | :-: |
| `RequisitosDeHabilitacion` | átomo | 90 | ✅ |
| `NivelDeOrganizador` | átomo | 90, 92 | ✅ |
| `PuntajeDeDesempeno` | átomo | 92 | ✅ |
| `DebidoProceso` | átomo | 93 | ✅ |
| `AccionSensible` | átomo | 95, 96 | ✅ |
| `ClaveDeTarea` | átomo | 96 | ✅ |
| `OrganizadorRepositorio` | molécula | 90, 91, 92, 93 | ✅ |
| `ContratoRepositorio` | molécula | 91 | ✅ |
| `DesempenoRepositorio` | molécula | 92, 93 | ✅ |
| `AutomatizacionRepositorio` | molécula | 95, 96 | ✅ |
| `CU90`–`CU96` | organismos | 90–96 | ✅ |

## Micro-PR abiertos al troncal

| Rama | Qué agrega | Estado |
| --- | --- | :-: |
| — | **Pendiente**: `ck_apelacion_org_resuelta` y `ck_apelacion_sancion_org_estado` se contradicen (H-7); `ck_ejecucion_tarea_error` referencia un resultado que no existe (H-9). Los dos son `sql/40_reglas/`, que es troncal | ⬜ |

## Bloqueos

Ninguno de otro carril.

## Matriz de gates

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `verificar_criterios.py --servicio organizador`: «6 verificados · Sin divergencias» | ✅ |
| Datos | Restricciones citadas con prueba de rechazo | 6+6+5+6+5+6 = 34 rechazos, uno por restricción citada | ✅ |
| Seguridad | Prueba negativa de RLS | pendiente: `AislamientoEsquemaTest` | ⬜ |
| Plazos | Vencimiento y aviso previo | `DebidoProceso`, con el plazo derivado de `vigente_desde` persistido | ✅ |
| Arquitectura | Piezas por nivel, sin saltos | tabla de arriba | ✅ |
| Operación | Health, readiness, trazas | pendiente: capa `web/` | ⬜ |
| Entrega | Pruebas | `integrationTest` en verde | ✅ |

## Gate de salida — evidencia

- [x] `./gradlew :servicios:organizador:integrationTest` — **BUILD SUCCESSFUL**
- [x] `python3 scripts/verificar_criterios.py --servicio organizador` — Sin divergencias
- [x] Cada criterio de aceptación con su `@Test` + `@DisplayName` nombrado igual
- [x] Cada `R-XXX-nn` citado con prueba de rechazo
- [ ] `./gradlew spotlessCheck check`

> Lo verificado es lo que tiene su comando pegado arriba. Lo que **no** está verificado,
> y por eso no se afirma: la capa HTTP, el arranque del servicio, los trabajos
> programados, y las dos restricciones de la bóveda que hoy no pueden dispararse.

## Ver también

[[informe]] · [[07 Carriles de trabajo concurrente]] · [[carril-2B]] · [[carril-3A]]
