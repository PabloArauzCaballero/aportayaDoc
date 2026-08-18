---
tags:
  - arquitectura
  - adr
titulo: "ADR-006 — Contratos y validación"
estado: superada por ADR-020
fecha: 2026-08-12
---

# ADR-006 — Contratos y validación

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-020 Contratos OpenAPI primero]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

Cada caso de uso define entrada, salida, validaciones y errores esperados. Esa
definición se usa en cuatro lugares: el borde HTTP del backend, la app, el
backoffice y las pruebas. Si se escribe cuatro veces, en la cuarta semana ya no
coinciden — y aquí una divergencia no es un formulario feo, es un límite operativo
que se validó en el cliente pero no en el servidor.

Existe además una regla de la bóveda que este ADR no puede debilitar: la validación
de aplicación **da buen mensaje**, no garantiza. Lo que protege dinero, plazos o
evidencia lo garantiza la base ([[Restricciones]]).

## Decisión

**Un paquete `contratos` con esquemas Zod, escrito una vez y consumido por los tres
artefactos.** El documento OpenAPI se **deriva** de esos esquemas; no se escribe a
mano.

- Un archivo por caso de uso: `contratos/CU31.ts` exporta `EntradaCU31`,
  `SalidaCU31` y los códigos de error del caso.
- El backend valida en el borde con el mismo esquema que el cliente usa para
  construir el formulario.
- Los tipos de TypeScript se **infieren** del esquema (`z.infer`), nunca se declaran
  aparte.
- Los importes se declaran como *string* con validación de formato, no como número
  ([[ADR-005 Dinero y decimales]]).

## Motivo

**Porque el contrato es la traducción literal del caso de uso.** El caso de uso ya
dice qué campos entran, cuáles son obligatorios y qué se rechaza; el esquema es esa
misma frase en código, y el nombre del archivo (`CU31`) hace la trazabilidad obvia
sin herramientas.

**Porque una sola definición no puede desincronizarse.** Si mañana el umbral de un
formulario PCC-01 cambia (CU-41), cambia en un archivo y el compilador señala cada
lugar del sistema que hay que revisar. Ese es todo el beneficio de haber elegido un
solo lenguaje; este paquete es donde se cobra.

**Porque validar en el borde es requisito de idempotencia.** La clave de
idempotencia llega en la entrada y **se valida antes de cualquier escritura**: eso
solo es posible si el borde tiene un esquema estricto que rechaza lo desconocido.

**Zod y no otra cosa** porque valida y tipa a la vez, corre igual en Node y en el
cliente, y permite refinamientos con mensaje propio —que es exactamente lo que la
bóveda pide de la capa de aplicación: rechazar con un mensaje que una persona
entienda, sabiendo que la barrera real está más abajo.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **OpenAPI escrito a mano + generador de clientes** | El documento se vuelve la copia que nadie actualiza; obliga a un paso de generación en cada cambio. |
| **DTOs con `class-validator`** (lo habitual en NestJS) | Decoradores sobre clases no se comparten con el frontend y duplican el tipo. |
| **tRPC** | Excelente ergonomía, pero ata el cliente al backend y complica publicar una API estable para terceros (pasarela, auditor). |
| **Validar solo en el servidor** | El cliente pierde la posibilidad de dar buen mensaje y se llena de reglas copiadas a mano. |

## Consecuencias

**A favor**

- Un cambio de contrato rompe la compilación de quien lo consume mal.
- La documentación OpenAPI está siempre viva, porque es un derivado.
- Las pruebas construyen entradas válidas desde el mismo esquema.

**En contra**

- El paquete `contratos` es una dependencia común: un cambio incompatible obliga a
  desplegar API y clientes coordinados. Se maneja con versionado de ruta
  (`/api/v1`) y esquemas aditivos.
- Zod agrega peso al bundle del cliente: se importa por caso de uso, no en bloque.

## Reglas de uso

| Regla | Cómo se ve |
| --- | --- |
| Un archivo de contrato por caso de uso | `contratos/CU21.ts` |
| Los tipos se infieren, no se escriben | `type EntradaCU21 = z.infer<typeof EntradaCU21Schema>` |
| Rechazar lo desconocido | `.strict()`: un campo de más es un error, no algo que se ignora |
| Los errores tienen código, no solo texto | `AP-CU21-03`, mapeado al criterio del caso de uso |
| Ninguna regla que proteja dinero vive solo acá | Debe existir su restricción `R-XXX-nn` |
| Los importes son *string* con formato | `z.string().regex(/^\d+\.\d{2}$/)` |

## Cómo se verifica

- [ ] Existe `contratos/CU<NN>.ts` por cada caso de uso implementado.
- [ ] El OpenAPI publicado se genera en el CI; si difiere del versionado, falla.
- [ ] Toda regla de negocio del contrato cita la restricción que la respalda, o
      declara explícitamente que es solo de presentación.
- [ ] Prueba de contrato: la app compila contra el esquema publicado por la API.

## Ver también

[[ADR-001 Lenguaje y runtime]] · [[ADR-004 Frontend]] · [[ADR-008 Pruebas]] · [[_CasosDeUso]]
