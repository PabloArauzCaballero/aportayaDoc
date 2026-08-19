---
name: despliegue-contenedores
description: "Empaquetar y desplegar AportaYa: Dockerfile multietapa sin root, NGINX como única entrada pública, redes internas sin puertos expuestos, manifiestos de Kubernetes separados por entorno, sondas, recursos y secretos. Úsala al crear o cambiar Dockerfile, compose, NGINX o manifiestos, y al preparar un despliegue nuevo."
---

# Despliegue en contenedores

Procesos que se despliegan, con su rol de base ([[Entornos y despliegue]]):

| Proceso | Qué corre | Rol |
| --- | --- | --- |
| Cada uno de los catorce servicios | Spring Boot | `svc_<servicio>` |
| `worker` | outbox + Kafka: outbox y trabajos con fecha | `rol_aplicacion` (o rol propio, si se acota) |
| `reportes` | Consultas pesadas y exportes | `rol_auditor` (solo lectura) |
| `migrador` | Aplica `sql/` una vez por despliegue | `rol_migracion` |

Cada uno con su credencial. Ninguno superusuario.

## NGINX es la única puerta

```
Internet → NGINX (único que publica 80/443)
             └── red interna → api · worker · PostgreSQL
```

- La API, el worker y la base **no publican puertos al host**. Nada de `5432` ni
  `6379` expuestos, nada de `network_mode: host`.
- NGINX se ocupa de: TLS, proxy inverso, cabeceras de reenvío, límite de tamaño de
  petición, timeouts, límite de tasa complementario, registro de acceso estructurado y
  salud del upstream.
- Si la base es administrada y remota: red privada, TLS, lista blanca de origen.

## Dockerfile

- **Multietapa**: build separado de runtime; en la imagen final no van ni el código
  fuente ni las dependencias de desarrollo.
- **Usuario no root**, siempre. Una imagen que corre como root no pasa el CI.
- Imagen base mínima y fijada por digest.
- `HEALTHCHECK` cuando aporte, manejo correcto de señales para el apagado controlado.
- Sistema de archivos de solo lectura salvo los montajes necesarios.
- Sin secretos en la imagen ni en argumentos de build; `.dockerignore` completo.
- Límites de CPU y memoria declarados.

## Kubernetes, separado

```
despliegue/
├── Dockerfile              plantilla única, parametrizada por servicio
├── infra.yml               niveles, conexiones, entrada y entornos ← se EDITA
├── compose/                dev: base · <servicio> · dinero · todo
└── k8s/generado/           qa/ y prod/ ← se GENERAN, no se versionan
    ↑ python3 scripts/generar_k8s.py, desde servicios/*/descriptor.yml + infra.yml
```

**Los manifiestos no se escriben ni se editan.** Catorce copias divergen y la
divergencia se descubre en producción ([[ADR-025 Empaquetado y despliegue de los servicios]]).
Lo que se revisa en un PR es el `descriptor.yml` y `infra.yml`.

`dev` no está en k8s: dev es compose. Y los valores secretos **no van a git**.

### El generador valida antes de escribir

Falla, y no genera nada, si: algún servicio declara menos de 2 réplicas, si el máximo
excede el tope de su nivel, si `Σ (replicas_max × pool)` no cabe en PgBouncer, si el
pool de PgBouncer no cabe en `max_connections`, o si un servicio de nivel N1 exige
antiafinidad estricta en un entorno con una sola zona
([[ADR-037 Alta disponibilidad y balanceo]]).

| Aspecto | Regla |
| --- | --- |
| Entrada | NGINX Ingress o Gateway API; los servicios son `ClusterIP` |
| Aislamiento | Namespace por entorno y `NetworkPolicy` con denegación por omisión |
| Seguridad del pod | `runAsNonRoot`, sin escalada de privilegios, capacidades eliminadas, seccomp por defecto |
| Recursos | `requests` y `limits` obligatorios; sin ellos no se despliega |
| Sondas | `liveness`, `readiness` y `startup` distintas y honestas |
| Despliegues | API y cada clase de worker por separado, con `PodDisruptionBudget` |
| Migraciones | `Job` aparte, nunca en el arranque del contenedor de la API |
| Secretos | Gestor de secretos externo; nunca en el manifiesto |

## Salud honesta

- `/salud` (liveness): responde si el proceso vive.
- `/listo` (readiness): **falla** si no puede servir tráfico —base inalcanzable,
  catálogos sin sembrar, migración pendiente—. Devolver `200` cuando no se puede
  servir es peor que caerse.
- El arranque valida las variables de entorno con esquema: si falta una, el proceso
  no levanta.

## Apagado controlado

Recibir la señal, dejar de aceptar peticiones, terminar lo que está en curso, cerrar
el pool. En el worker: terminar el trabajo actual y **no** tomar uno nuevo. Un
apagado brusco a mitad de un trabajo de dinero es un reintento con efecto duplicado
esperando a ocurrir.

## Orden de un despliegue

```
1. Aplicar esquema (Job del migrador)   ← compatible hacia atrás
2. Sembrar catálogos mínimos            ← sin esto, denegar por omisión bloquea todo
3. Desplegar API y worker
4. Verificar readiness y humo
5. Plan de reversión listo: la versión anterior levanta contra el esquema nuevo
```

Un cambio incompatible de esquema se parte en dos despliegues. Siempre.

## Antipatrones

- Publicar el puerto de PostgreSQL "para poder conectarme".
- Correr migraciones al arrancar la API con varias réplicas.
- Un contenedor que corre como root "por ahora".
- `latest` como etiqueta de imagen.
- Manifiesto de producción sin límites de recursos ni sondas.
- Secretos en variables de entorno del manifiesto versionado.

## Ver también

`entorno-monorepo` · `respaldos-restauracion` · `resiliencia-rendimiento` ·
`observabilidad` · `ci-calidad` · `docs/Arquitectura/ADR-012 Empaquetado y despliegue.md` ·
`docs/Arquitectura/Entornos y despliegue.md`
