---
tags:
  - arquitectura
  - metodo
titulo: "Lineamientos adoptados y descartados"
fecha_revision: 2026-08-16
---

# Lineamientos adoptados y descartados

> Qué se incorporó del prompt de backend de producción v7.0.0 y del prompt general
> de programación, y **qué se descartó porque contradice una decisión ya tomada**
> en esta bóveda. La regla de resolución es una sola:

> **Ante conflicto gana la bóveda.** El caso de uso, la restricción, el modelo y el
> ADR vigente mandan sobre cualquier lineamiento externo. Lo que contradice se
> elimina, no se deja conviviendo: dos reglas opuestas en dos documentos producen
> código que cumple una y viola la otra.

> [!important] Revisado el 2026-08-16 tras el cambio a Spring Boot con microservicios
> **Tres lineamientos que estaban descartados pasaron a estar adoptados.** No porque
> antes se hubieran juzgado mal, sino porque el contexto que los hacía inaplicables
> cambió. Están marcados con ⇄ y explican qué los movió.
>
> Un documento de descartes que no se revisa cuando cambia la arquitectura se
> convierte en la fuente de las contradicciones que existe para evitar.

## Adoptado — y dónde vive ahora

| Del prompt | Skill que lo implementa |
| --- | --- |
| Temperatura 0, cero adivinanzas, supuestos declarados | `codigo-limpio` (regla cero) · [[Prompt general de desarrollo]] |
| KISS, abstraer solo con repetición real, sin capas decorativas | `codigo-limpio` · [[Método de arquitectura]] |
| Nombres del dominio, prohibición de `Manager`/`Helper`/`Utils` | `codigo-limpio` · `glosario-dominio` |
| Límite de tamaño de archivo con umbrales y excepciones justificadas | `ci-calidad` |
| Tipado estricto, contratos explícitos entre capas | `codigo-limpio` · `contratos-api` |
| Matriz de librerías y ADR obligatorio para decisiones caras | `decisiones-adr` |
| No afirmar "listo para producción" sin evidencia | `definicion-de-terminado` |
| Auth default-deny, JWT, refresh rotado, Argon2id, rate limit | `autenticacion-jwt` |
| Validación de toda entrada externa (cuerpo, consulta, cabeceras, webhooks, configuración) | `contratos-api` · `autenticacion-jwt` |
| Idempotencia con clave, huella, toma atómica y repetición | `idempotencia-reintentos` |
| Adaptadores de integración con timeout, reintento, cortacircuitos y dobles | `trabajos-outbox` · `resiliencia-rendimiento` |
| Registro único, redacción de sensibles, identificador de correlación | `observabilidad` |
| Métricas, salud contra disponibilidad, objetivos y alertas accionables | `observabilidad` · `resiliencia-rendimiento` |
| Escalabilidad: pools, N+1, paginación, streaming, contrapresión | `resiliencia-rendimiento` |
| Rendimiento medido, línea base, planes de consulta, presupuesto | `resiliencia-rendimiento` |
| Lectura y escritura separadas, réplicas, vistas y proyecciones | `lecturas-proyecciones` |
| NGINX como única entrada, contenedor sin root, redes internas, Kubernetes aparte | `despliegue-contenedores` |
| Respaldos configurables y **ensayo de restauración obligatorio** | `respaldos-restauracion` |
| Semillas en JSON, idempotentes, las de prueba bloqueadas en producción | `semillas-catalogos` (ya lo hacía: `seeders/minimos` y `seeders/prueba`) |
| CI con puertas que bloquean el merge, cobertura como piso, cadena de suministro | `ci-calidad` |
| OpenAPI sin divergencia, documentación de endpoints, README por carpeta con valor real | `documentacion-entregables` |
| Protocolo por fases con gate de entrada y salida, informe de progreso | `plan-por-fases` |
| Definición objetiva de calidad con matriz de evidencia | `definicion-de-terminado` |

## Adoptado desde el 2026-08-16 — lo que cambió de veredicto

| Del prompt | Estaba descartado porque | Ahora se adopta porque |
| --- | --- | --- |
| ⇄ **Prohibir objetos en `public` y exigir esquemas por dominio** | El esquema generado vivía en el esquema por defecto, y separarlo exigía cambiar el generador y tocar las 633 FK. El aislamiento se lograba con RLS y roles | [[ADR-017 Propiedad de datos por servicio]] lo decide: **un esquema y un rol por servicio**. Las FK cruzadas se conservan porque siguen en el mismo clúster, y el aislamiento pasa a ser un `GRANT`, no una convención. El generador gana la asignación de esquema desde el `.puml` del módulo |
| ⇄ **Cola externa para distribuir eventos** | Una cola externa rompía la garantía de que encolar fuera parte del `COMMIT` | [[ADR-018 Outbox transaccional y mensajería]] separa las dos cosas: **la escritura sigue siendo una fila en PostgreSQL dentro de la transacción**, y Kafka solo transporta lo ya confirmado. La objeción original apuntaba a publicar desde el código de negocio, y eso **sigue prohibido** |
| ⇄ **Validación con anotaciones sobre tipos generados** | Los decoradores no cruzaban al cliente; el contrato en Zod sí | [[ADR-020 Contratos OpenAPI primero]] invierte la fuente: la especificación es lo que cruza, y **Bean Validation** valida los tipos que esa especificación genera. La anotación ya no es la fuente del contrato, es su aplicación |

> **Las tres tienen la misma forma.** El descarte original era correcto *dado su
> contexto*, y el contexto cambió. Por eso se revisan y no se borran: el registro de
> por qué algo se rechazó es lo que permite saber si el rechazo sigue en pie.

## Descartado — y por qué

| Del prompt | Por qué se elimina |
| --- | --- |
| **JPA / Hibernate como ORM** (entidades, `hbm2ddl`, repositorio CRUD genérico) | [[ADR-016 Acceso a datos con jOOQ]]: el esquema es **generado** desde `docs/entidades/*.puml`; el acceso a datos se deriva de la base por introspección con **jOOQ**. Un ORM que administre entidades crea una segunda copia de 307 tablas que diverge, y su *dirty checking* es incompatible con append-only. **Prohibido por escrito**, no desaconsejado |
| **Migraciones versionadas escritas a mano** | El DDL sale de `scripts/generar_ddl.py`. Flyway **aplica** los artefactos de `sql/`; nunca se escribe una migración suelta ([[Entornos y despliegue]]) |
| **Migrar al arrancar el servicio** | Con catorce procesos arrancando a la vez, catorce Flyway compitiendo por el mismo esquema es una carrera garantizada. La migración es un paso del despliegue ([[ADR-025 Empaquetado y despliegue de los servicios]]) |
| **Código primero para la API** (especificación generada desde anotaciones) | [[ADR-020 Contratos OpenAPI primero]]: el contrato se escribe **antes** que la implementación, porque es lo que desbloquea al carril que todavía no tiene ese servicio. Una especificación derivada del código no puede existir antes que el código |
| **Base en memoria para las pruebas** | [[ADR-026 Pruebas de un sistema distribuido]]: **PostgreSQL 16 real con Testcontainers**, porque la garantía vive en la base. Una base sin `EXCLUDE`, `btree_gist` ni RLS prueba otro sistema |
| **Una base por servicio** | [[ADR-017 Propiedad de datos por servicio]]: costaría las 633 FK, las restricciones de exclusión y la atomicidad de la partida doble. Se parte el despliegue, no el modelo |
| **Transacciones distribuidas (XA / dos fases)** | [[ADR-022 Comunicación entre servicios]]: bloqueos que sobreviven a la caída de un participante y un coordinador que es fallo único. La coordinación es por saga con compensación |
| **Gateway que compone respuestas de varios servicios** | Mete lógica de negocio en la pieza que no debe tenerla, y devuelve el archivo compartido por catorce carriles que la arquitectura fue a eliminar |
| **Registro de servicios (Eureka, Consul)** | Descubrimiento dinámico para un conjunto estático de catorce nombres. Se resuelve por DNS del orquestador; una pieza menos que puede caerse |
| **Malla de servicios (Istio, Linkerd)** | Resuelve bien timeout y reintento, y agrega un plano de control entero a operar. Resilience4j en el cliente alcanza a esta escala. Se reevalúa si el parque crece |
| **Roles `backend_migrator` / `backend_writer` / `backend_reader`** | El repositorio ya define `rol_aplicacion`, `rol_backoffice`, `rol_cumplimiento`, `rol_auditor` y `rol_migracion` en `sql/00_base/01_roles.sql`, y ahora además un `svc_<servicio>` por servicio. Se usan **esos nombres**; la idea de separación de privilegios se conserva íntegra y se refuerza |
| **Documentación LaTeX del modelo de datos** | La bóveda de Obsidian con una nota por tabla y una por FK **ya es** esa documentación, y está generada. Un `.tex` paralelo sería una tercera copia que envejece |
| **Cobertura como umbral de calidad principal** | Se conserva como **piso** en `ci-calidad`, pero el criterio sigue siendo el de [[ADR-026 Pruebas de un sistema distribuido]]: qué del dinero **no** está probado, no el porcentaje |
| **"Nunca SQL puro como semillas"** — *no era conflicto* | Ya se cumple: la fuente son los JSON de `seeders/`; el SQL de `sql/60_semillas/` es un derivado generado. Se adopta tal cual |

## Qué hacer cuando aparezca otro lineamiento externo

1. Buscar si contradice un caso de uso, una restricción o un ADR vigente.
2. Si contradice y el lineamiento externo es mejor → **se escribe un ADR nuevo** que
   supera al anterior, con su motivo. No se aplica en silencio.
3. Si contradice y no es mejor → se elimina del lineamiento externo y se anota acá.
4. Si no contradice → se incorpora a la skill que corresponda, no a un documento
   suelto que nadie va a leer al programar.

## Qué hacer cuando cambie un ADR

**Se relee esta tabla entera.** Un descarte se justificó contra un contexto; si el
contexto cambió, el descarte puede haber caducado sin que nadie lo note. El cambio
del 2026-08-16 movió tres de catorce filas: no revisarlas habría dejado el documento
prohibiendo exactamente lo que la arquitectura nueva exige.

## Ver también

[[_Arquitectura]] · [[Método de arquitectura]] · [[Prompts/_Prompts|Prompts generalistas]] · [[Stack]] · [[ADR-017 Propiedad de datos por servicio]]
