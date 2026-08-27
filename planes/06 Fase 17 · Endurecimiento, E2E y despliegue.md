---
tags:
  - plan
  - fase
titulo: "Fase 17 — Endurecimiento, rendimiento, E2E y despliegue"
fase: 17
depende_de: [12, 13, 14, 15, 16]
habilita: []
---

# Fase 17 — Endurecimiento, rendimiento, E2E y despliegue

> **Objetivo.** Que lo que funciona en pruebas aguante producción: carga medida,
> fallos absorbidos, respaldos restaurados de verdad, seguridad verificada y un
> despliegue reproducible. Y que la afirmación *"esto se puede desplegar"* tenga
> evidencia detrás, no confianza.

> **Se ejecuta en:** Ola 5 · carril T (convergencia, máquina única) — ver [[07 Carriles de trabajo concurrente]] para
> la propiedad de archivos y el prompt de arranque del carril.

> [!important] Antes de escribir la primera línea
> [[00b Estándar de ejecución · código limpio, pruebas y calidad]] aplica en
> esta fase entera: regla cero de no inventar, composición atómica, KISS,
> nombres del dominio, las siete pruebas obligatorias por caso de uso (la séptima es
> la compensación de saga) y el checklist de PR. **Se declara cada pieza por nivel
> antes de crearla.**

> **Receta exacta:** [[00c Recetario · implementar un caso de uso]] fija el orden de
> lectura, el orden de construcción en ocho pasos, las firmas canónicas y los
> nombres de las piezas de `comun/`. **Se copian, no se reinventan.**

**Nada de funcionalidad nueva en esta fase.** Si aparece un caso de uso sin
implementar, pertenece a su fase, no a esta.

## Gate de entrada

- [ ] Fases 12 a 16 cerradas con sus gates ejecutados
- [ ] 94 de los 99 casos de uso implementados — la fase 18 (ERP) ya corrió antes; solo
      la fase 19 (publicidad) va después
- [ ] La prueba E2E `PasanakuCompletoE2ETest.java` (hito de la Fase 11) en verde

## Leer antes

[[Seguridad]] **entero** ·
[[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] ·
`docs/Arquitectura/ADR-025 Empaquetado y despliegue de los servicios.md` ·
`docs/Arquitectura/ADR-013 Respaldo y continuidad.md` (vigente) ·
`docs/Arquitectura/Entornos y despliegue.md` ·
skills `seguridad-aplicacion`, `resiliencia-rendimiento`, `respaldos-restauracion`,
`despliegue-contenedores`, `ci-calidad`, `documentacion-entregables`,
`definicion-de-terminado`

---

## 17.1 · Suite E2E completa

Playwright + Chromium contra el stack levantado con `docker compose --profile todo`:
los catorce servicios, el gateway y Kafka corriendo de verdad. Seis recorridos, no uno:

| Recorrido | Qué ejercita | Por qué existe |
| --- | --- | --- |
| `PasanakuCompletoE2ETest.java` | Registro → billetera → grupo → sorteo → aportes → entrega → comisión → factura → cierre diario | El producto existe |
| `IncumplimientoE2ETest.java` | Mora → declaración → descargo → fondo de garantía → subrogación → cobranza → reemplazo | El debido proceso funciona de punta a punta |
| `DisolucionE2ETest.java` | Disolución con prelación y prorrata sobre saldo indivisible | **Ni un centavo perdido ni inventado** |
| `CumplimientoE2ETest.java` | Umbral cruzado → operación relevante → alerta → caso → ROS → reporte mensual remitido | La cadena regulatoria completa |
| `ReclamoE2ETest.java` | Reclamo → plazo hábil → prórroga → resolución → reparación → segunda instancia | El consumidor financiero |
| `DegradacionE2ETest.java` | Pasarela caída → conmutación · SIAT caído → contingencia · mensajería caída → reintento | El sistema con proveedores rotos |

**Chromium** cubre además: `/docs` renderiza el OpenAPI, las rutas públicas de
transparencia y verificación de certificados se ven correctamente, y —cuando exista
`apps/backoffice`— las pantallas de cumplimiento.

**Entregable 17.1:** los seis recorridos en verde en CI, con el compose completo.

---

## 17.2 · Rendimiento: medir antes de optimizar

**Ninguna optimización sin medición previa.** El orden es: medir → identificar →
corregir → volver a medir con el mismo escenario.

### Escenario de carga

Base sembrada con volumen realista a 12 meses: 50 000 usuarios, 5 000 grupos,
600 000 obligaciones, 2 000 000 de movimientos de billetera. Se genera con un script
versionado (`scripts/generar_carga.py`), no a mano.

### Qué se mide

| Métrica | Objetivo inicial | Cómo se mide |
| --- | --- | --- |
| Latencia p95 de `POST /v1/aportes` | < 400 ms | k6 contra el compose |
| Latencia p95 de `GET /v1/billetera/extractos` | < 800 ms (réplica) | ídem |
| Cierre diario con 100 000 movimientos | < 5 min | ejecución cronometrada |
| Pendiente del outbox en régimen | < 100 eventos | métrica por servicio |
| Edad del evento más viejo sin relevar | < 60 s | métrica por servicio |

> Los números son **punto de partida**, no compromiso: se ajustan con la primera
> medición real y se registran en `planes/informes/carril-P<N>.md`. Un objetivo
> inventado sin medir no sirve para nada.

### Qué se busca y se corrige

- **N+1**: consultas dentro de bucles. El acceso por repositorio los oculta bien;
  se detectan con el log de consultas en las pruebas de integración, no leyendo código.
- **Índices**: se revisan los planes de las 10 consultas más lentas con `EXPLAIN
  ANALYZE`. Si falta un índice, **se agrega al modelo** (`docs/`, `sql/30_indices/`),
  no como parche suelto.
- **Paginación**: toda lista tiene tope y orden por **lista blanca**. Una lista sin
  paginación es un incidente esperando fecha.
- **Streaming**: extractos y exportaciones grandes se transmiten, no se arman en
  memoria.
- **Pools**: dimensionados con medición, no con el valor por defecto. Por servicio,
  cada uno el suyo; el de `nucleo-financiero` (dinero) aparte (ADR-021).

**Entregable 17.2:** informe de rendimiento con medición antes y después, y los
índices nuevos incorporados **al modelo**.

---

## 17.3 · Resiliencia

| Mecanismo | Dónde | Verificación |
| --- | --- | --- |
| **Timeouts** en toda llamada externa | Cada `*Adapter` | Proveedor que no responde ⇒ falla en el timeout, no cuelga |
| **Reintentos con retroceso y jitter** | Relevo del outbox y consumidores | Ya implementado en la Fase 2; se verifica bajo carga |
| **Circuit breaker** por proveedor | `RegistroDeSalud` | Proveedor degradado ⇒ se abre el circuito y conmuta **con evento** |
| **Backpressure** | Cola de envíos y rezago de consumo | Cola creciendo ⇒ se rechaza con `429`, no se acumula memoria |
| **Apagado controlado** | Cada servicio y el gateway | `SIGTERM` durante una transacción ⇒ termina, no corta |
| **Idempotencia bajo carga** | Todo endpoint con efecto | 100 requests concurrentes con la misma clave ⇒ **un** efecto |

**Prueba de caos mínima:** matar el contenedor de Postgres durante una operación,
matar un servicio a mitad del consumo de un evento (la re-entrega no duplica el
efecto), matar al orquestador a mitad de una saga (el barredor la retoma o la
compensa — ADR-028), y cortar la red al proveedor simulado. Después de cada una, el
sistema tiene que quedar **consistente**: sin dinero duplicado, sin eventos
perdidos, sin sagas a medias.

**Entregable 17.3:** las seis verificaciones y las cuatro pruebas de caos documentadas
con su resultado.

---

## 17.4 · Respaldos y continuidad (cierra CU-56)

ADR-013 y la skill `respaldos-restauracion`. **La parte que casi nunca se hace y es
la única que importa: el ensayo de restauración.**

| Punto | Qué se verifica |
| --- | --- |
| Respaldo automático | Programado, cifrado, con retención declarada |
| **PITR** | Recuperación a un punto en el tiempo, probada de verdad |
| **Ensayo de restauración** | Restaurar en un entorno limpio y **verificar que el sistema opera** |
| RPO y RTO | **Medidos** en el ensayo, comparados con los comprometidos (`compararObjetivos` de CU-56) |
| Evidencia regulatoria | El resultado del ensayo queda en `prueba_continuidad` y se reporta al comité |

> **"Hay backup" no es una afirmación válida sin una restauración ejecutada.** El
> ensayo produce un RTO y un RPO reales; si no coinciden con los comprometidos, se
> corrige la infraestructura o se corrige el compromiso — pero no se declara cumplido.

**Entregable 17.4:** un ensayo de restauración completo, con RTO y RPO medidos,
registrado en `prueba_continuidad` (CU-56 ejercitado con datos reales).

---

## 17.5 · Seguridad

| Control | Verificación |
| --- | --- |
| **RLS**: pruebas negativas por módulo | Contexto ajeno ⇒ cero filas, en las 12 familias de tablas |
| **Roles de base**: mínimo privilegio | `rol_auditor` no escribe · ningún servicio edita append-only · ninguno es superusuario |
| **Rate limit** en bordes públicos y operaciones sensibles | Login, recuperación, registro, retiro |
| **Secretos** | Ninguno en la imagen, en el repo ni en un log. Escaneo en CI |
| **PII en logs** | Revisión del enmascaramiento de Logback con un caso real por módulo |
| **Cabeceras** | HSTS, CSP, `X-Content-Type-Options`, sin cabeceras que delaten el stack — verificado en el gateway |
| **Dependencias** | `./gradlew dependencyCheckAnalyze` sin vulnerabilidades altas o críticas sin justificar |
| **Archivos subidos** (puerto `AlmacenArchivos`) | Tipo MIME y tamaño validados antes de escribir; nada ejecutable; rutas no derivadas del nombre del usuario |
| **Cifrado** | Números de cuenta bancaria cifrados; cada descifrado con registro y justificación |
| **Acceso administrativo** | `R-SEG-10`: operador sin TOTP no abre sesión y su factor nunca es SMS ni WhatsApp · `R-SEG-11`: cambiar su credencial no deja sesión viva ni dispositivo confiable · `R-SEG-12`: toda decisión irreversible exige segundo factor ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]) |
| **Estándar de codificación segura** | `python3 scripts/verificar_seguridad.py` en TODO OK: patrones prohibidos, secretos versionados y el ciclo cableado ([[Seguridad]] §6) |
| **Segregación** | `R-SEG-04`: quien autoriza no ejecuta; quien decide no resuelve la apelación. `R-SEG-07`: nadie se otorga a sí mismo un rol — son dos reglas distintas y cada una lleva su prueba |

Además: pasar la skill `security-review` sobre el código completo y resolver o
justificar cada hallazgo por escrito, y recorrer [[Seguridad]] §3 control por control
—cada uno declara si lo hace cumplir el **motor**, un **gate** o una **revisión**, y esa
columna es lo que el informe tiene que poder demostrar—.

**Entregable 17.5:** informe de seguridad con los doce controles verificados, la
correspondencia ISO/IEC 27001 · 27002 · 27034 de [[Seguridad]] §4, y los hallazgos
resueltos o justificados. Las brechas que sigan abiertas se declaran en
[[Seguridad]] §7, no se omiten.

---

## 17.6 · Observabilidad en producción

| Pieza | Qué tiene que existir |
| --- | --- |
| **Trazas** | OpenTelemetry desde el request en el gateway hasta el consumidor del evento, correlacionadas por `traza` |
| **Métricas** | Latencia por operación · tasa de error · profundidad de cola · edad del trabajo más viejo · fallos por adaptador externo |
| **Alertas** | **Solo lo que requiere que alguien actúe**: cierre diario no cuadrado, reporte regulatorio por vencer, descuadre de custodia, proveedor degradado, cola creciendo |
| **Tableros** | Uno operativo (salud del sistema) y uno de cumplimiento (CU-98) |
| **Salud** | `/actuator/health/liveness` y `/actuator/health/readiness` conectados a las sondas del orquestador |

> **Una alerta que nadie atiende se apaga sola en la cabeza de la gente.** Cada
> alerta definida tiene un dueño y una acción esperada, escritos.

**Entregable 17.6:** trazas correlacionadas verificadas con un caso real
(*"¿qué pasó con el aporte de Juan del martes?"* respondido con una consulta por
`CU-21` y una fecha).

---

## 17.7 · Despliegue

| Pieza | Estado esperado |
| --- | --- |
| **Dockerfile** multietapa, sin root, sin `latest`, sin secretos | ADR-025 |
| **El gateway** como única entrada pública; los catorce servicios sin puertos publicados | ADR-025 |
| **Manifiestos** separados por entorno, con sondas, recursos y secretos externos | ADR-025 |
| **Réplicas**: cada servicio escala por separado; **los trabajos programados con bloqueo** (ShedLock) entre réplicas | ADR-018 |
| **Migración**: `Job` que ejecuta `psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql` con `rol_migracion`; Flyway descartado | ADR-032 |
| **Rollback** probado | Volver a la versión anterior sin perder datos |
| **PgBouncer** en modo *transaction* para los catorce servicios | ADR-021 |

### Orden del despliegue, no negociable

```
1  job de migración (rol_migracion)   → psql -v ON_ERROR_STOP=1 -f sql/aplicar.sql (ADR-032)
2  job de semillas                    → los 20 catálogos
3  los catorce servicios              → en orden de dependencia: identidad → nucleo-financiero → resto
4  el gateway                         → rutas compuestas, sondas en verde
5  verificación posterior             → sql/50_verificacion/verificaciones.sql + humo: una operación real por servicio
```

> **Este despliegue es el «entorno de ensayo»** que exige el gate de entrada de F12
> ([[15 Fase F12 · Endurecimiento, E2E y publicación]]): nombre `ensayo`, URL interna
> del parque ([[20 Saneamiento del plan · huecos de la migración a microservicios]]
> §6.8). Los E2E de frontend corren contra ese entorno remoto, no contra un compose
> local.

**Entregable 17.7:** un despliegue completo al entorno `ensayo`, con rollback
probado.

---

## 17.8 · Documentación de entrega

Sin duplicar: lo que ya está en la bóveda **no se reescribe**, se enlaza (skill
`documentacion-entregables`).

| Documento | Qué contiene | Dónde |
| --- | --- | --- |
| OpenAPI | **Escrito primero** por servicio, publicado en `/docs` | generado |
| `README.md` raíz | Arranque en 10 minutos, comandos, estructura | actualizado |
| `servicios/<x>/README.md` | Cómo agregar un servicio y un caso de uso nuevo | nuevo |
| Runbook operativo | Qué hacer cuando: el cierre no cuadra · un reporte está por vencer · un proveedor cae · la custodia no concilia | nuevo |
| `planes/informes/carril-P<N>.md` | Avance, riesgos, decisiones y desviaciones del carril (`planes/informe.md` lo consolida solo el guardián) | actualizado |
| Evidencia de pruebas | Salida de los seis corredores + cobertura + informes de rendimiento, seguridad y restauración | adjunta |
| ADR nuevos | Los que hayan surgido durante la ejecución | `docs/Arquitectura/` |

**Entregable 17.8:** la carpeta de entrega completa y `python3
scripts/verificar_boveda.py` en verde.

---

## Gate de salida de la Fase 17 — y del proyecto

Este es el gate que autoriza a decir **"esto se puede desplegar"**. Cada casilla
exige un comando ejecutado o un informe con evidencia. La skill
`definicion-de-terminado` prohíbe explícitamente marcarlas sin eso.

### Funcionalidad
- [ ] **94 de los 99 casos de uso** implementados (todos salvo la fase 19, publicidad), cada uno con sus criterios de aceptación como pruebas nombradas
- [ ] Las **140 restricciones** con prueba de rechazo
- [ ] Las **305 tablas** tienen código que las escribe — verificable recién con las fases 18 y 19 cerradas; el verificador vive en `verificar_criterios.py` sobre las clases jOOQ usadas
- [ ] Los seis recorridos E2E en verde

### Calidad
- [ ] `./gradlew verificar` en verde de punta a punta
- [ ] Cobertura sobre los pisos: 95 % en `dominio/`, 90 % en `aplicacion/`, 100 % de criterios y restricciones
- [ ] `./gradlew generateJooq compileJava` y `generateOpenApiClients` ejecutados: regenerar no modifica nada que esté versionado
- [ ] Sin `@SuppressWarnings` sin comentario que cite el motivo

### Los diez invariantes, verificados uno por uno
- [ ] 1 · El esquema sigue siendo de `sql/` (diff vacío en CI)
- [ ] 2 · Una transacción por caso de uso (lint + revisión)
- [ ] 3 · `SET LOCAL` dentro de la transacción (prueba de dos requests sobre la misma conexión)
- [ ] 4 · Ningún importe pasa por `double` ni `float` (lint + pruebas de cuadre)
- [ ] 5 · Append-only respetado (rechazo por `REVOKE`, probado)
- [ ] 6 · Ninguna red dentro de la transacción (lint)
- [ ] 7 · Idempotencia validada antes de escribir (prueba bajo concurrencia)
- [ ] 8 · Plazos persistidos al crear (prueba del feriado agregado después)
- [ ] 9 · Denegar por omisión (base sin catálogos ⇒ todo rechazado)
- [ ] 10 · Umbrales y tarifas como catálogo (lint + revisión)

### Operación
- [ ] Rendimiento medido, con informe antes/después
- [ ] Las cuatro pruebas de caos con el sistema consistente al final
- [ ] **Ensayo de restauración ejecutado**, con RTO y RPO medidos
- [ ] Informe de seguridad con los doce controles verificados y `verificar_seguridad.py` en TODO OK
- [ ] Trazas correlacionadas hasta el consumidor, probadas con un caso real
- [ ] Despliegue a ensayo completo, con rollback probado

### Cumplimiento
- [ ] La licencia sigue `EN_TRAMITE` en producción y **ningún servicio financiero está habilitado** hasta que ASFI resuelva
- [ ] Los catálogos `⚠ PROVISIONAL` (límites, impuestos, umbrales UIF) **confirmados con legal y tributaria**, o el despliegue queda limitado a entorno de prueba
- [ ] Los contratos de adhesión redactados y registrados ante ASFI
- [ ] La deuda de *object lock* declarada con fecha de revisión

---

## Lo que este plan deja explícitamente pendiente

No es omisión: es alcance declarado, para que nadie lo descubra tarde.

| Pendiente | Por qué queda fuera | Cuándo se retoma |
| --- | --- | --- |
| **Object storage con *object lock*** | ADR-017 eligió el puerto `AlmacenArchivos` con adaptador local como transitorio. La evidencia regulatoria (reportes UIF, respaldos de reclamo, extractos) pide inmutabilidad real | Antes de operar con licencia otorgada |
| **`apps/movil` y `apps/backoffice`** | Este plan es del backend. El cliente `clientes/typescript` generado de los contratos ya los habilita | Plan de frontend aparte |
| **Integraciones reales** (pasarela QR, SIAT, WhatsApp, KYC) | Se implementan los adaptadores y se prueban con simuladores; la integración real exige contratos comerciales | Cuando existan los convenios |
| **Confirmación legal de los catálogos provisionales** | Límites, impuestos y umbrales UIF están sembrados como borrador con su advertencia | Antes de producción |
| **Spring Boot + jOOQ** | [[Stack]] deja registrado que la decisión se revierte si el objetivo real es integrarse con core bancario | Si aparece ese objetivo |

## Ver también

[[00c Recetario · implementar un caso de uso]] · [[07 Carriles de trabajo concurrente]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00 Plan maestro]] · [[05 Fases 12 a 16 · Plataforma, reputación y cumplimiento]] · [[ADR-025 Empaquetado y despliegue de los servicios]] · [[ADR-013 Respaldo y continuidad]] · [[Entornos y despliegue]]
