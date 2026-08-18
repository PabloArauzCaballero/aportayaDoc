---
tags:
  - arquitectura
  - adr
titulo: "ADR-029 — Catálogo legible por todos los servicios"
estado: aceptada
fecha: 2026-08-18
---

# ADR-029 — Catálogo legible por todos los servicios

## Contexto

La regla de la bóveda es **denegar por omisión**: sin límite, licencia, tarifario o
política vigentes, la operación se rechaza ([[Método de arquitectura]],
[[Flujo de una transacción]]). [[ADR-017 Propiedad de datos por servicio]] prometió
un esquema `catalogo` con "tarifario vigente y umbrales regulatorios"; la
implementación (`scripts/modelo.py`) solo puso `tipo_cambio` y `dia_no_habil`. El
resto quedó en los esquemas de `tarifas` y `cumplimiento` — ilegible para los otros
roles — y [[ADR-016 Acceso a datos con jOOQ]] ni siquiera permite generar las
clases de `catalogo`. El resultado (planes/20 §1.3, contradicción C-9): cada
operación de dinero necesitaría una llamada HTTP dentro de la transacción, que
[[ADR-022 Comunicación entre servicios]] prohíbe.

## Decisión

**Lo que todo servicio debe consultar para denegar por omisión vive en `catalogo`,
con lectura universal, escritura controlada y vigencia versionada.**

1. **Contenido.** Se mueven a `catalogo` las tablas que son *parámetro leído por
   todos y administrado por un proceso de gobierno*: umbrales UIF, límites por
   nivel de usuario, calendario de días no hábiles, licencia vigente, tarifario
   vigente, políticas e impuestos — la lista exacta es la de los 20 catálogos de
   `seeders/minimos/`, y la asignación concreta se hace en `scripts/modelo.py`
   (paso S4 del saneamiento).
2. **Permisos.** Todo `svc_*` recibe `SELECT` sobre `catalogo`. La **escritura** la
   tiene únicamente el servicio dueño del ciclo administrativo de cada tabla
   (`cumplimiento` para umbrales y licencia, `tarifas` para el tarifario), con su
   rol propio.
3. **Vigencia, no edición.** Todo parámetro tiene `vigente_desde` /
   `vigente_hasta`; cambiar un umbral es **insertar una vigencia nueva**, nunca
   editar la actual. La operación lee la vigencia a su fecha, y una operación en
   curso no cambia de reglas a mitad de transacción.
4. **Doble control del cambio.** Un cambio de parámetro regulatorio o de tarifario
   exige el circuito de gobierno de la bóveda (acta o solicitud aprobada por
   segunda identidad — skills `gobierno-comites`, `semillas-catalogos`); la fila
   nueva referencia el acta. No hay `UPDATE` administrativo directo en producción.
5. **jOOQ.** El `includes` de generación de cada servicio pasa a ser: su esquema
   + `catalogo` (solo lectura) + sus tablas de infraestructura de
   [[ADR-027 Infraestructura de mensajería en el modelo|ADR-027]]. Delta a ADR-016.
6. **Sin dato vigente ⇒ rechazo.** Un tope `NULL`, una licencia vencida o un
   tarifario sin vigencia a la fecha producen rechazo con código de error, jamás
   permiso. Es la prueba obligatoria de todo caso de uso que consulte catálogo.

## Motivo

El camino caliente de cada operación de dinero no puede depender de otra JVM: la
verificación de límites tiene que ser una lectura local, transaccionalmente
consistente con la operación que la usa. Y en una entidad supervisada el parámetro
es norma: quién lo cambió, cuándo, con qué aprobación y desde cuándo rige es
exactamente lo que un inspector pregunta. Vigencias insertadas + acta referenciada
responden eso sin reconstrucción forense.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Llamada HTTP a `tarifas`/`cumplimiento` en cada operación** | Prohibida dentro de la transacción (ADR-022); fuera de ella introduce una ventana de incoherencia en el peor lugar |
| **Copia local por evento en cada servicio** | Catorce cachés de umbrales regulatorios con rezago: un límite endurecido que tarda en propagarse es un hallazgo UIF; queda como patrón para datos de negocio, no para parámetros |
| **Dejar los parámetros en el esquema del dueño con `GRANT` selectivo** | Multiplica los pares de permisos y rompe la regla simple "el rol solo ve su esquema + catálogo" que la prueba de aislamiento verifica |

## Consecuencias

- `modelo.py` reasigna tablas a `catalogo` y `sql/` se regenera (S4); las
  semillas mínimas apuntan al esquema nuevo.
- La prueba de aislamiento por pares se ajusta: `catalogo` es la excepción de
  lectura esperada; todo lo demás sigue denegado.
- `cumplimiento` y `tarifas` ganan los casos de uso administrativos de vigencia
  (ya existen en la bóveda) con su doble control.

## Cómo se verifica

- [ ] Cualquier `svc_*` puede `SELECT` sobre `catalogo` y ninguno puede `INSERT`
      salvo los dueños declarados (probado por rol).
- [ ] `evaluarTope` con tope `NULL` o vigencia vencida ⇒ rechazo (prueba por CU).
- [ ] Toda fila de parámetro tiene vigencia y referencia de aprobación; el intento
      de `UPDATE` sobre una vigencia activa falla por trigger.
- [ ] Un cambio de umbral aparece en `comun.bitacora_evento` con las dos
      identidades del doble control.
- [ ] `generateJooq` de cualquier servicio produce las clases de `catalogo`.

## Ver también

[[ADR-017 Propiedad de datos por servicio]] · [[ADR-016 Acceso a datos con jOOQ]] · [[ADR-022 Comunicación entre servicios]] · `planes/20 · Saneamiento del plan` · [[_Arquitectura]]
