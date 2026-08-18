---
name: documentacion-entregables
description: "Documentar AportaYa sin duplicar: qué es fuente de verdad y qué es derivado, OpenAPI sin divergencia, documentación de endpoints, README por carpeta solo donde aporta, evidencia de pruebas y contenido de una entrega final. Úsala al escribir cualquier documento, al agregar un endpoint, o cuando dos documentos digan cosas distintas."
---

# Documentación y entregables

Regla que evita el 90 % del problema:

> **Cada cosa se documenta en un solo lugar; el resto son derivados generados.** Dos
> copias escritas a mano divergen, y cuando divergen nadie sabe cuál es la verdad.

## Quién es fuente de verdad

| Pregunta | Fuente | Derivados |
| --- | --- | --- |
| Qué obliga la norma | `docs/Cumplimiento.md` | — |
| Cómo se ejecuta el flujo | `docs/CasosDeUso/CU-NN` | Pruebas, códigos de error |
| Qué impide la violación | `docs/Restricciones.md` | `sql/40_reglas/` |
| Dónde vive cada dato | `docs/entidades/*.puml` | `docs/Modelos/`, `sql/`, tipos de jOOQ |
| Qué catálogos existen | `seeders/*.json` | `sql/60_semillas/` |
| Con qué se implementa y por qué | `docs/Arquitectura/` | — |
| Qué acepta y devuelve la API | `openapi/<servicio>.yaml` | OpenAPI, cliente tipado |
| Cómo se trabaja | `.claude/skills/` | — |

**Nada de lo derivado se edita a mano.** Si hay que cambiarlo, se cambia la fuente y
se regenera; si el CI detecta diferencia, alguien rompió esa regla (`ci-calidad`).

## OpenAPI

- La especificación OpenAPI **se escribe primero y es la fuente**; lo que se genera de ella es el servidor y los clientes, y eso no se edita a mano.
- Incluye rutas reales, parámetros, esquemas, seguridad, errores, ejemplos sintéticos
  y versión.
- El CI compara el generado con el publicado: si difiere, falla.
- La interfaz de exploración no se expone en producción salvo decisión explícita.
- Los ejemplos son **sintéticos**: nunca datos reales de una persona.

## Documentación de endpoints

`docs/endpoints.md` no repite el OpenAPI: dice lo que el esquema no puede decir.

Por endpoint: responsabilidad · caso de uso que implementa · autenticación y
autorización · validaciones relevantes · flujo interno resumido · transacción e
idempotencia · límite de tasa · errores de negocio con su código · archivos
relacionados · enlace al `operationId`.

## README por carpeta

Solo donde oriente de verdad. Un README por subcarpeta trivial es ruido que nadie
mantiene. Cuando existe, dice: qué contiene, qué **no** debe ponerse ahí,
dependencias permitidas y el flujo si no es evidente.

## Comentarios y explicaciones repetidas

Si una explicación técnica se repite en varios archivos, no se copia el comentario:
se escribe una vez en el README de la carpeta o en la skill correspondiente, y el
código apunta ahí. En el código quedan los comentarios que dicen **por qué**
—restricción, norma, paso del caso de uso— no los que repiten el qué
(`codigo-limpio`).

## Cómo se explica algo complejo

Cuando haya que documentar un mecanismo del dominio —el encaje, el sorteo con
compromiso y revelación, la partida doble— se explica por capas: analogía simple →
qué resuelve en el negocio → cómo funciona técnicamente → dónde se confunde la
gente → un escenario concreto. Sirve para incorporar a alguien nuevo en horas en
lugar de semanas.

## Evidencia

Los resultados de pruebas, carga, seguridad y ensayos de restauración se conservan
como artefactos del CI:

```
artifacts/
├── pruebas/
├── cobertura/
├── carga/
├── seguridad/
└── restauracion/
```

No se versionan en git (`ci-calidad`), pero **se conservan**: son la evidencia con la
que se responde una inspección o un reclamo.

## Entrega final

Cuando el alcance es un producto entregable:

- [ ] Código y `sql/` regenerado.
- [ ] `seeders/` con los catálogos y su estado (lo provisional, marcado como tal).
- [ ] OpenAPI y `docs/endpoints.md` coherentes entre sí.
- [ ] Los ADR de las decisiones tomadas.
- [ ] Informe de progreso y matriz de `definicion-de-terminado`.
- [ ] Manifiestos de despliegue y `.env.example` **sin secretos**.
- [ ] Runbooks: despliegue, reversión, respaldo, restauración, incidente.
- [ ] Comandos exactos para reproducir todo lo anterior.

## Antipatrones

- Documentar el modelo por segunda vez en otro formato.
- Escribir el OpenAPI a mano "porque es más rápido".
- README genérico copiado en diez carpetas.
- Ejemplos con datos personales reales.
- Versionar los JSON de resultados de pruebas.
- Documentación que describe lo que se pensaba hacer, no lo que el código hace.

## Ver también

`ci-calidad` · `contratos-api` · `definicion-de-terminado` · `plan-por-fases` ·
`caso-de-uso` · `boveda-modelo`
