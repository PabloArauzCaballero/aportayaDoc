---
tags:
  - moc
  - indice
titulo: "AportaYa — modelo de datos"
entidades: 306
relaciones_fk: 633
modulos: 14
---

# AportaYa — Índice

> [!abstract] Qué es esta bóveda
> El modelo de datos completo del sistema, navegable como grafo. Cada tabla y
> cada clave foránea es una nota, enlazada con las demás, para poder recorrer
> el modelo por relaciones en vez de leer nueve diagramas sueltos.

## Cómo está organizada

```
docs/
├── Index.md                 ← estás acá
├── Modelos/
│   ├── Entidades/           ← una nota por tabla (306), en 14 carpetas
│   │   ├── 01 - Identidad, Usuarios y Seguridad/
│   │   ├── 02 - Grupos, Cupos, Turnos y Gobernanza/
│   │   └── ...
│   └── Relaciones/          ← una nota por clave foránea (633), en 14 carpetas
│       ├── 01 - Identidad, Usuarios y Seguridad/
│       └── ...
├── CasosDeUso/              ← un caso de uso por flujo (36), con criterios de aceptación
├── Cumplimiento.md          ← matriz normativa ASFI · UIF · BCB · SIN · ISO
├── Restricciones.md         ← catálogo de restricciones con DDL
└── entidades/               ← justificación de negocio + diagramas .puml
```

| Carpeta | Qué contiene | Índice |
| --- | --- | --- |
| **Entidades** | Una nota por tabla, agrupadas en una carpeta por módulo: columnas, claves, FK salientes y entrantes, entidades vecinas y las notas del diagrama. | [[_Entidades]] |
| **Relaciones** | Una nota por FK, agrupadas por el módulo de la tabla de origen: destino, cardinalidad, si es opcional y si cruza módulos. | [[_Relaciones]] |
| **entidades/** | Por qué existe cada entidad, a nivel de negocio y de sistema. Un documento por módulo. | [[entidades/README\|Fichas de negocio]] |
| **Cumplimiento** | Contraste requisito por requisito contra ASFI, UIF, BCB, SIN e ISO, con estado y brechas abiertas. | [[Cumplimiento]] |
| **Casos de uso** | Cómo se ejecuta cada flujo: pasos, tablas, validaciones, evidencia y criterios de aceptación. | [[_CasosDeUso]] |
| **Restricciones** | Las reglas que la base de datos hace cumplir, con su DDL y la norma que las obliga. | [[Restricciones]] |

## Los cinco registros que conviene entender primero

Casi todo el modelo se explica con cinco ideas. Si vas a leer solo cinco notas, que sean estas:

1. **[[obligacion_aporte]]** — el eje del dinero. La cubre el fondo, la deduce la entrega y la puntúa la reputación.
2. **[[registro_incumplimiento]]** — el incumplimiento como expediente, no como bandera.
3. **[[asiento_contable]]** — doble partida: nada se edita, todo se reversa.
4. **[[transaccion_billetera]]** — el saldo no se guarda: se deriva de un libro append-only con partida doble interna.
5. **[[concepto_tarifa]]** — la política de cobro completa en seis columnas: se cambia con un seeder, no con un despliegue.

## Módulos

| # | Módulo | Foco de negocio | Tablas | FK | Fichas |
| :-: | --- | --- | --: | --: | --- |
| 01 | Identidad, Usuarios y Seguridad | Saber con certeza a quién le estás confiando plata ajena | 25 | 32 | [[01_identidad_usuarios\|negocio]] |
| 02 | Grupos, Cupos, Turnos y Gobernanza | Reglas del juego, orden de cobro y decisiones colectivas | 22 | 49 | [[02_grupos_turnos\|negocio]] |
| 03 | Aportes, Pagos QR y Conciliación | Que "pagué" signifique "el banco lo confirmó" | 23 | 48 | [[03_aportes_pagos_qr\|negocio]] |
| 04 | Entregas de Fondo | Que la bolsa llegue completa, a la persona correcta, una sola vez | 10 | 24 | [[04_entregas_fondo\|negocio]] |
| 05 | Notificaciones y Comunicaciones | WhatsApp como canal real de cobro, sin spam ni doble aviso | 15 | 21 | [[05_notificaciones\|negocio]] |
| 06 | Transparencia y Reputación | Que nadie tenga que "creerle" al organizador | 16 | 22 | [[06_transparencia_reputacion\|negocio]] |
| 07 | Organizador y Automatización | Administrar es un rol, no un negocio: el organizador no cobra ni custodia | 12 | 17 | [[07_organizador_automatizacion\|negocio]] |
| 08 | Garantía, Incumplimiento, Cobranza y Sanciones | El grupo no se detiene, pero la deuda no se perdona sola | 33 | 99 | [[08_garantia_incumplimiento\|negocio]] |
| 09 | Auditoría, Reportes y Cumplimiento | Poder demostrar todo lo anterior ante un reclamo o un regulador | 18 | 26 | [[09_auditoria_reportes\|negocio]] |
| 10 | Billetera, Custodia y Dinero Electrónico | El saldo no se guarda: se deriva, y todos los días cuadra contra el banco | 26 | 65 | [[10_billetera_custodia\|negocio]] |
| 11 | Tarifas, Comisiones, Impuestos y Facturación | La política de cobro es dato, no código: se cambia con un seeder | 27 | 65 | [[11_tarifas_comisiones\|negocio]] |
| 12 | Cumplimiento Regulatorio y Consumidor Financiero | Que una inspección se responda con consultas, no armando carpetas | 47 | 102 | [[12_cumplimiento_asfi\|negocio]] |
| 13 | Contabilidad Financiera y ERP | Que cerrar un mes no dependa de un Excel armado a mano | 18 | 39 | [[13_contabilidad_erp\|negocio]] |
| 14 | Publicidad y Campañas | Que un partner se anuncie dentro de la app sin inventar un segundo cobro | 14 | 24 | [[14_publicidad_campanas\|negocio]] |

## Entidades más conectadas

El grado (FK entrantes + salientes) es un buen proxy de importancia estructural:

| Entidad | Módulo | FK salientes | FK entrantes | Grado |
| --- | :-: | --: | --: | --: |
| [[usuario]] | 01 | 0 | 211 | **211** |
| [[grupo]] | 02 | 1 | 45 | **46** |
| [[participante]] | 02 | 3 | 25 | **28** |
| [[registro_incumplimiento]] | 08 | 9 | 11 | **20** |
| [[transaccion_billetera]] | 10 | 5 | 14 | **19** |
| [[cuenta_billetera]] | 10 | 4 | 14 | **18** |
| [[asiento_contable]] | 03 | 4 | 12 | **16** |
| [[entrega_fondo]] | 04 | 8 | 8 | **16** |
| [[cuenta_contable]] | 03 | 3 | 12 | **15** |
| [[token_verificacion]] | 01 | 4 | 10 | **14** |
| [[obligacion_aporte]] | 03 | 7 | 7 | **14** |
| [[pago]] | 03 | 4 | 9 | **13** |

## Acoplamiento entre módulos

De las 633 claves foráneas, **328 cruzan módulos**. La matriz muestra
cuántas FK van de un módulo (fila) a otro (columna):

| desde \ hacia | 01 | 02 | 03 | 04 | 05 | 06 | 07 | 08 | 09 | 10 | 11 | 12 | 13 | 14 |
| :-: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| **01** | · | · | · | · | · | · | · | 1 | · | · | · | · | · | · |
| **02** | 11 | · | 2 | · | · | · | 1 | 1 | · | · | · | · | · | · |
| **03** | 12 | 9 | · | · | · | · | · | · | · | · | · | · | 1 | · |
| **04** | 8 | 5 | 1 | · | · | · | · | · | · | · | · | · | · | · |
| **05** | 4 | 1 | 1 | · | · | · | · | · | · | · | · | · | · | · |
| **06** | 7 | 7 | · | · | · | · | · | · | · | · | · | · | · | · |
| **07** | 7 | 1 | · | · | · | · | · | · | · | · | · | · | · | · |
| **08** | 27 | 29 | 8 | 2 | 1 | · | · | · | · | · | · | · | · | · |
| **09** | 16 | 3 | · | · | · | · | · | · | · | · | · | · | · | · |
| **10** | 20 | 3 | 8 | · | · | · | · | · | 1 | · | · | · | · | · |
| **11** | 15 | 6 | 6 | 1 | · | · | · | 1 | · | 3 | · | 1 | · | · |
| **12** | 60 | · | · | · | · | · | · | · | 4 | 5 | 1 | · | · | · |
| **13** | 8 | · | 10 | · | · | · | · | · | · | · | · | · | · | · |
| **14** | 6 | · | · | · | · | · | 1 | · | · | · | 1 | · | 1 | · |

> [!tip] Cómo leerla
> La columna **01** llena de números confirma que identidad es el cimiento: casi
> todo cuelga de `usuario`. La fila **08** muestra lo contrario: el incumplimiento
> consume de todos lados porque necesita el contexto completo para armar el expediente.

## Convenciones de la bóveda

- **Tags**: `#entidad`, `#relacion`, `#fk`, `#cross-modulo`, `#append-only`, `#modulo/0X-...`
- **Propiedades**: cada nota lleva frontmatter con módulo, tabla, clase, claves y conteos, para filtrar con búsqueda o Dataview.
- **`↗`** en una tabla marca que la referencia cruza a otro módulo.
- Las notas **append-only** no admiten `UPDATE` ni `DELETE`: se corrigen registrando el movimiento inverso.
- Los nombres de nota son los **nombres de tabla** (`snake_case`), así que `[[pago]]` autocompleta desde cualquier nota.

## Búsquedas útiles

```
tag:#append-only              → tablas que no se editan nunca
tag:#cross-modulo             → FK que acoplan módulos
tag:#entidad "clave_idempotencia"  → dónde se protege contra duplicados
```

> [!note] Cómo se generó
> Las notas de `Modelos/` se derivan de los `.puml` de `docs/entidades/`: si cambia
> un diagrama, hay que regenerarlas para que no se desincronicen. Las fichas de
> negocio de `docs/entidades/*.md` sí están escritas a mano.

