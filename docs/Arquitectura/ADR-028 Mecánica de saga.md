---
tags:
  - arquitectura
  - adr
titulo: "ADR-028 — Mecánica de saga: estado, recuperación y compensación"
estado: aceptada
fecha: 2026-08-18
---

# ADR-028 — Mecánica de saga

## Contexto

[[ADR-022 Comunicación entre servicios]] eligió saga **orquestada** para toda
operación que cruza servicios y mueve dinero, y prometió que "el estado se persiste
antes de cada paso" y que "si el proceso muere, otro la retoma". El saneamiento
(`planes/20` §1.2)
encontró que nada de eso tenía mecanismo: sin tabla de estado, sin proceso que
barra sagas atascadas, sin timeout de paso, sin credencial para los pasos que no
tienen usuario, y sin un solo inventario de qué operaciones son sagas — mientras
las fases seguían describiendo esas operaciones como transacciones ACID locales.
El inventario ahora existe (planes/20 §2, S1–S9); este ADR fija cómo se ejecutan.

## Decisión

**El orquestador es el servicio donde nace el hecho, el estado vive en su esquema,
y un barredor con ShedLock garantiza que ninguna saga muere en silencio.**

1. **Orquestación.** El organismo de saga (`CU21CobrarAporteSaga`) persiste en su
   `estado_saga` ([[ADR-027 Infraestructura de mensajería en el modelo|ADR-027]])
   el paso actual **en la misma transacción** que el efecto local de ese paso. Los
   pasos remotos son llamadas HTTP idempotentes: la `Idempotency-Key` se **deriva**
   como `id_saga + numero_de_paso` — esta es la función de derivación que
   [[ADR-021 Sesión, RLS y pooling]] invocaba sin definir.

2. **Recuperación.** Cada servicio orquestador corre un barredor `@Scheduled` +
   ShedLock que toma las sagas con `edad_del_paso > timeout_de_paso` (30 s por
   omisión, configurable por saga) y decide: reintentar el paso (idempotente, sin
   riesgo de duplicar) o **compensar**. Una saga barrida más de N veces (3 por
   omisión) pasa a compensación obligatoria.

3. **Compensación = movimiento inverso, nunca `UPDATE` del libro.** El alcance de
   append-only queda acotado por escrito: el **estado** de una obligación o de una
   saga avanza por `UPDATE`; el **libro contable, los eventos y la bitácora** no se
   editan jamás. Toda compensación produce su propio asiento de reverso, referido
   al original.

4. **Saga que no puede compensar = incidente, no log.** Se abre un incidente
   operativo con severidad alta (skill `observabilidad`), se notifica a una persona
   por el canal de guardia, y la operación queda visible para el usuario como «en
   revisión» — nunca como éxito ni como fallo silencioso. La métrica
   `sagas_sin_resolver{servicio,saga}` con umbral **cero** es alerta de página.

5. **Credencial de sistema.** Los pasos disparados sin usuario (barredor,
   consumidores, `@Scheduled`) llaman a otros servicios con un **token de cliente**
   emitido por `identidad` (client credentials, `app.rol='sistema'`, vigencia
   corta, alcance por servicio). Las políticas RLS del rol `sistema` se escriben en
   `sql/40_reglas/` — permiten exactamente lo que cada trabajo necesita y nada más.
   Ningún paso de saga viaja sin identidad verificable.

6. **Prueba obligatoria.** Cada saga del inventario tiene su `*SagaTest` que fuerza
   la compensación **en cada paso**: matar al orquestador después del paso k y
   verificar que el barredor retoma o compensa, y que el dinero cuadra al centavo
   en ambos desenlaces. Es la séptima prueba obligatoria por caso de uso.

7. **Doble control para intervención manual.** Reprocesar una saga a mano,
   descartarla o forzar una compensación fuera del barredor exige el flujo de
   cuatro ojos: solicitud registrada con justificación + aprobación de otra
   identidad, ambas en `comun.bitacora_evento`. No existe endpoint que lo haga en
   un solo paso.

## Motivo

En una entidad supervisada, una operación de dinero a medias no es un bug: es un
hallazgo de auditoría y, si toca fondos de clientes, un evento de riesgo
reportable. La mecánica de arriba convierte "otro la retoma" —una frase sin
sujeto— en un proceso concreto con bloqueo entre réplicas, límite de reintentos,
desenlace forzoso y rastro completo. La compensación por reverso mantiene el libro
íntegro: el auditor ve el intento, el fallo y la reversión como tres hechos, no un
estado sobrescrito.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Coreografía por eventos** | Ya descartada en ADR-022: nadie sabe en qué estado está la operación; incompatible con atención de reclamos con plazo legal |
| **Motor de workflow externo** (Temporal, Camunda) | Una pieza más que operar, respaldar y auditar, para nueve sagas conocidas; el patrón tabla + barredor cubre el caso con lo que ya existe |
| **Retomar sagas desde el outbox** | El outbox garantiza publicación, no orquestación: no conoce pasos, timeouts ni compensaciones |

## Consecuencias

- `plataforma/comun-mensajeria` (o un módulo hermano `comun-saga`) provee la base:
  entidad de estado, barredor, derivación de clave, y el contrato del paso
  compensable. Los servicios orquestadores (`aportes`, `entregas`, `garantia`,
  `tarifas`) la usan; los demás no la conocen.
- `identidad` suma la emisión de tokens de cliente al contrato de la fase 3.
- El recetario `00c` incorpora la receta de saga; las fronteras transaccionales de
  los CU afectados quedan reescritas (planes/20 §2).

## Cómo se verifica

- [ ] Toda saga del inventario tiene su fila en `estado_saga` desde el primer paso.
- [ ] Matar el orquestador en cualquier paso ⇒ el barredor la resuelve en menos de
      `2 × timeout_de_paso`, sin duplicar efectos (probado en `*SagaTest`).
- [ ] `sagas_sin_resolver` en cero es condición de gate de la fase 17; distinto de
      cero en producción despierta a alguien.
- [ ] Toda compensación tiene asiento de reverso referido al original; ningún
      `UPDATE` toca `asiento_contable` ni `movimiento_contable` (probado y con
      trigger).
- [ ] La intervención manual sin segunda aprobación no existe como ruta (probado
      con prueba negativa de permisos).

## Ver también

[[ADR-022 Comunicación entre servicios]] · [[ADR-027 Infraestructura de mensajería en el modelo]] · [[ADR-021 Sesión, RLS y pooling]] · [[ADR-024 Autenticación y sesión distribuida]] · `planes/20 · Saneamiento del plan` · [[_Arquitectura]]
