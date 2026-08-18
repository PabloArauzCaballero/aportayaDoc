---
name: decisiones-adr
description: "Registrar una decisión técnica de AportaYa como ADR y evaluar librerías antes de adoptarlas: cuándo una decisión merece ADR, la plantilla obligatoria, cómo se supera un ADR anterior y la matriz de librerías. Úsala al elegir una librería o un proveedor, al cambiar algo caro de revertir, o cuando alguien proponga apartarse de una decisión ya tomada."
---

# Decisiones y ADR

Las decisiones de este proyecto viven en `docs/Arquitectura/`, una por documento, con
**su motivo y lo que la revertiría** ([[_Arquitectura]]). Un ADR no se edita para
cambiar de opinión: se escribe uno nuevo que **supera** al anterior, y el viejo queda
marcado como superado con la fecha. La historia de por qué se decidió algo es
evidencia, y acá la evidencia no se borra.

## Cuándo hace falta un ADR

Cuando la decisión es **cara de revertir**:

| Situación | ADR |
| --- | --- |
| Cambia la forma del código en muchos lugares | Sí |
| Ata el sistema a un proveedor o a una librería central | Sí |
| Afecta cómo se garantiza una restricción o un plazo legal | Sí |
| Toca dinero, evidencia o datos personales | Sí |
| Se aparta de un ADR vigente | Sí, y debe superarlo explícitamente |
| Elección de librería de uso puntual | No: matriz de librerías |
| Estructura interna de un módulo | No: README del módulo |

Si dudás: ¿cuánto costaría deshacerlo en seis meses? Si la respuesta es "una semana o
más", escribí el ADR.

## Plantilla

```md
---
tags: [arquitectura, adr]
titulo: "ADR-0NN — <decisión>"
estado: aceptada | superada por ADR-0MM | rechazada
fecha: AAAA-MM-DD
---

# ADR-0NN — <decisión>

## Contexto        ← qué obliga a decidir, con datos del proyecto, no genéricos
## Decisión        ← qué se hace, en imperativo y sin ambigüedad
## Motivo          ← por qué esta y no otra; el argumento que realmente desempató
## Alternativas descartadas   ← tabla: alternativa | por qué no
## Consecuencias   ← a favor y **en contra**; lo que se acepta pagar
## Qué revertiría esta decisión
## Cómo se verifica ← casillas comprobables en el código o el CI
## Ver también
```

Un ADR sin la sección de consecuencias en contra está incompleto: si una decisión no
cuesta nada, probablemente no era una decisión.

`scripts/verificar_boveda.py` lo comprueba en el CI: número sin repetir, estado
válido, las seis secciones obligatorias y presencia en el índice de
[[_Arquitectura]]. Un ADR incompleto **bloquea el merge**, igual que un caso de uso
sin sus secciones.

## Matriz de librerías

Toda librería relevante se registra en `docs/decisions/matriz-de-librerias.md`:

| Campo | Qué se anota |
| --- | --- |
| Responsabilidad | Qué problema resuelve, en una línea |
| Candidatas | Las que se miraron de verdad |
| Versión evaluada | Y fecha |
| Compatibilidad | JDK 21, Spring Boot, PostgreSQL, jOOQ |
| Mantenimiento | Última publicación, ritmo de correcciones, tamaño de la comunidad |
| Seguridad | Vulnerabilidades conocidas, superficie que agrega |
| Licencia | Y si es compatible con el producto |
| Salida | Cómo se reemplaza si hay que sacarla |
| Decisión | Cuál se eligió y por qué |

Reglas:

- **Una responsabilidad, una librería.** Dos que hacen lo mismo es deuda inmediata.
- Nada de versiones alpha/beta en producción sin ADR y plan de reversión.
- Toda dependencia nueva se justifica en el PR; sin justificación, no entra.
- Antes de agregar una: ¿lo resuelve la plataforma, PostgreSQL o diez líneas propias?

## Cuando alguien propone apartarse de un ADR

1. Se lee el ADR vigente: casi siempre la objeción ya está en "alternativas
   descartadas" o en "consecuencias en contra".
2. Si la objeción es nueva y el contexto cambió → se escribe el ADR que lo supera.
3. Mientras tanto, **el ADR vigente manda** y el código lo respeta.

Lo que no se hace es aplicar la excepción en un módulo y dejar dos criterios
conviviendo: eso convierte cualquier revisión en una discusión de gustos.

## Lineamientos externos

Un prompt, un estándar o una guía traída de otro proyecto **no supera** un ADR por sí
sola. Se reconcilia y se deja escrito qué se adoptó y qué se descartó, con el motivo:
[[Lineamientos adoptados y descartados]].

## Ver también

`plan-por-fases` · `definicion-de-terminado` · `revision-codigo` · `proveedores-externos` ·
`docs/Arquitectura/_Arquitectura.md` · `docs/Arquitectura/Método de arquitectura.md`
