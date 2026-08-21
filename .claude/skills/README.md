# Skills de AportaYa

Cada carpeta es una skill: instrucciones de **cómo se hace el trabajo acá**, no
documentación del producto. Se invocan solas cuando la tarea coincide con su
descripción, o a mano con `/<nombre>`.

La bóveda (`docs/`) dice **qué** hay que construir. Las skills dicen **cómo**.

## Método y arquitectura

| Skill | Cuándo |
| --- | --- |
| `arrancar-carril` | **Primera acción al abrir el chat de un carril**, antes de todo |
| `frontera-transaccional` | Paso 0 de todo caso de uso: qué va todo-junto-o-nada |
| `arquitectura-atomica` | Antes del primer archivo de cualquier funcionalidad |
| `implementar-desde-boveda` | Al empezar a programar un caso de uso |
| `plan-por-fases` | Cuando el alcance abarque varios módulos o infraestructura |
| `decisiones-adr` | Al elegir una librería o cambiar algo caro de revertir |
| `codigo-limpio` | Al escribir o revisar cualquier código |
| `revision-codigo` | Al revisar un PR |
| `debido-proceso` | Cuando una decisión termine con alguien perdiendo algo |
| `definicion-de-terminado` | Antes de decir que algo está listo |
| `entorno-monorepo` | Al mover paquetes, dependencias o scripts |
| `git-flujo` | Antes de commitear y al abrir el PR |
| `glosario-dominio` | Al nombrar cualquier cosa |

## Especificación: la bóveda

| Skill | Cuándo |
| --- | --- |
| `boveda-modelo` | Al tocar `docs/entidades/*.puml` o regenerar la bóveda |
| `caso-de-uso` | Al escribir o cambiar un caso de uso |
| `restriccion` | Cuando una regla deba ser imposible de violar |
| `norma-nueva` | Cuando aparezca una resolución, circular o umbral nuevo |
| `semillas-catalogos` | Al cambiar un valor de catálogo en `seeders/` |

## Construcción

| Skill | Cuándo |
| --- | --- |
| `contratos-api` | Antes de implementar cualquier endpoint |
| `back-spring` | Al escribir el backend |
| `datos-jooq` | Al escribir consultas y repositorios |
| `servicios-y-sagas` | Al llamar a otro servicio, consumir un evento o cruzar una operación |
| `dinero-decimal` | Cada vez que aparezca un importe |
| `trabajos-outbox` | Al disparar efectos fuera de la transacción |
| `errores-api` | Al devolver o traducir un error |
| `idempotencia-reintentos` | En todo endpoint con efecto y todo webhook |
| `seguridad-aplicacion` | **Antes de escribir cualquier endpoint, consulta, adaptador, Dockerfile o pantalla**, y al revisar un PR |
| `autenticacion-jwt` | Al crear un endpoint o tocar login, refresh y permisos |
| `roles-y-accesos` | Al decidir qué permiso exige algo, o dar de alta a un operador |
| `seguridad-sesion-rls` | En toda consulta con políticas de fila |
| `lecturas-proyecciones` | Listados pesados, extractos, vistas y réplica de lectura |
| `extraccion-de-datos` | Al crear un reporte o exportar algo con datos personales |
| `motor-de-reglas` | Al escribir una regla de cumplimiento, antifraude o automatización |
| `automatizacion-tareas` | Al escribir un trabajo programado o un motor de tareas |
| `pruebas-cu` | Al implementar cualquier caso de uso |

## Interfaz

| Skill | Cuándo |
| --- | --- |
| `disenar-frontend` | Al crear o modificar cualquier pantalla |
| `movil-expo` | Al trabajar en la app |
| `web-backoffice` | Al trabajar en el backoffice |

## Dominio

| Skill | Cuándo |
| --- | --- |
| `kyc-onboarding` | Alta, verificación, niveles, contrato de adhesión |
| `contabilidad-partida-doble` | Cualquier flujo que mueva dinero |
| `qr-pagos` | Cobro con QR, pasarelas y conciliación bancaria |
| `desembolsos-payouts` | Cualquier salida de dinero: retiro, entrega, devolución |
| `reembolsos-disputas` | Devolver un cobro o responder un contracargo |
| `proveedores-externos` | Al integrar, enrutar o dar de baja un proveedor |
| `facturacion-sin` | Comisiones, tarifario, impuestos y factura |
| `gobernanza-grupo` | Ciclo del grupo, cupos, turnos y acuerdos |
| `emparejamiento-ingreso` | Postulación, emparejamiento, invitaciones y referencias |
| `organizador-habilitacion` | Habilitar, medir, sancionar o dar de baja a un organizador |
| `sorteo-transparencia` | Sorteo verificable, cadena de bloques y reputación |
| `reputacion-social` | Insignias, reseñas y certificados de reputación |
| `garantia-mora-cobranza` | Mora, fondo de garantía, sanciones y cobranza |
| `alertas-riesgo-temprano` | Scoring, métricas de grupo y alertas antes del incumplimiento |
| `notificaciones-consentimiento` | Cualquier aviso al usuario |

## Cumplimiento y control

| Skill | Cuándo |
| --- | --- |
| `cumplimiento-uif` | Umbrales, debida diligencia, monitoreo y reportes de sospecha |
| `reportes-regulatorios` | Cualquier remisión periódica con plazo y acuse |
| `reclamos-consumidor` | Circuito de reclamos y transparencia de información |
| `observabilidad` | Rastro, indicadores, incidentes y eventos de riesgo |
| `gobierno-comites` | Comités, actas, riesgo de producto y oficial de cumplimiento |
| `indicadores-tablero` | Al crear un KPI o armar un tablero |
| `plazos-habiles` | Cada vez que aparezca 'X días hábiles' |

## Operación y entrega

| Skill | Cuándo |
| --- | --- |
| `ci-calidad` | Al configurar el CI o cuando un gate bloquee el merge |
| `resiliencia-rendimiento` | Al integrar un proveedor, dimensionar pools o medir |
| `despliegue-contenedores` | Docker, NGINX, Kubernetes y manifiestos |
| `respaldos-restauracion` | Respaldos, punto en el tiempo y ensayo de restauración |
| `documentacion-entregables` | Al documentar, agregar un endpoint o preparar la entrega |

## Reglas comunes a todas

1. **La bóveda manda.** Si el código y la especificación divergen, se corrige el
   código —o se corrige la bóveda primero, y en el mismo PR.
2. **Ninguna cifra regulatoria ni comercial en el código.** Van a catálogo, con
   vigencia y cita.
3. **Denegar por omisión.** Falta el límite, la licencia o la política: se rechaza.
4. **Nada se edita.** La corrección es un registro nuevo que compensa al anterior.
5. **La garantía vive en la base.** La aplicación valida para dar buen mensaje.
