---
tags:
  - arquitectura
  - adr
titulo: "ADR-025 — Empaquetado y despliegue de catorce servicios"
estado: aceptada
fecha: 2026-08-16
---

# ADR-025 — Empaquetado y despliegue de los servicios

> Supera a [[ADR-012 Empaquetado y despliegue]], que empaquetaba una API y un worker
> en imágenes Node multietapa.

## Contexto

[[ADR-014 Arquitectura de servicios]] convierte dos procesos en quince —catorce
servicios y un gateway— más PostgreSQL, PgBouncer y Kafka. Eso cambia tres cosas de
golpe: cuánto tarda y cuánto pesa construir, qué levanta una máquina de carril para
trabajar, y qué se despliega cuando cambia una sola cosa.

El riesgo concreto es que el costo de operar quince procesos se coma la ganancia de
aislamiento que motivó la partición. Este ADR existe para acotarlo.

## Decisión

**Una imagen por servicio, construida por capas desde el mismo `Dockerfile`
plantilla; `docker compose` con perfiles para el desarrollo local; Kubernetes con
manifiestos generados para los entornos desplegados.**

### La imagen

```dockerfile
# etapa 1 — construcción, una sola vez para todo el repositorio
FROM eclipse-temurin:21-jdk AS construccion
COPY . .
RUN ./gradlew :servicios:${SERVICIO}:bootJar --no-daemon

# etapa 2 — runtime, capas separadas para que el cambio de código no reconstruya deps
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=construccion .../dependencies/ ./
COPY --from=construccion .../application/  ./
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","org.springframework.boot.loader.launch.JarLauncher"]
```

| Regla | Por qué |
| --- | --- |
| **Sin root**, usuario `app` | Igual que antes; no cambia |
| **Sin `latest`**, versión fijada en la etiqueta | Un despliegue tiene que ser reproducible |
| **Capas de Spring Boot** (`bootJar` en capas) | Las dependencias cambian poco; el código, todo el tiempo. Sin esto, cada cambio empuja 200 MB |
| **JRE, no JDK**, en la imagen final | La mitad del tamaño |
| **Ningún puerto publicado salvo el gateway** | [[ADR-022 Comunicación entre servicios]] |
| Un solo `Dockerfile` plantilla, parametrizado | Catorce `Dockerfile` copiados divergen en tres semanas |

**Presupuestos**, medidos y bloqueantes:

| Presupuesto | Valor | Bloquea |
| --- | :-: | :-: |
| Tamaño de imagen de runtime | ≤ 250 MB | advierte |
| Arranque hasta `/actuator/health/readiness` | ≤ 20 s | sí |
| Memoria en reposo por servicio | ≤ 512 MB | advierte |
| Construcción incremental de un servicio | ≤ 90 s | advierte |

El arranque de 20 s es el precio de la JVM frente a los 5 s de Node del ADR anterior,
y está declarado como tal. Si molesta, la salida es CDS o imagen nativa, no bajar el
presupuesto.

### El desarrollo local — el punto que decide si esto es vivible

**Una máquina de carril no levanta los quince procesos.** Levanta lo mínimo:

```bash
docker compose --profile base up -d      # postgres, pgbouncer, kafka
./gradlew :servicios:aportes:bootRun     # SU servicio, en su IDE, con recarga
```

Contra los otros trece programa por el contrato ([[ADR-020 Contratos OpenAPI primero]]) y prueba con dobles. El sistema entero levantado solo hace falta para el
E2E, que corre en `main` y en la ola de convergencia.

Los perfiles de compose son: `base` (infraestructura), `<servicio>` (uno),
`dinero` (los que hacen falta para un flujo de dinero completo) y `todo`.

### Los entornos desplegados

| Pieza | Decisión |
| --- | --- |
| Orquestador | **Kubernetes**, un `Deployment` por servicio |
| Manifiestos | **Generados** desde un descriptor por servicio, no escritos catorce veces |
| Entrada | **NGINX Ingress** → gateway. Ningún servicio con `Service` de tipo `LoadBalancer` |
| Sondas | `readiness` y `liveness` sobre Actuator, distintas: *readiness* mira la base y Kafka; *liveness*, solo el proceso |
| Configuración | `ConfigMap` por servicio, secretos en `Secret` montados como variables. **Nunca en la imagen** |
| Migraciones | **Un `Job` de Flyway antes del despliegue**, con `rol_migracion`. Ningún servicio migra al arrancar |
| Réplicas | 1 por omisión; 3 en `nucleo-financiero`, `aportes` e `identidad` |
| Despliegue | Rolling, con `maxUnavailable: 0` en los servicios de dinero |

**Que ningún servicio migre al arrancar** es la regla que más importa acá: con
catorce procesos arrancando a la vez, catorce Flyway compitiendo por el mismo esquema
es una condición de carrera garantizada. La migración es un paso del despliegue, no
del arranque.

### El CI construye solo lo que cambió

Gradle sabe qué subproyectos afecta un cambio. El trabajo de CI construye y prueba
**los servicios afectados más los que dependen de plataforma**; un cambio en
`plataforma/` los construye todos, y por eso plataforma se toca por micro-PR.

## Motivo

**El costo de quince procesos se paga en construcción y en operación, no en
desarrollo**, y eso es lo que lo hace aceptable. El carril trabaja con un proceso
suyo y una base; nunca necesita el sistema entero en su máquina, que es lo que suele
hacer insoportables los microservicios.

**Generar los manifiestos** es la misma regla que gobierna todo este proyecto: lo que
escribe un generador no tiene catorce variantes. Catorce despliegues escritos a mano
divergen, y la divergencia se descubre en producción.

**Un `Dockerfile` plantilla y no catorce** por lo mismo. Es la excepción a «cada
servicio posee todos sus archivos», y está justificada: el archivo no tiene contenido
propio del servicio.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Imagen nativa con GraalVM** | Arranque en milisegundos y 80 MB de imagen, que con catorce servicios es tentador. Se descarta ahora por costo de construcción (minutos por servicio) y por fricción con jOOQ y la reflexión. **Es la primera optimización a evaluar** si el arranque o la memoria molestan de verdad. |
| **Un solo contenedor con los catorce servicios dentro** | Anula el despliegue independiente, que es la mitad del motivo de [[ADR-014 Arquitectura de servicios]]. |
| **`docker compose` también en producción** | Alcanza para un entorno de prueba y no da despliegue progresivo, sondas ni reprogramación. |
| **Manifiestos escritos a mano por servicio** | Catorce copias que divergen. |
| **Migración al arrancar** (`spring.flyway.enabled=true`) | Carrera entre réplicas y entre servicios sobre el mismo esquema. |
| **Serverless** | Conexiones efímeras contra RLS por sesión y transacciones con contexto: incompatible, y ya estaba descartado. |

## Consecuencias

**A favor**

- El radio de un despliegue es un servicio: un cambio de umbral UIF no toca el que
  mueve dinero.
- El escalado es por servicio y responde a la carga real de cada uno.
- Una máquina de carril arranca en un comando y no depende de que los otros trece
  compilen.

**En contra, y hay que asumirlo**

- **Quince procesos que observar, actualizar y parchear.** Es el costo recurrente
  más alto de esta arquitectura y no tiene una mitigación elegante: se paga con
  generación de manifiestos y con un tablero único.
- **Kafka en producción es una pieza seria.** Gestionado si se puede; tres nodos y
  alguien que los cuide si no.
- **La JVM arranca lento y ocupa memoria.** Catorce veces 512 MB es un número real de
  infraestructura, declarado arriba como presupuesto.
- Un cambio en `plataforma/` reconstruye los catorce. Correcto y lento; refuerza que
  plataforma se congele temprano.

## Cómo se verifica

- [ ] Ninguna imagen corre como root ni usa `latest`.
- [ ] Ningún servicio publica puerto al exterior salvo el gateway.
- [ ] Ningún `application.yml` tiene `spring.flyway.enabled: true`.
- [ ] Los manifiestos están generados: regenerar no produce diff.
- [ ] `docker compose --profile base up` más un servicio alcanza para trabajar.
- [ ] Cada servicio responde `readiness` y `liveness` por separado, y *readiness*
      falla si la base no está.
- [ ] Los cuatro presupuestos, medidos en la máquina de medición y no declarados.

## Ver también

[[ADR-012 Empaquetado y despliegue]] · [[ADR-014 Arquitectura de servicios]] · [[ADR-022 Comunicación entre servicios]] · [[ADR-017 Propiedad de datos por servicio]] · [[Entornos y despliegue]] · [[_Arquitectura]]
