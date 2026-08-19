---
tags:
  - arquitectura
titulo: "Entornos y despliegue"
fecha_revision: 2026-08-16
---

# Entornos y despliegue

> Cómo se aplica el esquema, cómo se opera y qué tiene que ser cierto antes de que
> un despliegue toque dinero real.

## Procesos

Desde [[ADR-014 Arquitectura de servicios]] no hay un proceso `api` y un `worker`:
hay **catorce servicios**, cada uno con sus propios trabajos programados y su propio
relevo de outbox, más el gateway.

| Proceso | Qué corre | Rol de base | Escala |
| --- | --- | --- | --- |
| `gateway` | Spring Cloud Gateway | — (no toca la base) | Horizontal, sin estado |
| Los catorce servicios | Spring Boot: API + trabajos + relevo | `svc_<servicio>` | Horizontal, por nivel de criticidad: 4 · 3 · 2 réplicas ([[ADR-037 Alta disponibilidad y balanceo]]) |
| `auditoria` (lecturas) | Consultas pesadas y exportaciones | `rol_auditor` (solo lectura) | Contra la **réplica** |
| `migrador` | `psql -f sql/aplicar.sql` | `rol_migracion` (DDL) | **Un `Job`, una vez por despliegue** ([[ADR-032 Aplicación del esquema]]) |

Los roles son los de `sql/00_base/01_roles.sql` más un `svc_<servicio>` por servicio;
**ninguno es superusuario**, ninguno tiene `UPDATE`/`DELETE` sobre las tablas
append-only, y ninguno tiene permiso sobre el esquema de otro servicio: la barrera es
de base, no de código ([[ADR-017 Propiedad de datos por servicio]],
[[ADR-021 Sesión, RLS y pooling]]).

> **Ningún servicio migra al arrancar.** Con catorce procesos levantando a la vez,
> catorce aplicando DDL sobre el mismo esquema es una carrera garantizada. La
> migración es un paso del despliegue, y Flyway está descartado
> ([[ADR-032 Aplicación del esquema]]).

## Entornos

| Entorno | Base | Datos | Quién entra |
| --- | --- | --- | --- |
| Local | PostgreSQL 16 en Docker + Kafka | Semillas de catálogo + `99_desarrollo.sql` | Cualquiera |
| Pruebas (CI) | Testcontainers, efímera | Semillas de catálogo | Nadie |
| Integración | Gestionada | Datos ficticios, proveedores en modo prueba | Equipo |
| Producción | Gestionada, réplica + PITR | Datos reales | **Nadie con `psql` de escritura** |

En producción no hay acceso interactivo de escritura a la base. Lo que haga falta se
hace por caso de uso, y queda en bitácora. Esa es la diferencia entre un sistema
auditable y uno que solo dice serlo.

### Local — lo que levanta una máquina de carril

**No los quince procesos.** Un carril levanta la infraestructura y **su** servicio:

```bash
docker compose --profile base up -d       # postgres, pgbouncer, kafka
./gradlew :servicios:aportes:bootRun      # SU servicio, con recarga
```

Contra los otros trece programa por el contrato y prueba con dobles
([[ADR-020 Contratos OpenAPI primero]]). Los perfiles disponibles son `base`,
`<servicio>`, `dinero` (los necesarios para un flujo de dinero completo) y `todo`.

Que esto sea así es lo que hace vivible la arquitectura: si trabajar exigiera quince
contenedores, los microservicios costarían más de lo que rinden.

## Despliegue del esquema

```bash
python3 scripts/generar_ddl.py                        # el DDL sale de la bóveda
psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql            # esquemas → tablas → claves → índices → sellos → reglas → GRANT

# El SQL escrito a mano (restricciones, semillas, humo) nombra las tablas sin
# calificar. `aplicar.sql` fija el search_path para SU sesión; las sesiones que
# vienen después lo necesitan explícito:
export PGOPTIONS="-c search_path=aportes,auditoria,cumplimiento,entregas,erp,garantia,grupos,identidad,notificaciones,nucleo_financiero,organizador,publicidad,tarifas,transparencia,catalogo,comun,public"

psql -v ON_ERROR_STOP=1 -f sql/60_semillas/sembrar.sql
psql -f sql/50_verificacion/prueba_humo.sql           # comprobaciones sobre la base real
./gradlew generateJooq                                # introspección → clases de jOOQ, por esquema
```

**Solo en desarrollo**, después de lo anterior:

```bash
psql -c "ALTER DATABASE aportaya SET app.entorno = 'dev'"   # la marca, una sola vez
psql -v ON_ERROR_STOP=1 -f sql/61_dev/sembrar_dev.sql
```

Sin esa marca, `sembrar_dev.sql` **aborta**. Es deliberado: es lo que hace que un
despliegue apuntado al lugar equivocado falle en vez de contaminar una base real
([[ADR-032 Aplicación del esquema]], `seeders/README.md`). Un entorno productivo
**nunca** ejecuta el `ALTER DATABASE`, y por eso `sql/61_dev/` puede quedarse en la
imagen sin riesgo.

Reglas:

1. **El esquema se despliega antes que el código**, y solo con cambios compatibles
   hacia atrás (agregar columna anulable, agregar tabla). Un cambio incompatible se
   parte en dos despliegues: primero la base tolera ambos, después el código deja de
   usar lo viejo.
2. **Nunca se edita `sql/` a mano.** Si hace falta algo distinto, se cambia el
   `.puml` o el catálogo y se regenera (skills `boveda-modelo`, `restriccion`).
3. **Un cambio de esquema no se paraleliza.** Para todo, se hace en troncal, se
   regenera, se verifica la bóveda y recién ahí los carriles rebasan. La partición en
   servicios **no** compra independencia de modelo, y suponerlo desincroniza catorce
   compilaciones a la vez.
4. **Sin catálogo sembrado no se opera**, y es deliberado: *denegar por omisión*
   rechaza toda operación sin límite, tarifario o licencia vigente (`R-LIM-01`,
   `R-LIC-01`).

## Configuración

Cada servicio trae su `application.yml` y **valida su configuración al arrancar**: si
falta una clave, el proceso no levanta. Nada de valores por defecto silenciosos para
credenciales, umbrales o direcciones de proveedores. Los secretos viven en el gestor
de secretos del proveedor, nunca en el repositorio ni en la imagen.

**No hay un archivo de configuración compartido.** Con catorce servicios, la
configuración vive dentro del servicio que la usa: es lo que elimina el conflicto que
un `.env.example` común producía en cada PR.

Lo que **no** es configuración: umbrales regulatorios, límites operativos y tarifas.
Eso es catálogo en la base, con vigencia y evidencia de quién lo cambió.

### El único número que nadie decide solo

La suma de los pools de los catorce servicios más sus relevos no puede superar
`max_connections`. Se declara en un solo lugar y el arranque advierte si la suma se
pasó ([[ADR-021 Sesión, RLS y pooling]]). Un servicio que sube su pool sin mirar la
suma le roba conexiones a los otros trece.

## Puerta de calidad antes de producción

- [ ] `sql/aplicar.sql` aplica en limpio y la prueba de humo pasa.
- [ ] La suite completa pasa contra PostgreSQL real
      ([[ADR-026 Pruebas de un sistema distribuido]]).
- [ ] Las clases de jOOQ regeneran y **todo compila**: si el esquema cambió y el
      código no, el build falla ([[ADR-016 Acceso a datos con jOOQ]]).
- [ ] Cada par de servicios que se llama tiene su contrato verificado en verde.
- [ ] Cada saga tiene su prueba de compensación en verde.
- [ ] Ninguna regla de análisis estático de dinero silenciada.
- [ ] Los manifiestos regeneran sin diff.
- [ ] Existe plan de reversión: el despliegue anterior levanta contra el esquema
      nuevo.

## Operación

| Qué | Cómo |
| --- | --- |
| Respaldo | Continuo con PITR; se prueba la restauración, no se asume |
| Continuidad | CU-56 exige ejercitar la prueba de continuidad, con evidencia |
| Monitoreo | Retraso del relevo de outbox, edad del evento más viejo sin publicar, retraso del consumidor por tema, cola de descartados, fallos por adaptador, cortacircuitos abiertos, descuadre de encaje |
| Alertas que despiertan a alguien | Encaje descuadrado, cierre diario no ejecutado, reporte con plazo legal por vencer, **saga que no pudo compensar**, evento en la cola de descartados |
| Trazas | OpenTelemetry; toda traza lleva `cu`, `usuario_id`, `servicio` y un `x-request-id` que **atraviesa los catorce** |
| Retención | Según [[Cumplimiento]]; los registros de auditoría no se purgan por conveniencia |

> **Con catorce servicios, la traza correlacionada deja de ser comodidad.** Es la
> única forma de responder «¿qué pasó con el aporte de Juan del martes?» sin leer los
> registros de siete procesos por separado.

## Integraciones externas

Cada proveedor —pasarela QR, WhatsApp Business Cloud API, SIAT del SIN, KYC— entra
por una **interfaz de dominio** con su adaptador, su clave de idempotencia y su modo
de prueba. Sustituir un proveedor debe ser cambiar un adaptador, no tocar un caso de
uso. Las credenciales de cada uno viven en el gestor de secretos y rotan sin
desplegar código.

Todo adaptador declara **timeout, reintento y cortacircuitos**; uno sin timeout es un
rechazo de revisión, no una omisión menor ([[ADR-022 Comunicación entre servicios]]).

## Ver también

[[ADR-025 Empaquetado y despliegue de los servicios]] · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-021 Sesión, RLS y pooling]] · [[ADR-026 Pruebas de un sistema distribuido]] · [[Flujo de una transacción]] · [[Cumplimiento]]
