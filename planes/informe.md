---
tags:
  - plan
  - informe
titulo: "Informe consolidado — AportaYa"
actualizado: 2026-08-13
---

# Informe consolidado — backend y frontend

> **Este archivo solo agrega estado.** El detalle de cada carril vive en
> `planes/informes/carril-P<N>.md`, uno por puesto, para que cinco carriles
> concurrentes no se pisen ([[07 Carriles de trabajo concurrente]] §5).
> Se actualiza **al cerrar cada tramo**, no en cada commit.

## Avance por tramo

La secuencia real de ejecución sobre las cinco máquinas está en
[[17 Plan de acción secuencial · coordinación de cinco máquinas]] §5. El tramo —no la
ola— es la unidad de planificación y el punto de sincronización.

| Tramo | P1 · Mac M5 | P2 · Ubuntu | P3 · Legion | P4 · Dell A | P5 · Dell B | Estado |
| :-: | --- | --- | --- | --- | --- | :-: |
| **T0** | BE Fase 0 + contratos | verifica el gate | preparación | preparación | landing | ⬜ |
| **T1** | BE Fases 1 y 2 | acompaña troncal | FE-F0 móvil | FE-F0 backoffice | FE-F0 web | ⬜ |
| **T2** | 1A identidad | 1B contable | FE-F1 `packages/ui` | 1C habilitación | 1D notificaciones | ⬜ |
| **T3** | 2C grupos | 2A billetera | FE-F2 → F6 shells | 2D auditoría | 2B tarifas | ⬜ |
| **T4** | FE-F3 móvil identidad | 3A aportes | 3B transparencia | 3D entregas 10a | 2E organizador | ⬜ |
| **T5** | FE-F4 móvil billetera | 4A entregas 10b | 4B garantía | 3C cumplimiento | FE-F9 sitio | ⬜ |
| **T6** | FE-F5 móvil pasanaku | BE fase 17 | FE-F7 operación | FE-F8 cumplimiento | FE-F10 SEO | ⬜ |
| **T7** | FE-F5 (sigue) | despliegue | FE-F7 + E2E Android | FE-F8 (sigue) | FE-F11 GEO | ⬜ |
| **T8** | FE-F12 tiendas | producción | **5A contabilidad ERP** ★ | **5B publicidad** ★ | publicación web | ⬜ |
| **T9** | correcciones de tienda | operación | **FE-F13 backoffice ERP** ★ | **FE-F14 backoffice publicidad** ★ | medición GEO | ⬜ |

★ carriles nuevos: cubren los 12 CU que ningún plan nombraba (defecto 5 de
[[17 Plan de acción secuencial · coordinación de cinco máquinas]]).

**El parque lo opera una sola persona.** Máximo dos carriles en primer plano por
tramo; los otros tres corren sobre especificación cerrada y se visitan por ronda
(§10). El detalle de cada carril —dependencias, entregas, gate propio y dónde se
rompe— está en [[18 Fichas de carril · las 38 unidades de trabajo]].

## Deuda declarada

Carriles listos que no entraron en su tramo, con el tramo donde se pagan
([[17 Plan de acción secuencial · coordinación de cinco máquinas]] delta 5). Una deuda
sin tramo asignado es un carril que no se hace.

| Carril | Tramo de origen | Tramo de pago | Estado |
| :-: | :-: | :-: | :-: |
| 2E organizador | T3 | T4 | ⬜ |
| F9 sitio público | T3 | T5 | ⬜ |
| 3C cumplimiento | T4 | T5 | ⬜ |

## Avance del backend

| Ola | Carriles | Fases | Estado | Cerrada el |
| :-: | :-: | --- | :-: | --- |
| **0** | T (troncal) | 0, 1, 2 | ⬜ pendiente | — |
| **1** | A · B · C · D | 3, 5, 4, 12 | ⬜ pendiente | — |
| **2** | A · B · C · D · E | 6, 7, 8, 15, 14 | ⬜ pendiente | — |
| **3** | A · B · C · D | 9, 13, 16, 10a | ⬜ pendiente | — |
| **4** | A · B | 10b, 11 | ⬜ pendiente | — |
| **5** | T | 17 | ⬜ pendiente | — |

Estados: ⬜ pendiente · 🟡 en curso · ✅ cerrada con gate ejecutado · 🔴 bloqueada

## Carriles

| Carril | Ola | Tramo | Fase | Módulo | Puesto · Máquina | Estado |
| :-: | :-: | :-: | :-: | --- | --- | :-: |
| T | 0 | T0–T1 | 0–2 | troncal | **P1** · Mac M5 | ⬜ |
| A | 1 | T2 | 3 | 01 identidad | **P1** · Mac M5 | ⬜ |
| B | 1 | T2 | 5 | 03 contable | **P2** · Ubuntu | ⬜ |
| C | 1 | T2 | 4 | 12 habilitación | **P4** · Dell A | ⬜ |
| D | 1 | T2 | 12 | 05 notificaciones | **P5** · Dell B | ⬜ |
| A | 2 | T3 | 6 | 10 billetera | **P2** · Ubuntu | ⬜ |
| B | 2 | T3 | 7 | 11 tarifas | **P5** · Dell B | ⬜ |
| C | 2 | T3 | 8 | 02 grupos | **P1** · Mac M5 | ⬜ |
| D | 2 | T3 | 15 | 09 auditoría | **P4** · Dell A | ⬜ |
| E | 2 | T4 | 14 | 07 organizador | **P5** · Dell B | ⬜ |
| A | 3 | T4 | 9 | 03 aportes | **P2** · Ubuntu | ⬜ |
| B | 3 | T4 | 13 | 06 transparencia | **P3** · Legion | ⬜ |
| C | 3 | T5 | 16 | 12 cumplimiento | **P4** · Dell A | ⬜ |
| D | 3 | T4 | 10a | 04 entregas (CU-18) | **P4** · Dell A | ⬜ |
| A | 4 | T5 | 10b | 04 entregas | **P2** · Ubuntu | ⬜ |
| B | 4 | T5 | 11 | 08 garantía | **P3** · Legion | ⬜ |
| T | 5 | T6–T8 | 17 | convergencia y despliegue | **P2** · Ubuntu | ⬜ |
| **5A** ★ | — | T8 | **18** | 13 contabilidad ERP · CU-100–106 | **P3** · Legion | ⬜ |
| **5B** ★ | — | T8 | **19** | 14 publicidad y campañas · CU-110–114 | **P4** · Dell A | ⬜ |

Cada **puesto** lleva un solo informe, `informes/carril-P<N>.md`, copiado de
`informes/_plantilla.md`, con una sección por carril que atraviesa.

## Avance del frontend

| Ola | Carriles | Fases | Estado | Cerrada el |
| :-: | :-: | --- | :-: | --- |
| **F0** | T (troncal) | F0, F1 | ⬜ pendiente | — |
| **F1** | M · B · W | F2, F6, F9 | ⬜ pendiente | — |
| **F2** | M1 · M2 · B1 · W1 · W2 | F3, F4, F7, F10, F11 | ⬜ pendiente | — |
| **F3** | M3 · B2 | F5, F8 | ⬜ pendiente | — |
| **F4** | T | F12 | ⬜ pendiente | — |

| Carril | Ola | Tramo | Fase | Producto | Puesto · Máquina | Estado |
| :-: | :-: | :-: | :-: | --- | --- | :-: |
| T | F0 | T1 | F0 · andamiaje móvil | andamiaje | **P3** · Legion | ⬜ |
| T | F0 | T1 | F0 · andamiaje backoffice | andamiaje | **P4** · Dell A | ⬜ |
| T | F0 | T1 | F0 · andamiaje web | andamiaje | **P5** · Dell B | ⬜ |
| T | F0 | T2 | F1 | sistema de diseño | **P3** · Legion | ⬜ |
| M | F1 | T3 | F2 | shell móvil | **P3** · Legion | ⬜ |
| B | F1 | T3 | F6 | shell backoffice | **P3** · Legion | ⬜ |
| W | F1 | T5 | F9 | sitio público | **P5** · Dell B | ⬜ |
| M1 | F2 | T4 | F3 | móvil · identidad | **P1** · Mac M5 | ⬜ |
| M2 | F2 | T5 | F4 | móvil · billetera | **P1** · Mac M5 | ⬜ |
| B1 | F2 | T6–T7 | F7 | backoffice · operación | **P3** · Legion | ⬜ |
| W1 | F2 | T6 | F10 | **SEO** | **P5** · Dell B | ⬜ |
| W2 | F2 | T7 | F11 | **GEO** | **P5** · Dell B | ⬜ |
| M3 | F3 | T6–T7 | F5 | móvil · pasanaku | **P1** · Mac M5 | ⬜ |
| B2 | F3 | T6–T7 | F8 | backoffice · cumplimiento | **P4** · Dell A | ⬜ |
| T | F4 | T8 | F12 | publicación | **P1** · Mac M5 | ⬜ |
| **F13** ★ | — | T9 | **F13** | backoffice · contabilidad ERP | **P3** · Legion | ⬜ |
| **F14** ★ | — | T9 | **F14** | backoffice · publicidad | **P4** · Dell A | ⬜ |

Las olas de frontend van **una detrás** de las de backend: consumen el **contrato**
OpenAPI, no la implementación ([[16 Carriles de frontend]] §3). La Ola F0 se parte en tres
andamiajes concurrentes —tres directorios nuevos, colisión cero— por el delta 2 de
[[17 Plan de acción secuencial · coordinación de cinco máquinas]]; `packages/ui` (F1)
sigue siendo de un solo puesto.

## Casos de uso

**0 de 99 implementados.** Un CU cuenta como implementado cuando todos sus criterios
de aceptación tienen su `it()` con el mismo nombre y todas sus restricciones citadas
tienen prueba de rechazo.

> **Eran 87 hasta que se agregaron `13_contabilidad_erp` (CU-100–106) y
> `14_publicidad_campanas` (CU-110–114).** Los tres documentos de plan seguían
> contando 87 y ninguno los cubría: es el defecto 5 de
> [[17 Plan de acción secuencial · coordinación de cinco máquinas]]. Los doce están
> asignados a los carriles `5A`, `5B`, `F13` y `F14`, y **faltan escribir los cuatro
> documentos de fase correspondientes** (fases 18, 19, F13 y F14).

| Bloque | CU | Carriles | Tramo |
| --- | :-: | --- | :-: |
| Núcleo del pasanaku | 87 | los 19 originales | T0–T7 |
| Contabilidad ERP | 7 | `5A` · `F13` | T8 · T9 |
| Publicidad y campañas | 5 | `5B` · `F14` | T8 · T9 |

## Hitos

| Hito | Tramo | Estado |
| --- | :-: | :-: |
| El pipeline transversal funciona (las diez pruebas de `CU-00`) | T1 | ⬜ |
| Sistema de diseño congelado | T2 | ⬜ |
| **Validación del stack: CU-31 de punta a punta** | T3 | ⬜ |
| El pasanaku funciona (`PasanakuCompletoE2ETest.java`) | T5 | ⬜ |
| Los 87 casos de uso del núcleo implementados | T5 | ⬜ |
| Autorizado a desplegar (backend) | T7 | ⬜ |
| App aprobada en ambas tiendas | T8 | ⬜ |
| Sitio público en línea, indexado y citable | T8 | ⬜ |
| **Primera medición GEO en los cuatro motores** | T8 | ⬜ |
| Los 99 casos de uso implementados | T9 | ⬜ |

## Sincronización entre olas

Se registra acá el cierre de cada ola: quién fusionó, si `main` quedó verde y qué
apareció al integrar (§7 de [[07 Carriles de trabajo concurrente]]).

| Ola | `main` verde | Integración | Notas |
| :-: | :-: | --- | --- |

## Micro-PR al troncal

| Rama | Carril | Qué agrega | Estado |
| --- | :-: | --- | :-: |

## Riesgos abiertos

Los diez del §11 del [[00 Plan maestro]] siguen abiertos: ninguna ola se ejecutó
todavía. Se actualiza al cerrar cada ola, con el riesgo que se cerró y el que apareció.

## Decisiones tomadas durante la ejecución

Vacío. Toda decisión cara de revertir se registra como ADR en `docs/Arquitectura/` y
se enlaza acá con una línea.

## Desviaciones respecto del plan

Vacío. Qué se hizo distinto, por qué, y si el plan se corrige o la desviación es
puntual.

## Ver también

[[19 Contrato de carril · conflicto cero, skills y calidad verificada]] · [[18 Fichas de carril · las 38 unidades de trabajo]] · [[00 Plan maestro]] · [[17 Plan de acción secuencial · coordinación de cinco máquinas]] · [[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[07 Carriles de trabajo concurrente]] · [[01 Fase 0 · Cimientos del repositorio]] · [[02 Fases 1 y 2 · Capa de datos y núcleo transversal]] · [[03 Fases 3 a 7 · Identidad, habilitación y núcleo de dinero]] · [[04 Fases 8 a 11 · Circuito del pasanaku]] · [[05 Fases 12 a 16 · Plataforma, reputación y cumplimiento]] · [[06 Fase 17 · Endurecimiento, E2E y despliegue]] · [[10 Plan maestro del frontend]] · [[16 Carriles de frontend]] · [[14 Fases F9 a F11 · Sitio público, SEO y GEO]]
