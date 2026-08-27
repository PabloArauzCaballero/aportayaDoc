---
tags:
  - arquitectura
  - adr
  - alcance
  - producto
titulo: "ADR-039 — Sin efectivo: la plataforma no opera dinero físico"
estado: aceptada
fecha: 2026-08-20
---

# ADR-039 — Sin efectivo

> Retira del alcance del producto la operatoria de efectivo por corresponsales, que
> [[CU-10 Recargar saldo]] admitía como medio `AGENTE` y
> [[CU-57 Operar un punto de atención y arquear el efectivo]] desarrollaba entera.

## Contexto

El modelo nació con dos caminos para meter plata a la billetera: **QR** y **efectivo en un
punto de atención**. El segundo arrastraba consigo un módulo completo —`punto_atencion`,
`arqueo_punto_atencion`, límite diario por punto, contingencia sin conectividad, faltante de
caja como evento de riesgo— y un caso de uso propio.

Nada de eso estaba mal modelado. El problema es otro: **contradice la propuesta de valor**.
AportaYa existe para que el pasanaku deje de depender de que alguien junte y guarde billetes.
Ofrecer «andá a un corresponsal con efectivo» es reintroducir en la app exactamente la
complicación que el producto viene a sacar: una fila, un horario, un tercero que cuenta plata
y una persona caminando con dinero encima.

Y hay un costo que no se ve hasta que llega la inspección:

| Lo que arrastra el efectivo | Dónde aparece |
| --- | --- |
| Umbral **PCC-01** por concepto `EFECTIVO`, simple y acumulado | `umbral_reporte_uif`, incisos a y b |
| **Arqueo** diario por punto y fecha, con diferencia derivada | `arqueo_punto_atencion` |
| **Faltante de caja** como evento de riesgo operativo | [[CU-54 Registrar un evento de riesgo operativo]] |
| **Custodia física** y traslado de valores | fuera del modelo, y caro |
| **Alcance de licencia** más amplio ante ASFI | `licencia_regulatoria.alcance_autorizado` |

Pedir en la licencia una categoría que no se va a usar es pagar supervisión por un servicio
que no se presta.

## Decisión

**La plataforma no opera dinero en efectivo. El único ingreso de fondos es electrónico —QR,
transferencia o tarjeta—, y la única salida es a una cuenta bancaria del titular.**

En concreto:

1. **`CU-10` no admite `AGENTE`.** El enum de medio queda `QR | TARJETA | TRANSFERENCIA`.
2. **[[CU-57 Operar un punto de atención y arquear el efectivo]] queda obsoleto**, con su
   código reservado: la convención de [[_CasosDeUso]] es que un caso retirado **conserva su
   número** y nunca se reutiliza.
3. **Se retiran del modelo `PuntoAtencion` y `ArqueoPuntoAtencion`**, con sus cuatro claves
   foráneas y la columna `orden_recarga.punto_atencion_id`.
4. **`instrumento_fondeo.tipo`** pierde `AGENTE` y `EFECTIVO`; **`transaccion_billetera.canal`**
   pierde `AGENTE`.
5. **El alcance de licencia sembrado deja de pedir `AGENTE`.**
6. **Los umbrales UIF de concepto `EFECTIVO` se conservan.** Son la norma, no una función del
   producto: existen aunque nosotros no operemos efectivo, y borrarlos sería fingir que la
   norma cambió. Simplemente nunca se disparan.

## Motivo

- **La coherencia del producto es un activo.** Un producto que promete simplicidad y ofrece un
  camino complicado enseña a desconfiar de la promesa.
- **El modelo describe lo que se construye.** El gate de salida de la Fase 17 exige que **las
  tablas tengan código que las escriba**. Conservar dos tablas de una capacidad que no se va a
  ofrecer obliga a una de dos cosas: escribir código muerto, o dejar el gate en rojo para
  siempre. Las dos son peores que quitarlas.
- **Menos alcance regulatorio es menos riesgo y menos costo.** Cada categoría de la licencia
  trae obligaciones de supervisión propias.
- **El efectivo es la superficie de fraude más difícil de controlar** con medios técnicos: no
  deja rastro digital hasta que alguien lo digita.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| Dejar el efectivo modelado pero apagado por configuración | El gate de la Fase 17 exige código que escriba cada tabla; «apagado» se convierte en código muerto o en un gate en rojo permanente |
| Quitarlo solo de la app y conservar el caso de uso para backoffice | La operatoria de efectivo **es** el caso de uso; sin app que la origine, `CU-57` no tiene disparador |
| Conservarlo para una fase posterior | Volver a introducirlo es una migración con datos vacíos: barata. Mantenerlo mientras tanto no lo es |
| Aceptar efectivo solo para el aporte del grupo, no para recarga | El mismo módulo, el mismo arqueo, la misma licencia. No ahorra nada |

## Consecuencias

- **306 tablas pasan a 304.** Las cifras citadas en los planes y en el README se actualizan; el
  gate de cifras de `verificar_boveda.py` lo comprueba.
- **La skill `efectivo-puntos-atencion` se retira.** Una skill que enseña a construir algo que no
  existe es una trampa para quien la cargue; se quita del índice y de la matriz de carriles.
- **La cobranza pierde un canal de último recurso.** Un moroso sin medio electrónico no tiene
  cómo regularizar en persona. Se acepta: la estrategia de cobranza ya llega hasta la etapa
  prejudicial por canales digitales.
- **Se pierde el cliente sin bancarizar.** Es el costo real de esta decisión y hay que decirlo:
  quien no tiene cuenta ni billetera de otro proveedor no puede entrar. Se compensa con la
  interoperabilidad del QR, que el reglamento del BCB (RD 079/2022) exige para todo el sistema
  financiero nacional.
- **Volver atrás cuesta un ADR y una migración**, no una decisión de implementación.

## Cómo se verifica

```bash
python3 scripts/generar_boveda.py       # 305 entidades, sin PuntoAtencion ni ArqueoPuntoAtencion
python3 scripts/generar_ddl.py          # el esquema no vuelve a crear las tablas
python3 scripts/generar_semillas.py     # ninguna semilla escribe un punto de atención
python3 scripts/verificar_boveda.py     # cifras al día y ningún CU citando una tabla inexistente
python3 scripts/verificar_carriles.py   # la skill retirada no queda huérfana ni nombrada
psql -f sql/aplicar.sql                 # aplica en limpio
```

Y la comprobación que importa: **ninguna búsqueda de `punto_atencion` devuelve una tabla viva**,
y `CU-10` no acepta `AGENTE` en su contrato.

## Ver también

[[CU-10 Recargar saldo]] · [[CU-57 Operar un punto de atención y arquear el efectivo]] ·
[[ADR-035 Canales por defecto]] · [[Cumplimiento]] ·
[[Flujo de pantallas · app del participante]] · [[Flujo funcional · recorrido del usuario]] ·
`semillas-catalogos` · `boveda-modelo`
