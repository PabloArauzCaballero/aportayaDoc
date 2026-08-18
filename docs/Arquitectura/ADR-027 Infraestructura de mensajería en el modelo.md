---
tags:
  - arquitectura
  - adr
titulo: "ADR-027 — Infraestructura de mensajería en el modelo de datos"
estado: aceptada
fecha: 2026-08-18
---

# ADR-027 — Infraestructura de mensajería en el modelo de datos

## Contexto

[[ADR-018 Outbox transaccional y mensajería]] decidió que todo efecto externo sale
por un outbox escrito en la misma transacción, que un relevo publica en Kafka y que
todo consumidor es idempotente. Pero el modelo no tiene el sustrato: el saneamiento
del 2026-08-18 (`planes/20` §1.1)
encontró que `evento_dominio` existe **una sola vez**, en el esquema `comun`,
append-only y con `SELECT` reservado a `rol_auditor` — el relevo, que necesita
`SELECT … FOR UPDATE SKIP LOCKED` y un `UPDATE` de estado, es imposible con esos
permisos. Y las tablas que ADR-018 y [[ADR-022 Comunicación entre servicios]] dan
por existentes —`evento_consumido`, el estado de saga, la de ShedLock— **no están en
ningún `.puml`**, mientras [[Estructura del repositorio]] prohíbe crear tablas a
mano. Además el esquema `comun` no figuraba en ningún ADR: la verificación de
[[ADR-017 Propiedad de datos por servicio]] ("catorce esquemas más `catalogo`")
fallaba contra los dieciséis reales.

## Decisión

**Se separan mensajería y auditoría. La mensajería es infraestructura por esquema,
generada por plantilla; la auditoría queda en `comun`, que este ADR documenta.**

1. **Cuatro tablas de infraestructura por esquema de servicio**, agregadas en
   `scripts/modelo.py` como plantilla por módulo (el mismo mecanismo que
   `APPEND_ONLY`), nunca a mano en los `.puml`:

   | Tabla | Para qué | Permisos del `svc_*` propio |
   | --- | --- | --- |
   | `evento_dominio` | outbox del servicio | `SELECT` · `INSERT` · `UPDATE` (solo columnas de estado de publicación) |
   | `evento_consumido (id_evento, consumidor, fecha)` | idempotencia de consumo | `SELECT` · `INSERT` |
   | `estado_saga` | solo en esquemas que orquestan ([[ADR-028 Mecánica de saga]]) | `SELECT` · `INSERT` · `UPDATE` |
   | `shedlock` | bloqueo de trabajos programados entre réplicas | los que ShedLock requiere |

   Ningún otro rol lee el outbox ajeno: la frontera de ADR-017 se sostiene también
   en la mensajería.

2. **El `id_evento` lo genera el productor** — es la clave primaria del outbox — y
   viaja dentro del evento. Es la clave de `evento_consumido`. La purga de
   `evento_consumido` sigue la retención del tema correspondiente; los eventos
   publicados del outbox **no se borran**: se marcan publicados y se archivan según
   el plazo de conservación legal (diez años para hechos con efecto contable,
   `docs/Cumplimiento.md`).

3. **El esquema `comun` queda documentado como el de auditoría transversal**:
   `bitacora_evento` y `registro_acceso_datos`, `INSERT` para todos, `SELECT` solo
   `rol_auditor`, append-only con cadena de hash. No se le agrega nada más sin ADR.

4. La verificación de ADR-017 pasa a ser: `\dn` lista **dieciséis** esquemas —
   catorce de servicio, `catalogo` y `comun`.

## Motivo

**El outbox es la garantía de que un hecho con efecto externo no se pierde ni se
duplica; una garantía cuyo sustrato no existe es una promesa.** Ponerlo por esquema
—y no compartido— mantiene la propiedad de ADR-017: cada relevo ve solo sus
eventos, y un servicio comprometido no puede leer ni marcar los ajenos. Generarlo
por plantilla mantiene la regla de que el esquema sale entero de los generadores, y
hace imposible que un servicio nuevo (fase 18, 19 o posteriores) nazca sin su
infraestructura.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Outbox compartido en `comun`** (lo que había) | Cada relevo vería los eventos de los otros trece; permisos incompatibles con append-only; reintroduce el acceso cruzado que ADR-017 declara imposible |
| **Escribir las tablas a mano en cada `.puml`** | Catorce copias que divergen; el generador existe para eso |
| **CDC/Debezium sin outbox** | Sigue siendo la mejora futura de ADR-018; no elimina la necesidad de `evento_consumido` ni de `estado_saga` |

## Consecuencias

- `scripts/modelo.py` y `scripts/generar_ddl.py` incorporan la plantilla; `sql/` se
  regenera y crece en ~56 tablas de infraestructura (paso S4 del saneamiento).
- `plataforma/comun-mensajeria` programa contra nombres de tabla idénticos en todos
  los esquemas: el código del relevo y del consumidor es uno solo.
- La prueba de aislamiento por par de servicios cubre también el outbox ajeno.

## Cómo se verifica

- [ ] `\dt <esquema>.*` muestra las cuatro tablas en cada esquema de servicio.
- [ ] El rol de un servicio no puede hacer `SELECT` sobre `evento_dominio` de otro
      esquema (permiso denegado, probado por par).
- [ ] `comun` contiene exactamente `bitacora_evento` y `registro_acceso_datos`.
- [ ] Con Kafka caído, los eventos se acumulan en el outbox propio y hay métrica de
      edad del evento más viejo, con umbral y alerta.
- [ ] Un evento del outbox marcado publicado nunca se borra antes de su plazo de
      conservación.

## Ver también

[[ADR-018 Outbox transaccional y mensajería]] · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-028 Mecánica de saga]] · `planes/20 · Saneamiento del plan` · [[_Arquitectura]]
