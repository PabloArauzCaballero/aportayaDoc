---
name: entorno-monorepo
description: "Trabajar en el monorepo Gradle de AportaYa: estructura de servicios/ y plataforma/, tareas de Gradle, orden de arranque local con perfiles de compose, regeneración del esquema y de las clases de jOOQ, configuración por servicio y qué hace el CI. Úsala al montar el entorno, al agregar un servicio, cuando algo no compila tras cambiar el modelo, o antes de abrir un PR."
---

# Trabajar en el monorepo

## Estructura

```
aportaya/
├── apps/         api · worker · movil · backoffice
├── packages/     contratos · dominio · ui · datos
├── sql/          esquema generado — no se edita a mano
├── docs/         la bóveda: especificación y arquitectura
└── scripts/      generadores en Python
```

Qué va en cada paquete compartido:

| Paquete | Contiene | No contiene |
| --- | --- | --- |
| `openapi/` de cada servicio | Una operación por caso de uso + sus errores | Lógica, acceso a datos |
| `dominio` | Átomos puros: `Dinero`, `Periodo`, cálculos | IO, tipos de base |
| `ui` | Tokens y átomos/moléculas visuales comunes | Nada que dependa de APIs nativas |
| `datos` | Tipos introspectados + fábrica de conexión | Consultas de negocio |

Un archivo nuevo se ubica por **nivel**, no por conveniencia: `arquitectura-atomica`.

## Arranque local, en orden

```bash
./gradlew build

docker compose up -d db                       # PostgreSQL 16
python3 scripts/generar_ddl.py                # el esquema sale de la bóveda
psql -d pasanaku -v ON_ERROR_STOP=1 -f sql/aplicar.sql
psql -d pasanaku -v ON_ERROR_STOP=1 -f sql/60_semillas/sembrar.sql
psql -d pasanaku -f sql/60_semillas/99_desarrollo.sql   # solo local
psql -d pasanaku -f sql/50_verificacion/prueba_humo.sql # 165 comprobaciones

./gradlew generateJooq                              # introspección → tipos de jOOQ
./gradlew bootRun                                      # api + worker + backoffice
yarn --cwd apps/movil start                  # el FRONTEND sigue en yarn
```

Si te salteás las semillas, **nada funciona**: *denegar por omisión* rechaza toda
operación sin límite, tarifario y licencia vigentes. Es el comportamiento correcto.

## Comandos

| Comando | Qué hace |
| --- | --- |
| `./gradlew bootRun` | Levanta api, worker y backoffice con recarga |
| `./gradlew test` | Suite completa: contenedor, esquema, semillas, pruebas |
| `./gradlew test` | Solo pruebas puras, sin contenedor (para guardar seguido) |
| `./gradlew check` · `./gradlew compileJava` | Estilo y tipos; ambos bloquean el PR |
| `./gradlew generateJooq` | Regenera los tipos desde la base viva |
| `./gradlew generateOpenApiClients` | Genera la interfaz del servidor y los clientes Java y TypeScript desde el OpenAPI |
| `yarn --cwd apps/<x> <script>` | Solo para `apps/movil` y `apps/backoffice`: el frontend sigue en TypeScript |

## Cuando cambia el modelo

Cambiar una tabla o una columna es un procedimiento, no una edición:

```
1. skill boveda-modelo    → editar el .puml, regenerar las notas
2. skill restriccion      → si hay regla nueva que garantizar
3. python3 scripts/generar_ddl.py
4. aplicar sql/ en la base local
5. ./gradlew generateJooq       → el compilador señala cada lugar a revisar
6. actualizar contratos y pruebas
```

**Nunca** se edita `sql/` a mano ni se escribe una migración suelta: el esquema tiene
un solo dueño ([[ADR-002 Acceso a datos]]).

## Variables de entorno

- Validadas con un esquema **al arrancar**: si falta una, el proceso no levanta.
- `.env.example` versionado y completo; `.env` jamás se commitea.
- Sin valores por defecto silenciosos para credenciales, umbrales ni URLs de
  proveedores.
- Lo que cambia por norma o negocio **no es variable de entorno**: es catálogo en la
  base, con vigencia y evidencia.

## Qué hace el CI

```
lint → typecheck → test:atomos → test (contenedor + sql/ + semillas)
     → datos:tipos (diff vacío) → contratos:openapi (diff vacío) → build
```

Falla el build si: el esquema no aplica en limpio, la prueba de humo no pasa, los
tipos introspectados están desactualizados, el OpenAPI publicado difiere, o hay un
`eslint-disable` sin justificación sobre las reglas de dinero y transacción.

## Convenciones de trabajo

| Cosa | Regla |
| --- | --- |
| Rama | `pablo/feature/<CU-NN>-<descripcion-corta>` |
| Commit | Imperativo y en español, citando el caso de uso: `CU-21: cobrar aporte con QR` |
| PR | Qué caso de uso implementa, piezas por nivel, supuestos declarados, cómo verificarlo |
| Dependencia nueva | Se justifica en el PR; sin justificación, no entra |
| Node | La versión de `.nvmrc`; el CI usa la misma |

## Ver también

`git-flujo` · `semillas-catalogos` · `arquitectura-atomica` · `boveda-modelo` · `pruebas-cu` · `revision-codigo` ·
`docs/Arquitectura/Estructura del repositorio.md` · `docs/Arquitectura/Entornos y despliegue.md`
