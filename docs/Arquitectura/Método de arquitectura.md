---
tags:
  - arquitectura
  - metodo
titulo: "Método de arquitectura — cómo se diseña siempre"
fecha_revision: 2026-08-12
---

# Método de arquitectura

> **Cómo se hace la arquitectura en este proyecto, siempre.** No importa si lo que
> viene es una pantalla, un endpoint, un reporte o un módulo entero: el orden es
> este, y no se salta ningún paso. Un diseño que no pasó por acá no se implementa.

## Regla cero

> **No se diseña contra la memoria de una reunión: se diseña contra el caso de uso.**

Si el caso de uso no existe, el primer entregable es el caso de uso (skill
`caso-de-uso`), no el código. Si existe pero está incompleto o se contradice con el
modelo, **eso es el primer bug**: se corrige la bóveda antes de diseñar.

Y su corolario, que evita el error más caro:

> **Ante información faltante, se para y se pregunta.** Cero requisitos inventados,
> cero entidades nuevas por conveniencia, cero supuestos sin declarar. Si el vacío
> no es crítico, se avanza **documentando el supuesto** en el diseño.

## Los ocho pasos

### 1 · Leer la cadena completa, en orden

```
Norma → Caso de uso → Restricción → Modelo → Esquema → ADR vigente
```

[[Cumplimiento]] → [[_CasosDeUso]] → [[Restricciones]] → [[_Entidades]] → `sql/` →
[[_Arquitectura]]. Nunca al revés: leer primero el modelo lleva a diseñar sobre
tablas sin entender qué problema resuelven.

### 2 · Identificar el organismo y trazar la frontera transaccional

Un caso de uso = **un organismo** = **una transacción**. Antes de escribir nada se
responde por escrito:

- ¿Qué es lo que tiene que pasar todo junto o no pasar?
- ¿Qué queda **fuera** del `COMMIT` y por lo tanto va al outbox?
- ¿Qué pasa si el usuario reintenta? ¿Cuál es la clave de idempotencia?
- ¿Qué se bloquea, y a qué nivel, si dos personas hacen esto a la vez?

### 3 · Descomponer en átomos, moléculas y organismos

Obligatorio, en frontend y en backend ([[ADR-023 Composición atómica en Java]]). Para cada
pieza que aparezca en el diseño, tres preguntas:

| Pregunta | Si la respuesta es… | Entonces la pieza es |
| --- | --- | --- |
| ¿Necesita IO, red o base? | No | **Átomo** (función pura, objeto de valor, componente visual) |
| ¿Depende de **un** colaborador y hace **una** cosa? | Sí | **Molécula** (repositorio, adaptador, hook, campo compuesto) |
| ¿Orquesta varias piezas para cumplir un objetivo completo? | Sí | **Organismo** (caso de uso, sección de pantalla) |

Si una pieza no cabe en ninguno, está haciendo de más: se parte. Si cabe en dos,
está mezclando niveles: se parte igual.

### 4 · Decidir dónde vive cada garantía

Esta es la decisión que más se equivoca. El criterio no admite matices:

| Si la regla… | Vive en | Y la aplicación… |
| --- | --- | --- |
| Protege dinero, un asiento o una conciliación | **Base de datos** | valida igual, para dar buen mensaje |
| Impide doble cobro o doble acreditación | **Base** (`UNIQUE` de idempotencia) | valida la clave antes de escribir |
| Guarda o limita un plazo legal | **Base** | persiste el plazo al crear |
| Impide editar algo inmutable | **Base** (`REVOKE` + trigger) | ni lo intenta |
| Es un umbral, límite o tarifa regulatoria | **Catálogo sembrado**, con vigencia | lo lee, nunca lo hardcodea |
| Es preferencia de interfaz o mensaje | **Aplicación** | única dueña |

Toda regla que caiga en las cuatro primeras filas necesita su código `R-XXX-nn`
(skill `restriccion`) **antes** de que exista el servicio.

### 5 · Definir los bordes

Todo lo que sale del sistema —pasarela, mensajería, servicio fiscal, KYC— entra por
una **interfaz de dominio** con su adaptador. Por cada borde se define: clave de
idempotencia, qué se hace ante timeout, ante duplicado y ante respuesta fuera de
orden, y qué queda como evidencia de cada intento.

### 6 · Escribir el contrato antes que la implementación

El esquema de entrada y salida del caso de uso, con sus códigos de error, como
operación en `servicios/<servicio>/src/main/resources/openapi/<servicio>.yaml`
([[ADR-020 Contratos OpenAPI primero]]). Escribirlo primero obliga a cerrar preguntas
que de otro modo aparecen a mitad del código — y además **desbloquea al carril que
necesita este servicio y todavía no lo tiene**: puede generar el cliente y programar
contra un doble.

### 7 · Traducir los criterios de aceptación a pruebas

Uno a uno, más la prueba de rechazo de cada restricción citada, la de reintento y —si
hay dinero— la de cuadre ([[ADR-026 Pruebas de un sistema distribuido]]). Las pruebas se escriben con el
diseño, no después: son la definición de terminado, no un trámite.

### 8 · ¿Es una decisión cara de revertir?

Si cambia la forma del código en muchos lugares, ata a un proveedor o afecta cómo se
garantiza una restricción → **se escribe un ADR** con contexto, decisión, motivo,
alternativas descartadas, consecuencias y qué la revertiría. Si no, se implementa y
se documenta en el README del módulo.

## Criterio de simplicidad

> **La solución más simple que cumple el caso de uso completo, y ni una capa más.**

| Regla | En la práctica |
| --- | --- |
| **Se abstrae al tercer uso** | Dos usos parecidos se copian y se espera; al tercero se ve el patrón real |
| **Sin patrones por moda** | Un patrón entra si resuelve un problema que ya existe, no uno imaginado |
| **Sin generalización prematura** | Nada de "por si mañana soportamos otra moneda" hasta que exista el caso de uso |
| **Sin capa sin valor** | Si una capa solo reenvía llamadas, sobra |
| **Especializado antes que genérico** | Un caso de uso claro vale más que un servicio genérico que sirve a cinco a medias |

Simple no es escaso: es **la menor cantidad de piezas que sostiene todas las
garantías**. Quitar una restricción no simplifica, degrada.

## Señales de que el diseño está mal

| Señal | Qué significa | Qué hacer |
| --- | --- | --- |
| El servicio necesita una columna que no existe | Se está rediseñando el modelo por la ventana | Skill `boveda-modelo` |
| Aparece un número regulatorio dentro de un `if` | La norma se está enterrando en el código | Skill `norma-nueva`: va a catálogo |
| "Eso lo valida el backend" sobre algo que mueve dinero | La garantía está en el lugar equivocado | Skill `restriccion` |
| El caso de uso real difiere del escrito | La bóveda dejó de ser verdad | Se actualiza el caso, no se deja divergir |
| Un archivo pasa de ~200 líneas | Hay varios niveles mezclados | Volver al paso 3 |
| Hay que leer tres archivos para saber qué hace uno | Las dependencias no van en una sola dirección | Volver al paso 3 |
| La prueba necesita `sleep` o red real | El borde no está detrás de una interfaz | Volver al paso 5 |
| Aparece `utils.ts` | Hay átomos sin nombre | Darles nombre y dueño |

## Cómo se aplica con asistencia de IA

Los prompts generalistas de [[Prompts/_Prompts|`Prompts/`]] codifican este método:
uno general de desarrollo, uno de backend y uno de frontend, los tres con la división
en átomos, moléculas y organismos como regla obligatoria. Se usan tal cual, y para
este proyecto se combinan con las skills de `.claude/skills/`.

## Ver también

[[_Arquitectura]] · [[ADR-023 Composición atómica en Java]] · [[Estructura del repositorio]] · [[Restricciones]]
