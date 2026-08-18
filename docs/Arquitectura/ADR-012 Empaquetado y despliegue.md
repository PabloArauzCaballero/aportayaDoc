---
tags:
  - arquitectura
  - adr
titulo: "ADR-012 — Empaquetado y despliegue"
estado: superada por ADR-025
fecha: 2026-08-13
---

# ADR-012 — Empaquetado y despliegue

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-025 Empaquetado y despliegue de los servicios]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

El sistema son varios procesos con privilegios distintos —API, worker, reportes,
migrador— sobre una base que custodia dinero de terceros. Las decisiones de
empaquetado deciden, en la práctica, qué puede alcanzar un atacante que consiga
ejecutar algo dentro de un contenedor.

Además, el esquema es generado y se aplica como paso propio: no puede correr desde el
arranque de una API con varias réplicas.

## Decisión

**Contenedores con una sola puerta pública y roles separados por proceso.**

- **NGINX es el único servicio que publica puertos.** La API, el worker y la base no
  exponen nada al host; la comunicación va por red interna.
- Imágenes multietapa, **usuario no root**, base fijada por digest, sistema de
  archivos de solo lectura salvo montajes necesarios, sin secretos en la imagen.
- Un proceso, un rol de base ([[Entornos y despliegue]]); ninguno superusuario.
- Las **migraciones corren como trabajo aparte** (`Job`/servicio del migrador), nunca
  en el arranque del contenedor de la API.
- Orden fijo: esquema compatible hacia atrás → catálogos mínimos → API y worker →
  verificación de readiness y humo.
- Kubernetes en su propio directorio, con valores por entorno y secretos fuera de git.

## Motivo

**Porque exponer un puerto es una decisión de seguridad, no de comodidad.** Un
`5432` publicado "para conectarme desde la máquina" es, tarde o temprano, un `5432`
alcanzable desde internet.

**Porque migrar desde el arranque es una condición de carrera con dinero al lado.**
Tres réplicas arrancando aplican el mismo DDL a la vez; con suerte falla ruidosamente,
sin suerte deja el esquema a medias.

**Porque sin catálogos el sistema no opera y eso es correcto.** *Denegar por omisión*
rechaza toda operación sin límite, tarifario o licencia vigente, así que sembrar es
parte del despliegue, no una tarea posterior.

**Porque la readiness honesta es lo que evita servir errores.** Un pod que responde
`200` sin poder consultar la base recibe tráfico y lo pierde.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Publicar la API directamente, sin NGINX** | Se pierden TLS centralizado, límites de tamaño y tasa, y el registro de acceso uniforme. |
| **Migrar al arrancar la API** | Carrera entre réplicas; además mezcla privilegios de DDL con el runtime. |
| **Un contenedor con API y worker juntos** | No se pueden escalar por separado y un pico de trabajos degrada las peticiones. |
| **Serverless para los flujos de dinero** | Conexiones efímeras y transacciones con contexto no conviven bien ([[ADR-007 Sesión, RLS y pooling]]). |
| **Kubernetes desde el día uno** | Complejidad sin beneficio hasta que haya escala real; el diseño ya lo permite cuando haga falta. |

## Consecuencias

**A favor**

- Superficie expuesta mínima y auditable: una sola puerta.
- Escalado independiente de API y worker.
- Despliegues reversibles: la versión anterior levanta contra el esquema nuevo.

**En contra**

- Todo cambio incompatible de esquema se parte en dos despliegues. Es más lento y es
  deliberado.
- Desarrollar es menos cómodo: para mirar la base hay que entrar por el camino
  previsto, no por un puerto publicado.
- NGINX es un punto único de entrada que hay que dimensionar y vigilar.

## Cómo se verifica

- [ ] `docker compose config` no publica más puertos que los de NGINX.
- [ ] La imagen no corre como root (verificado en el CI).
- [ ] El despliegue falla si el `Job` de migración no terminó.
- [ ] `readiness` devuelve error con la base caída o los catálogos sin sembrar.
- [ ] Existe y está probado el plan de reversión.

## Ver también

[[Entornos y despliegue]] · `despliegue-contenedores` · `respaldos-restauracion` ·
`ci-calidad` · [[ADR-013 Respaldo y continuidad]]
