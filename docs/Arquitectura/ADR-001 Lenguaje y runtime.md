---
tags:
  - arquitectura
  - adr
titulo: "ADR-001 — Lenguaje, runtime y framework de la API"
estado: superada por ADR-015
fecha: 2026-08-12
---

# ADR-001 — Lenguaje, runtime y framework de la API

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-015 Lenguaje, runtime y framework]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

El sistema ya está especificado: 12 módulos, 274 tablas, 566 claves foráneas, 87
casos de uso con criterios de aceptación y un catálogo de restricciones que la base
hace cumplir. Lo que falta no es diseñar: es **traducir** esa especificación a
código sin que se desincronice. Hay además tres artefactos que hablan del mismo
dominio —API, app del participante y backoffice de cumplimiento— y cada regla de
negocio aparece en los tres (un límite, un umbral, un estado de obligación).

## Decisión

**TypeScript sobre Node 22 LTS, con NestJS montado en el adaptador Fastify.**

- Un módulo de NestJS por módulo de la bóveda (`01_identidad`, …, `12_cumplimiento`).
- **Un archivo de aplicación por caso de uso**, con el código en el nombre:
  `CU31DevengarComision.ts`.
- La transacción se abre y se cierra en ese archivo, nunca en el repositorio.

## Motivo

**Las alternativas empatan en lo esencial.** Kotlin/Spring, Python/FastAPI y
TypeScript resuelven igual de bien los tres requisitos duros del modelo: convivir
con un DDL generado, controlar el `BEGIN…COMMIT` y poner el contexto de RLS en la
conexión correcta. Ninguna gana por ahí, porque en las tres la respuesta es la
misma: query builder o codegen, jamás un ORM dueño del esquema.

**Desempata el costo de traducir la especificación.** Con un solo lenguaje, el
contrato de cada caso de uso se escribe una vez y lo consumen API, app y backoffice
([[ADR-006 Contratos y validación]]). Con el backend en otro lenguaje se escribe
tres veces, y la tercera copia se desincroniza en la cuarta semana. En un sistema
donde un umbral mal copiado es un incumplimiento normativo —no un bug de UI— esa
duplicación es el riesgo más caro de todos.

**Es lo que el repo ya asumía.** La skill `implementar-desde-boveda` propone
`CU31DevengarComision.ts` y `CU31.spec.ts` desde antes de esta decisión.

**NestJS y no un framework mínimo** porque el sistema tiene 12 módulos y decenas de
casos de uso: la inyección de dependencias explícita es lo que permite sustituir la
pasarela QR, el servicio fiscal o el proveedor KYC por dobles en las pruebas sin
tocar el dominio. Fastify por rendimiento y por su validación por esquema.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Kotlin/Java + Spring Boot + jOOQ** | Técnicamente la más sólida a diez años y con `BigDecimal` nativo, pero deja el frontend en otro lenguaje y triplica la escritura de contratos. Vuelve a la mesa con licencia ASFI o core bancario. |
| **Python + FastAPI + SQLAlchemy Core** | El camino más corto al primer flujo, pero el tipado es opcional y aquí el tipado es control interno. Queda para los generadores (`scripts/*.py`). |
| **Go + sqlc + pgx** | La mejor pareja conceptual de `sql/`, pero mercado laboral local pequeño y decimales por biblioteca externa. |
| **Node sin framework (Express/Hono suelto)** | A esta escala la ausencia de estructura se paga en inconsistencia entre módulos. |

## Consecuencias

**A favor**

- Un `yarn test` corre backend, app y backoffice contra los mismos tipos.
- Un cambio de tarifario o de umbral se refleja en los tres artefactos de una vez.
- Contratar es más fácil y más barato que para Spring.

**En contra, y hay que asumirlo**

- **No hay decimal nativo.** Se paga con las tres reglas de [[ADR-005 Dinero y decimales]],
  que son obligatorias, no recomendadas.
- Menos credibilidad inmediata ante un auditor de sistemas bancario que Java. Se
  compensa con evidencia: pruebas de rechazo por restricción y trazas por `CU-NN`.
- El ecosistema Node cambia rápido: se fija Node LTS y se actualiza por versión
  mayor, con una decisión consciente, no por `^`.

## Qué revertiría esta decisión

Que el objetivo a doce meses pase a ser operar con **licencia ASFI e integración con
core bancario**. En ese escenario se escribe un ADR-00X que supera a este y mueve la
API a Spring Boot + jOOQ, dejando los clientes en TypeScript.

## Cómo se verifica

- [ ] Existe un archivo `CU<NN>*.ts` por cada caso de uso implementado.
- [ ] Ningún repositorio abre transacciones; solo las usa.
- [ ] Cada adaptador externo tiene una interfaz de dominio y un doble de prueba.
- [ ] `node --version` coincide con la versión LTS fijada en `.nvmrc` y en el CI.

## Ver también

[[Stack]] · [[_Arquitectura]] · [[Estructura del repositorio]] · [[ADR-002 Acceso a datos]]
