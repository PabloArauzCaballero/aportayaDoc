---
tags:
  - moc
  - arquitectura
  - prompts
titulo: "Prompts generalistas de desarrollo"
fecha_revision: 2026-08-18
---

# Prompts generalistas

> Tres prompts **reutilizables en cualquier proyecto**, no solo en este. Codifican el
> [[Método de arquitectura]] y una sola regla estructural que los atraviesa:
> **todo se divide siempre en átomos, moléculas y organismos**, en el frontend y en
> el backend.

> [!important] En Pasanaku no se usan estos prompts: se usan las skills
> Desde el 2026-08-18, lo que una máquina de carril carga es **`arrancar-carril`**, y
> lo que decide antes de escribir es **`frontera-transaccional`**. Estos tres
> documentos quedan como la **destilación portable** —lo que uno se llevaría a otro
> proyecto— y **no se leen al programar acá**.
>
> El motivo es de economía, no de estilo: un prompt pegado a mano se copia mal, se
> recorta y envejece distinto en cada máquina. Una skill viaja con el clon, se
> versiona y tiene una sola versión.

## Qué usa cada quién

| Si sos… | Cargá | No leas |
| --- | --- | --- |
| Una máquina que toma un carril de Pasanaku | `arrancar-carril`, y lo que ella mande | Estos tres documentos |
| Quien decide la frontera de un caso de uso | `frontera-transaccional` | [[Prompt de backend]] |
| Quien lleva estas ideas a otro proyecto | Estos tres, completos | Las skills: son de este repositorio |

## Los tres, y su equivalente acá

| Prompt | Qué garantiza | Su equivalente en Pasanaku |
| --- | --- | --- |
| [[Prompt general de desarrollo]] | Temperatura 0, KISS, composición atómica, nombres, errores, pruebas, seguridad | `codigo-limpio` · `arquitectura-atomica` · `glosario-dominio` |
| [[Prompt de backend]] | Frontera transaccional, garantías en la base, idempotencia, bordes externos | `frontera-transaccional` · `back-spring` · `datos-jooq` · `servicios-y-sagas` |
| [[Prompt de frontend]] | Átomos visuales, tokens, estados obligatorios, dominio separado de la vista | `disenar-frontend` · `movil-expo` · `web-backoffice` |

**El general manda.** El especializado añade; si alguna vez contradice al general,
gana el general. Y si cualquiera de los tres contradice un ADR vigente de este
repositorio, **gana el ADR**.

## Por qué existen

Un asistente sin restricciones produce el mismo código que un equipo sin
arquitectura: funciona en la demostración y se vuelve inmantenible en el tercer mes.
Estos prompts trasladan al asistente las tres decisiones que más se rompen cuando
nadie las escribe: **no inventar**, **no mezclar niveles** y **no poner la garantía
en el lugar equivocado**.

## Ver también

[[Método de arquitectura]] · [[ADR-023 Composición atómica en Java]] · [[_Arquitectura]]
