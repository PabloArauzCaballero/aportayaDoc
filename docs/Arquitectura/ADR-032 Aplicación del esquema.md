---
tags:
  - arquitectura
  - adr
titulo: "ADR-032 — Aplicación del esquema: un solo mecanismo"
estado: aceptada
fecha: 2026-08-18
---

# ADR-032 — Aplicación del esquema

## Contexto

Tres documentos vigentes describían tres mecanismos para el mismo paso
(contradicción C-10, planes/20 §1.6): [[ADR-016 Acceso a datos con jOOQ]] decía
"Flyway aplica `sql/` en orden"; [[ADR-025 Empaquetado y despliegue de los servicios]]
un "`Job` de Flyway"; [[Entornos y despliegue]] el comando real:
`psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql`. Flyway asume archivos inmutables con
checksum; `sql/` **se regenera entero** desde los `.puml` cada vez que el modelo
cambia — los checksums se rompen por diseño. Ninguno declaraba superar a los otros.

## Decisión

**`psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql`, ejecutado por una imagen de
migración con `rol_migracion`, es el único mecanismo. Flyway queda descartado.**

1. **Entornos efímeros** (desarrollo, Testcontainers, CI): base nueva +
   `aplicar.sql` + `sembrar.sql` + `prueba_humo.sql`. Es lo que ya existe y
   funciona; nada cambia.
2. **Producción y ensayo: por diferencia, nunca por reconstrucción.** Un cambio de
   modelo produce, junto al `sql/` regenerado, el script de diferencia
   `sql/migraciones/<fecha>_<n>.sql` del estado N al N+1 (trabajo del troncal:
   cambio de modelo = para todo el parque, `07 §12`). El `Job` de despliegue de
   ADR-025 ejecuta **ese** script con `rol_migracion`, antes que cualquier imagen
   nueva, y `sql/50_verificacion/verificaciones.sql` corre después como parte del
   mismo Job: si la verificación falla, el despliegue no continúa.
3. **Compatibilidad hacia atrás obligatoria** (ya en [[Entornos y despliegue]]):
   el esquema N+1 tiene que sostener los servicios en versión N, porque el Job
   corre primero y las catorce imágenes después. Si una imagen falla a mitad del
   despliegue, el sistema queda mixto y **tiene que seguir funcionando**; el plan
   de reversión es redesplegar la imagen anterior contra el esquema nuevo, y se
   **prueba en `ensayo`** en cada cambio de esquema, no se declara.
4. **Controles de entidad supervisada sobre la migración de producción:**
   - respaldo verificado (último ensayo de restauración dentro de su periodicidad,
     [[ADR-013 Respaldo y continuidad]]) como precondición del Job — sin respaldo
     sano no hay migración;
   - el script de diferencia va a revisión con **doble control**: lo aprueba una
     identidad distinta de la que lo generó, y el Job registra hash del script,
     quién aprobó y salida completa en la bitácora;
   - `rol_migracion` solo existe para el Job: no tiene login interactivo en
     producción, y sus credenciales rotan tras cada uso programado.
5. **jOOQ genera desde la base migrada** — la frase de ADR-016 sobre Flyway queda
   corregida por este ADR; todo lo demás de ADR-016 sigue vigente.

## Motivo

Un mecanismo de migración con checksums sobre archivos que se regeneran es una
alarma que suena en cada despliegue hasta que alguien la apaga para siempre — y una
alarma desactivada en la ruta que toca 307 tablas con dinero es el peor control
posible. `psql` sobre artefactos generados + diferencia revisada a cuatro ojos +
verificación posterior en el mismo Job da lo que un auditor de sistemas pide:
reproducible, aprobado, verificado y con rastro.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Flyway/Liquibase sobre `sql/` generado** | Checksums rotos por diseño; "reparar" el historial en cada regeneración anula la garantía que la herramienta ofrecía |
| **Flyway solo para producción con migraciones a mano** | Dos fuentes de verdad del esquema (los `.puml` y las migraciones); divergen en el primer mes |
| **Regenerar y reconstruir también en producción** | Imposible con datos: reconstruir es perderlos; la diferencia es inevitable, mejor hacerla explícita y revisada |

## Consecuencias

- Nace `sql/migraciones/` (append-only: un script por cambio, nunca editado) y el
  generador de diferencia entra al trabajo del troncal (S4 del saneamiento define
  el punto de partida como diferencia cero).
- ADR-016 y ADR-025 quedan corregidos en su mención a Flyway por este ADR.
- El gate de la fase 17 suma: migración + reversión ensayadas en `ensayo` con
  salida pegada.

## Cómo se verifica

- [ ] `aplicar.sql` en limpio + humo pasan en CI en cada cambio de modelo.
- [ ] Todo cambio de esquema en `ensayo`/producción tiene su script en
      `sql/migraciones/` con hash, aprobador distinto del autor y salida en la
      bitácora.
- [ ] El Job aborta si el último ensayo de restauración está vencido.
- [ ] La imagen N corre contra el esquema N+1 en `ensayo` (prueba de compatibilidad
      hacia atrás por cada cambio).
- [ ] `rol_migracion` no puede abrir sesión interactiva en producción.

## Ver también

[[ADR-016 Acceso a datos con jOOQ]] · [[ADR-025 Empaquetado y despliegue de los servicios]] · [[ADR-013 Respaldo y continuidad]] · [[Entornos y despliegue]] · `planes/20 · Saneamiento del plan` · [[_Arquitectura]]
