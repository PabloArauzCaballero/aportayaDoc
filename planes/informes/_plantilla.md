---
tags:
  - plan
  - informe
  - carril
titulo: "Carril <ID> — <módulo>"
ola: <N>
fase: <N>
modulo: <NN_nombre>
rama: carril/<ola>-<id>-<modulo>
estado: en curso
---

# Carril \<ID\> — \<módulo\>

**Fase** \<N\> · **Casos de uso** \<lista\> · **Máquina** \<nombre\>

> Este archivo lo escribe **solo este carril**. Ningún otro lo toca: es lo que evita
> el conflicto en `informe.md` con cinco máquinas trabajando a la vez.

## Casos de uso

| CU | Contrato | Dominio | Infra | Aplicación | HTTP | Pruebas | Gate |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| CU-\<NN\> | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ |

## Piezas declaradas por nivel

Antes de crear cada pieza se declara acá su nivel (§1 del estándar de ejecución).

| Pieza | Nivel | CU | Estado |
| --- | --- | :-: | :-: |

## Fronteras transaccionales respondidas

Una entrada por caso de uso, con las cinco preguntas contestadas **antes** de
implementar.

### CU-\<NN\>
1. **Todo junto o nada:**
2. **Fuera del commit:**
3. **Clave de idempotencia (cliente o proveedor):**
4. **Qué se bloquea y a qué granularidad:**
5. **Si el proceso muere tras el commit:**

## Supuestos declarados

Regla cero: ninguno silencioso. Lo que se asumió por falta de información no crítica
va acá, con su CU.

## Micro-PR abiertos al troncal

| Rama | Qué agrega | Estado |
| --- | --- | :-: |

## Bloqueos

Qué está esperando, de quién, desde cuándo.

## Matriz de gates

Se reporta **así, no en prosa** (`definicion-de-terminado`). La evidencia es la salida
real del comando, no un adjetivo.

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `CU<NN>Test.java`, n/n | ⬜ |
| Datos | Restricciones citadas con prueba de rechazo | n/n rechazos verificados | ⬜ |
| Seguridad | Prueba negativa de RLS | contexto ajeno ⇒ 0 filas | ⬜ |
| Plazos | Vencimiento y aviso previo | — | ⬜ |
| Arquitectura | Piezas por nivel, sin saltos | `aportaya/capas` | ⬜ |
| Operación | Health, readiness, trazas con `cu` y `usuario_id` | — | ⬜ |
| Entrega | Lint, tipos, pruebas, build | salida citada | ⬜ |

### Frases prohibidas sin evidencia

«Está listo» · «debería funcionar» · «ya está probado» · «es seguro». Se reemplazan
por el número que salió: *«14 criterios como pruebas, 6 rechazos de restricción,
`yarn test:integracion` en verde»*.

## Gate de salida — evidencia

Comandos **ejecutados**, con su resultado. No se marca sin correr.

- [ ] `yarn lint && yarn typecheck`
- [ ] `yarn test:unit && yarn test:integracion && yarn test:api`
- [ ] Cada criterio de aceptación con su `it()` nombrado igual
- [ ] Cada `R-XXX-nn` citado con prueba de rechazo
- [ ] Checklist de PR de §12 del estándar
- [ ] Gate específico de la fase

## Ver también

[[informe]] · [[07 Carriles de trabajo concurrente]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]]
