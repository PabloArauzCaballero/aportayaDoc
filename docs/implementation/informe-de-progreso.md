# Informe de progreso

> Trabajo: Portal Administrativo, Contabilidad ERP (M13) y Publicidad (M14).
> Última actualización: 2026-08-16.

## 1. Resumen del ciclo

Se cerraron las fases 3 y 4. El modelo de datos pasó de 275 a **307 tablas** en
14 módulos, con 12 casos de uso nuevos, 14 restricciones de base de datos
nuevas y sus catálogos mínimos. Todo verificado contra PostgreSQL 16 real, no
solo contra los generadores.

## 2. Avance realizado

### Fase 3 — Modelo de datos (cerrada)

| Archivo | Qué cambió |
| --- | --- |
| `docs/entidades/13_contabilidad_erp.puml` (nuevo) | 18 tablas: ejercicio y período contable, centros de costo, presupuesto, terceros comerciales, cuentas por pagar y por cobrar, activos fijos, plantillas de asiento, estados financieros |
| `docs/entidades/14_publicidad_campanas.puml` (nuevo) | 14 tablas: socio comercial, anunciante, cuenta publicitaria, campaña, conjunto de anuncios, segmento, espacio, pieza creativa, revisión, anuncio, impresión, clic, conversión, factura de publicidad |
| `docs/entidades/13_*.md`, `14_*.md` (nuevos) | Fichas de negocio: qué es, para qué sirve, por qué debe existir, a nivel de sistema |
| `docs/entidades/03_aportes_pagos_qr.puml` | `cuenta_contable` gana jerarquía (`cuenta_padre_id`, `nivel`, `es_cuenta_de_movimiento`); `asiento_contable` gana `periodo_contable_id` y tres `origen_tipo` nuevos |
| `scripts/modelo.py`, `scripts/generar_boveda.py` | Módulos 13 y 14 registrados en `MODULOS`, `FOCO`, `APPEND_ONLY` (11 tablas nuevas selladas), `OVERRIDES`, `POR_MODULO` |
| `docs/CasosDeUso/CU-100..106`, `CU-110..114` (12 nuevos) | Plantilla completa: 13 secciones, contrato Zod con errores correlativos, ≥3 Gherkin, ≥4 flujos alternativos |
| `scripts/verificar_boveda.py` | Soporta códigos de CU de 3 dígitos (asumía ancho fijo `stem[3:5]`) |

### Fase 4 — Restricciones, roles y catálogos (cerrada)

| Archivo | Qué cambió |
| --- | --- |
| `docs/Restricciones.md` | Familias `R-CTB` (8) y `R-PUB` (6). 124 → **138 restricciones** |
| `sql/50_verificacion/prueba_humo.sql` | 22 pruebas de rechazo nuevas + casos positivos; conteo de tablas selladas 19 → 30; fixtures propios para no depender de seeders |
| `seeders/minimos/10-roles-y-permisos.json` | 3 roles, 13 permisos, 14 asignaciones |
| `seeders/minimos/21-contabilidad-y-publicidad.json` (nuevo) | 5 centros de costo, 3 categorías de activo fijo, 4 espacios publicitarios |
| `seeders/minimos/01-plan-de-cuentas.json` | 19 → 33 cuentas, con jerarquía y marca de cuenta de movimiento |
| `scripts/generar_semillas.py` | `$ref` a la propia tabla: una sentencia por fila |
| `.claude/skills/roles-y-accesos`, `caso-de-uso`, `boveda-modelo` | Pares de segregación nuevos, rangos `CU-100..119`, cifras del modelo al día |

### Pruebas ejecutadas (corrida limpia, 2026-08-16)

| Comando | Resultado |
| --- | --- |
| `python3 scripts/generar_boveda.py` | 307 entidades · 633 FK · `sin_resolver: []` |
| `python3 scripts/generar_ddl.py` | `Sin pendientes a nivel de datos.` · 1183 filas de semilla validadas |
| `python3 scripts/verificar_boveda.py` | **`TODO OK`** |
| `aplicar.sql` sobre `postgres:16` recién creada | `COMMIT` |
| 21 archivos de semillas mínimas | cargados sin error |
| `prueba_humo.sql` | **151 `OK`, 0 `FALLA`** |
| `verificaciones.sql` | las 11 consultas de control devuelven cero filas |

## 3. Riesgos detectados

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| `impresion_anuncio`/`clic_anuncio` crecen sin techo | Índices saturados a mediano plazo | No se particionaron todavía porque `conversion_anuncio` las referencia por FK; migrar esas FK a clave compuesta antes de particionar (fase 9) |
| El sobregasto de un conjunto de anuncios bajo concurrencia | Un lote final puede exceder el presupuesto diario | Declarado en `CU-113` como margen aceptado; se acota en la implementación del worker (fase 9) |
| `es_cuenta_de_movimiento` con `DEFAULT FALSE` | Una cuenta nueva mal sembrada bloquearía sus asientos | El defecto ya se corrigió en el plan de cuentas; la prueba de humo cubre ambos lados (sumarizadora rechaza, cuenta de movimiento acepta) |
| `aplicar.sql` no carga semillas | Una prueba que dependa de datos sembrados queda vacía sin avisar | Las pruebas nuevas crean sus propios fixtures; ya provocó dos falsos verdes en esta fase |

## 4. Decisiones clave

| Decisión | Justificación | Impacto |
| --- | --- | --- |
| Un solo libro mayor: M13 agrega orígenes a `asiento_contable`, no un libro paralelo | Dos libros que deben coincidir son una fuente de descuadres | `origen_tipo` gana 3 valores; ninguna tabla de asientos nueva |
| `anunciante` unifica organizador y socio comercial | El negocio los trata igual; evita FK opcionales duplicadas en toda la cadena | El resto de M14 es agnóstico a quién hay detrás |
| La publicidad cobra por `cuenta_por_cobrar` (M13), no por un circuito propio | No abrir un tercer sistema de facturación | `factura_publicidad` es origen, no registro contable |
| Familias `R-CTB` y `R-PUB` nuevas en vez de forzar las existentes | Las familias ya habían crecido de 12 a 15 al agregar módulos | Catálogo legible por dominio |
| Segregación repartida entre roles, no solo en el trigger | Un trigger que nadie puede activar mal es mejor que uno que se prueba tarde | `CONTABILIDAD` aprueba, `TESORERIA` paga; ningún rol acumula ambos |

## 5. Desviaciones

| Desviación | Motivo | Acción |
| --- | --- | --- |
| La prueba de humo de la fase 3 no corrió en su momento | El entorno no tenía Docker ni `psql` | Se declaró como excepción abierta en vez de cerrar el gate igual; corrió el 2026-08-16 y quedó en verde |
| Se tocó `scripts/verificar_boveda.py` y `scripts/generar_semillas.py`, fuera del alcance declarado | Códigos de CU de 3 dígitos y `$ref` autorreferente eran bloqueantes reales | Ambos cambios son correcciones de supuestos, no funcionalidad nueva; quedan documentados acá y en el plan |
| Cuatro pruebas de humo nuevas pasaron primero por el motivo equivocado | Dependían de filas que `aplicar.sql` no siembra; `INSERT ... SELECT` de cero filas no falla | Reescritas con fixtures propios; verificado que ahora ejercitan el CHECK y no una violación de FK |

## 6. Fase actual

**Fase 5 de 12 — Contratos por caso de uso (OpenAPI) en el `openapi/` de cada servicio.**
Gate de entrada aprobado: el modelo, las restricciones y los catálogos de M13 y
M14 están cerrados y verificados contra base real.

## 7. Próxima fase recomendada

Fase 5: escribir `EntradaCUNN` / `SalidaCUNN` / `ErroresCUNN` para los 12 casos
de uso nuevos, tomando los contratos ya redactados dentro de cada `CU-*.md` como
especificación. El trabajo es de transcripción y tipado, no de diseño: las
decisiones ya están tomadas.

## 8. Estado del entregable

**Completo** para el alcance de las fases 1 a 4: modelo de datos, casos de uso,
restricciones, roles, permisos y catálogos de los módulos 13 y 14, verificados
end-to-end contra PostgreSQL real.

No hay código de aplicación todavía — eso empieza en la fase 5. Nada de lo
entregado hasta acá se ejecuta en producción: es especificación y esquema.
