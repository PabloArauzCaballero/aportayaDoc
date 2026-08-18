---
tags:
  - arquitectura
titulo: "Estructura del repositorio"
fecha_revision: 2026-08-16
---

# Estructura del repositorio

> Dónde vive cada archivo, y por qué ahí. La estructura no es gusto: es la
> [[ADR-023 Composición atómica en Java|composición atómica]] hecha carpetas, para que
> el lugar de un archivo nuevo sea obvio y su nivel, verificable. Y desde
> [[ADR-014 Arquitectura de servicios]], es además **el mapa de propiedad**: quién
> puede tocar qué sin pedirle permiso a nadie.

## Monorepo con catorce servicios

```
aportaya/
├── settings.gradle.kts          descubre servicios/ por barrido — NO se edita al agregar uno
├── gradle/libs.versions.toml    catálogo de versiones · micro-PR
├── plataforma/                  ← Ola 0. SOLO LECTURA para los carriles de dominio
│   ├── comun-dominio/             Dinero, Periodo, PlazoHabil — átomos puros, sin Spring
│   ├── comun-datos/               conContexto(), SET LOCAL, fábrica de DSLContext
│   ├── comun-web/                 filtro de errores, idempotencia, guardia, traza
│   ├── comun-mensajeria/          outbox, relevo a Kafka, consumidor idempotente
│   ├── comun-pruebas/             Testcontainers, fixtures, las pruebas de barrido
│   └── gateway/                   Spring Cloud Gateway — única entrada pública
├── servicios/                   ← un directorio = un carril = un desplegable
│   ├── identidad/
│   ├── grupos/
│   ├── nucleo-financiero/         billetera, custodia y el libro contable entero
│   ├── aportes/
│   ├── entregas/
│   ├── notificaciones/
│   ├── transparencia/
│   ├── organizador/
│   ├── garantia/
│   ├── auditoria/
│   ├── tarifas/
│   ├── cumplimiento/
│   ├── erp/
│   └── publicidad/
├── clientes/
│   └── typescript/              cliente generado desde los OpenAPI · no se edita a mano
├── apps/
│   ├── movil/                   Expo · app del participante
│   └── backoffice/              React + Vite · cumplimiento, soporte, contabilidad
├── despliegue/
│   ├── Dockerfile               plantilla única, parametrizada por servicio
│   ├── compose/                 perfiles: base · <servicio> · dinero · todo
│   └── k8s/                     manifiestos GENERADOS desde el descriptor de cada servicio
├── sql/                         esquema generado (no se edita a mano)
├── docs/                        la bóveda: especificación y arquitectura
└── scripts/                     generadores en Python
```

**Un solo repositorio** aunque los servicios sean catorce: la bóveda, `sql/` y los
generadores son de todos, y publicarlos como artefacto versionado catorce veces
costaría más que el aislamiento que compraría. El aislamiento que sí importa —código,
build, configuración y despliegue— ya lo da el directorio del servicio.

> **`settings.gradle.kts` descubre los servicios por barrido de directorio.** Es la
> diferencia entre un archivo compartido que catorce carriles editan y un archivo que
> nadie vuelve a tocar. Agregar un servicio es crear una carpeta.

## Dentro de un servicio — todo lo suyo, y nada de nadie

```
servicios/tarifas/
├── build.gradle.kts                    ← sus dependencias. Versiones del catálogo
├── src/main/resources/
│   ├── application.yml                 ← su configuración. No hay .env compartido
│   └── openapi/tarifas.yaml            ← SU contrato, escrito primero
├── src/main/java/bo/aportaya/tarifas/
│   ├── dominio/                        ← ÁTOMOS
│   │   ├── CalculoDeComision.java
│   │   └── TarifarioCongelado.java
│   ├── infraestructura/                ← MOLÉCULAS
│   │   ├── DevengoRepositorio.java
│   │   ├── TarifarioRepositorio.java
│   │   ├── NucleoFinancieroCliente.java      cliente de otro servicio
│   │   └── ServicioFiscalAdapter.java        proveedor externo
│   ├── aplicacion/                     ← ORGANISMOS
│   │   ├── CU30CotizarComision.java
│   │   ├── CU31DevengarComision.java
│   │   └── CU33DevolverComision.java
│   ├── web/                            ← PÁGINAS: implementan la interfaz generada
│   │   └── TarifasController.java
│   └── trabajos/                       ← cron con ShedLock y consumidores de Kafka
├── src/test/java/bo/aportaya/tarifas/
│   ├── CU31Test.java
│   ├── CU31WebTest.java
│   ├── CalculoDeComisionTest.java
│   └── ArquitecturaTest.java           ← ArchUnit: la dirección de dependencia
├── descriptor.yml                      ← réplicas, recursos, sondas → genera el k8s
└── README.md                           ← qué resuelve, sus CU, eventos, trabajos
```

| Carpeta | Nivel | Puede depender de | Nunca hace |
| --- | --- | --- | --- |
| `dominio/` | Átomo | Nada. Ni Spring, ni jOOQ | IO, SQL, red, reloj o azar sin inyectar |
| `infraestructura/` | Molécula | `dominio/`, `plataforma/comun-datos` | Abrir transacción, orquestar otro caso |
| `aplicacion/` | Organismo | `dominio/`, `infraestructura/` | SQL directo, llamar a un proveedor por su cuenta |
| `web/` | Página | `aplicacion/` y los tipos generados | Contener reglas de negocio |
| `trabajos/` | — | `aplicacion/` | Reimplementar el caso de uso: lo invoca |

**`@Transactional` solo aparece en `aplicacion/`.** Lo verifica ArchUnit, no la
revisión.

## Dentro de `apps/movil` y `apps/backoffice`

```
src/
├── atomos/          Boton, Campo, Monto, Etiqueta, Chip
├── moleculas/       CampoMonto, FilaAporte, SelectorDeGrupo, useAporte
├── organismos/      FormularioDeAporte, TablaDeAportes, ResumenDeBilletera
├── pantallas/       composición de organismos + ruta (sin lógica)
├── dominio/         casos de uso del cliente, sobre el cliente GENERADO
└── tokens/          único lugar con valores de color, espacio y tipografía
```

Los átomos y moléculas **visuales** que sirven a los dos productos suben a un paquete
compartido; los que dependen de una API nativa (cámara, biometría) se quedan en
`apps/movil`.

> **`clientes/typescript/` es generado y no se edita.** Un tipo escrito a mano ahí es
> una divergencia esperando a ocurrir: el CI regenera y falla si hay diff.

## Convención de nombres

| Cosa | Forma | Ejemplo |
| --- | --- | --- |
| Servicio | `kebab-case`, el módulo sin número | `nucleo-financiero` |
| Paquete raíz | `bo.aportaya.<servicio>` | `bo.aportaya.tarifas` |
| Caso de uso | `CU<NN><VerboObjeto>.java` | `CU21CobrarAporte.java` |
| Prueba de caso de uso | `CU<NN>Test.java` | `CU21Test.java` |
| Prueba de API | `CU<NN>WebTest.java` | `CU21WebTest.java` |
| Contrato | `openapi/<servicio>.yaml`, una operación por CU | `openapi/aportes.yaml` |
| Repositorio | `<Sustantivo>Repositorio.java` | `ObligacionRepositorio.java` |
| Cliente de otro servicio | `<Servicio>Cliente.java` | `NucleoFinancieroCliente.java` |
| Adaptador externo | `<Proveedor>Adapter.java` | `PasarelaQrAdapter.java` |
| Tema de Kafka | `aportaya.<modulo>.<evento>` | `aportaya.aportes.aporte_confirmado` |
| Bloqueo de ShedLock | `<modulo>.<trabajo>` | `nucleo_financiero.cierre_diario` |
| Esquema de base | el módulo, sin número | `nucleo_financiero` |
| Rol de base | `svc_<esquema>` | `svc_nucleo_financiero` |
| Tabla de la bóveda | `snake_case` tal cual está en el modelo | `obligacion_aporte` |

El código `CU-NN` en el nombre no es decoración: es lo que hace que ir de la
especificación al código —y de una traza de producción al caso de uso— no requiera
ninguna herramienta. Con catorce servicios importa más, no menos.

## El mapa de propiedad

Esto es lo que hace posible que cinco máquinas trabajen a la vez.

| Ruta | Quién la toca |
| --- | --- |
| `servicios/<suyo>/**` | **El carril, en exclusiva.** Todo: build, configuración, contrato, código, pruebas, descriptor, README |
| `clientes/typescript/**` | Nadie: es generado |
| `plataforma/**` | Ola 0. Después, **micro-PR** |
| `gradle/libs.versions.toml` | **Micro-PR.** Una dependencia nueva no se agrega en rama de carril |
| `settings.gradle.kts` | Nadie: descubre por barrido |
| `despliegue/Dockerfile`, `despliegue/compose/` base | Ola 0 y Ola 5 |
| `sql/**`, `docs/**`, `scripts/**` | Nadie durante los carriles. Cambio de modelo = para todo y se hace en troncal |
| `.claude/skills/**` | **Micro-PR.** Las skills son de todos |

## Qué no va en este repositorio

- **Migraciones escritas a mano.** El esquema sale de `scripts/generar_ddl.py`; Flyway
  solo aplica lo que ya está en `sql/`.
- **Código generado versionado.** Ni jOOQ, ni los clientes, ni los manifiestos de
  Kubernetes. Se generan en el CI y la compilación es el gate.
- **Secretos.** Configuración validada al arrancar; el proceso no levanta si falta una
  variable.
- **Reglas regulatorias como constantes.** Umbrales, límites y tarifas son **catálogo
  sembrado** ([[Restricciones]], skill `norma-nueva`).
- **Utilidades genéricas sin dueño.** Una clase `Utils` es un síntoma: cada función
  pertenece a un átomo con nombre.
- **Un `Dockerfile` por servicio.** Es la única excepción a «el servicio posee todos
  sus archivos», y está justificada: no tiene contenido propio del servicio.

## Ver también

[[ADR-014 Arquitectura de servicios]] · [[ADR-023 Composición atómica en Java]] · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-025 Empaquetado y despliegue de los servicios]] · [[Flujo de una transacción]] · [[Método de arquitectura]] · [[_Arquitectura]]
