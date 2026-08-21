---
name: ci-calidad
description: "Puertas de calidad automáticas de AportaYa: orden del pipeline, qué bloquea el merge, límite de tamaño de archivo, cobertura como piso, escaneo de dependencias y secretos, y qué nunca se versiona. Úsala al configurar el CI, cuando un gate falle, al agregar una verificación nueva, o antes de fusionar."
---

# Calidad automática y CI

Lo mecánico no se discute en revisión: lo resuelve la herramienta. La revisión humana
se reserva para el riesgo —dinero, plazos, evidencia— y el CI se ocupa del resto.

## Orden del pipeline

```
1  install --frozen-lockfile
2  format:check
3  lint            (reglas con tipos donde sea viable)
4  typecheck       (estricto, sin warnOnly)
5  test:atomos     (puros, sin contenedor)
6  esquema         python3 scripts/generar_ddl.py → diff vacío
6b bóveda          python3 scripts/verificar_boveda.py → TODO OK (sale 1 si falla)
6c seguridad       python3 scripts/verificar_seguridad.py → TODO OK
                   (patrones prohibidos, secretos, R-SEG-10/11/12, ciclo cableado)
7  base efímera    sql/aplicar.sql sobre base vacía + prueba de humo
8  semillas        mínimas dos veces  → mismo estado, sin duplicados
9  semillas prueba dos veces en entorno no productivo
10 rechazo         las semillas de prueba fallan si el entorno es producción
11 permisos        rol_auditor no escribe; rol_aplicacion no toca append-only
12 test            suite completa contra Postgres real (Testcontainers)
13 datos:tipos     introspección → diff vacío
14 contratos       OpenAPI generado → diff vacío
15 build
16 seguridad       dependencias, secretos, imagen
```

Los pasos 6, 13 y 14 son los que impiden que la bóveda, la base y el código se
desincronicen: si alguno produce diff, alguien editó un derivado a mano.

El paso **6c** es la puerta del estándar de [[Seguridad]]: corre temprano y barato,
antes de compilar nada, porque un secreto versionado o un patrón prohibido no mejora
por esperar veinte minutos de suite. Bajarlo para desbloquear un merge está prohibido
([[Seguridad]] §5, prohibición 16).

## Qué bloquea el merge

| Gate | Motivo |
| --- | --- |
| Errores de tipos o de lint | No se negocia |
| Pruebas fallidas o desactivadas sin justificación | Idem |
| `sql/aplicar.sql` no aplica en limpio o falla la prueba de humo | El esquema es el contrato con la realidad |
| `sql/` regenerado produce diff | Lo derivado divergió de su fuente: alguien editó el SQL a mano |
| Las semillas de dev entran a una base **sin** `app.entorno = 'dev'` | La guarda es lo único que separa dev de producción |
| Un dato de personas aparece en `seeders/minimos/` | Ese archivo va a producción |
| Semillas no idempotentes | Un despliegue repetido duplicaría catálogo |
| Semillas de prueba aceptadas en producción | Riesgo de datos falsos en dinero real |
| Tipos introspectados o OpenAPI desactualizados | Divergencia silenciosa |
| Prueba de permisos fallida | La segregación de funciones es cumplimiento |
| Secreto detectado en el diff | Rotación inmediata, no solo revertir |
| Vulnerabilidad crítica sin excepción aprobada | — |
| Imagen que corre como root, o manifiesto sin recursos ni probes | `despliegue-contenedores` |
| Archivo manual sobre el límite sin excepción justificada | Ver abajo |
| `eslint-disable` sobre reglas de dinero o transacción sin justificación | `dinero-decimal` |

## Tamaño de archivo

| Líneas | Qué pasa |
| --- | --- |
| ≥ 220 | Advertencia |
| ≥ 260 | Revisión de diseño obligatoria |
| ≥ 300 | Bloqueado |

Excepciones permitidas, siempre con comentario en el PR: archivos generados,
catálogos de constantes, OpenAPI generado, pruebas tabulares extensas.

El conteo de líneas **no sustituye** el criterio de composición: un archivo de 180
líneas que mezcla niveles sigue estando mal (`arquitectura-atomica`). Los objetivos
por nivel son más exigentes que el límite duro: organismo ~200, componente ~150.

## Reglas de lint propias del proyecto

Además del estándar:

- Prohibido `number` en tipos, campos o parámetros que denoten dinero.
- Prohibido `parseFloat` / `Number()` sobre importes.
- Prohibida cualquier consulta fuera de `conContexto`.
- Prohibido `await` a un adaptador externo dentro de un bloque transaccional.
- Límite de dependencias entre niveles: `dominio/` no importa infraestructura.
- Prohibido `console.log` en código de runtime.

## Cobertura

Es un **piso**, no una meta: líneas 80 %, funciones 75 %, ramas 70 %, y más alto en
módulos de dinero y cumplimiento. No se excluye código difícil para subir el número,
y el criterio real sigue siendo el de [[ADR-008 Pruebas]]: qué del dinero **no** está
probado.

## Cadena de suministro

- Lockfile congelado; versiones fijadas; `engines` respetado.
- Revisión de dependencias nuevas: cada una se justifica en el PR.
- Escaneo de secretos, SAST e imagen; SBOM en los perfiles que lo exijan.
- Nada de versiones alpha/beta en producción sin ADR y plan de reversión.
- Actualizaciones por versión mayor con decisión consciente, no por rango `^`.

## Qué nunca se versiona

```gitignore
.env
.env.*
!.env.example
*.log
logs/
backups/
*.dump
*.sql.gz
artifacts/
**/resultados-humo*.json
**/resultados-carga*.json
```

Los resultados que sirven como evidencia se publican como artefactos del CI —
`artifacts/pruebas/`, `artifacts/carga/`, `artifacts/restauracion/`— no se commitean.

## Ver también

`definicion-de-terminado` · `codigo-limpio` · `pruebas-cu` · `entorno-monorepo` ·
`despliegue-contenedores` · `git-flujo`
