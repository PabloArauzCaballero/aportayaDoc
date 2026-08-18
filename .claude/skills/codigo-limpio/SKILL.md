---
name: codigo-limpio
description: "Estándar de código limpio de AportaYa: nombres, tamaño de funciones, comentarios, manejo de errores, condicionales, efectos secundarios, dependencias y simplicidad KISS. Úsala al escribir cualquier código nuevo, al refactorizar y antes de abrir un PR. Es el criterio con el que se acepta o se rechaza código en revisión."
---

# Código limpio

Estándar único para backend, app y backoffice. La estructura la define
`arquitectura-atomica`; esta skill define **cómo se escribe dentro de cada pieza**.

## Regla cero: no inventar

Cero requisitos, entidades, columnas, endpoints o reglas inventadas. Si falta
información **crítica** —regla de negocio, estado válido, permiso, contrato,
idempotencia, plazo legal— se para y se pregunta. Si el vacío no es crítico, se
avanza **declarando el supuesto**. Un supuesto escrito se corrige; uno silencioso se
descubre en producción.

En este proyecto casi nunca falta: la respuesta está en el caso de uso, en
[[Restricciones]] o en [[Cumplimiento]]. Buscar ahí es más rápido que suponer.

## Nombres

| Regla | Sí | No |
| --- | --- | --- |
| Específico y del dominio | `obligacionVencida`, `devengarComision` | `data`, `info`, `temp`, `item2` |
| Sin genéricos sin contexto | `RepositorioDeAportes` | `Manager`, `Helper`, `Processor`, `Utils` |
| El nombre dice qué es, no cómo se hizo | `saldoDisponible` | `resultadoQuery3` |
| Booleanos afirmativos | `estaVigente`, `puedeCobrar` | `noEsInvalido` |
| Español del dominio, como la bóveda | `participante`, `cuota` | mezclar `member`/`participante` |

El vocabulario del código es el de la bóveda. Si el modelo dice `obligacion_aporte`,
el código no inventa `Payment`.

## Funciones

- **Una responsabilidad.** Si necesitás un comentario para separar secciones dentro
  de una función, son dos funciones.
- **Sin banderas booleanas** que cambien el comportamiento: dos funciones con nombre.
- **Pocos parámetros**; si son muchos y relacionados, es un objeto de valor.
- **Retorno temprano** para casos borde; nada de pirámides de `else`.
- **El reloj, el azar y los identificadores se inyectan.** `new Date()` dentro de un
  cálculo es una prueba no determinista esperando.

## Condicionales

- Nada de números mágicos: un umbral regulatorio dentro de un `if` es un defecto de
  cumplimiento (skill `norma-nueva`), va a catálogo.
- Condiciones complejas se nombran: `const superaUmbralUif = …` antes del `if`.
- Estados como tipos, no como cadenas sueltas comparadas con `===`.
- Sin `else` cuando el `if` retorna.

## Comentarios

El código dice **qué**; el comentario dice **por qué**, y solo cuando el porqué no es
evidente. Los comentarios que valen la pena en este proyecto son casi siempre uno de
estos tres:

```ts
// R-BIL-04: el saldo se deriva; nunca se actualiza en su lugar.
// CU-31 paso 4: el tarifario se congela al crear el grupo, no al cobrar.
// UIF: el umbral llega del catálogo con vigencia; no se compara contra constante.
```

Prohibidos: comentarios que repiten el código, código comentado (para eso está git),
`TODO` sin explicación ni dueño.

## Errores

| Regla | Cómo se ve |
| --- | --- |
| Fallar temprano y ruidosamente | Estado inválido ⇒ excepción, no continuar con datos dudosos |
| Nunca `catch` vacío | Ni tragar excepciones "para que no se caiga" |
| Error de negocio ≠ defecto | El primero se maneja con código (`AP-CU31-02`); el segundo se registra y se propaga |
| El mensaje al usuario no filtra | Sin SQL, sin nombres de tabla, sin trazas, sin datos personales |
| Rechazo de la base se traduce | Al código `R-XXX-nn` documentado, no al texto crudo de PostgreSQL |

## Efectos secundarios y dependencias

- Lo que una pieza necesita **se le pasa**; nada de dependencias ocultas ni estado
  global mutable.
- Todo borde externo entra por una interfaz de dominio con su adaptador.
- Sin `import` que crucen niveles hacia arriba (`arquitectura-atomica`).
- Sin dependencias nuevas sin necesidad real y sin declararlo en el PR.

## Simplicidad (KISS)

> La solución más simple que cumple el caso de uso **completo**, y ni una capa más.

| Regla | En la práctica |
| --- | --- |
| Se abstrae al tercer uso | Con dos ejemplos se adivina el patrón; con tres se ve |
| Sin patrones por moda | Un patrón entra si resuelve un problema presente |
| Sin generalización prematura | Nada de "por si mañana hay otra moneda" |
| Sin capa que solo reenvía | Si no transforma ni protege, sobra |
| Especializado antes que genérico | Un caso de uso claro vale más que un servicio genérico a medias |

Simple no es escaso. Quitar una restricción, una prueba de rechazo o una traza **no
simplifica: degrada**.

## Formato y automatización

Lo mecánico no se discute en revisión: lo resuelve la herramienta.

- Prettier y ESLint con la configuración del repo; el CI falla si no pasa.
- TypeScript en modo estricto; `any` requiere justificación escrita.
- Reglas de lint propias del proyecto: prohibición de `number` en dinero
  (`dinero-decimal`), prohibición de consultas fuera de `conContexto`, límite de
  dependencias entre niveles.
- Un `eslint-disable` sin comentario que explique el porqué se rechaza en revisión.

## Antes de abrir el PR

- [ ] Las piezas nuevas están declaradas por nivel.
- [ ] Ningún nombre genérico sin contexto.
- [ ] Ninguna función con dos responsabilidades.
- [ ] Ningún número regulatorio en el código.
- [ ] Errores con código y mensaje humano.
- [ ] Sin `TODO` huérfanos, sin código comentado, sin `console.log`.
- [ ] Lint, tipos y pruebas en verde localmente.

## Ver también

`glosario-dominio` · `arquitectura-atomica` · `revision-codigo` · `pruebas-cu` · `dinero-decimal` ·
`docs/Arquitectura/Prompts/Prompt general de desarrollo.md`
