---
tags:
  - arquitectura
  - adr
titulo: "ADR-031 — Lecturas, réplica y rol auditor"
estado: aceptada
fecha: 2026-08-18
supera: ADR-011
---

# ADR-031 — Lecturas, réplica y rol auditor

> Supera a [[ADR-011 Lecturas y réplica]], escrita para el monolito (hablaba del
> worker, contaba 274 tablas y repartía la réplica sin decidir permisos). Lo que
> sigue vigente de ella se re-declara acá; lo demás queda como expediente.

## Contexto

Tres documentos repartían la lectura cruzada de tres maneras
(contradicción C-3, planes/20 §1.5): [[ADR-017 Propiedad de datos por servicio]]
declara a `auditoria` como **única** excepción; [[ADR-021 Sesión, RLS y pooling]]
abría el `DataSource` con `rol_auditor` a "listados pesados" de cualquier
servicio; ADR-011 se lo daba también a `cumplimiento`. Y nadie había decidido si
`rol_auditor` salta RLS: con `BYPASSRLS`, la mitad de las lecturas del sistema no
está protegida por las políticas; sin él, un reporte regulatorio sale incompleto
— ambas cosas inaceptables sin decidirlas por escrito.

## Decisión

**Separación por credencial desde el primer día; la lectura cruzada es una
excepción enumerada, siempre contra la réplica, siempre con huella.**

1. **Se conserva de ADR-011:** credenciales de lectura y escritura distintas desde
   el día uno (`aportaya.datasource.lectura.url`); todo lo que necesita leer lo que
   acaba de escribir va a la primaria, sin excepción; la réplica es solo para lo
   que tolera retraso; las proyecciones se generan desde la bóveda.
2. **Excepciones de lectura cruzada: exactamente dos.** `auditoria` y
   `cumplimiento`, contra la **réplica**, con `rol_auditor`. Ningún otro servicio
   lo recibe: un "listado pesado" de otro servicio se resuelve con una proyección
   propia sobre su esquema o pidiendo el reporte a `auditoria` por contrato. La
   frase de ADR-021 que lo abría a "listados pesados" queda corregida por este ADR.
3. **`rol_auditor` tiene `BYPASSRLS`,** y se compensa con tres controles duros:
   - solo existe en los `DataSource` de réplica de esos dos servicios; la primaria
     lo rechaza por `pg_hba` (sin camino de escritura, sin camino caliente);
   - **toda consulta que lo use deja huella** en `comun.registro_acceso_datos` con
     identidad del solicitante y justificación no nula — el CHECK que la auditoría
     de robustez ya endureció;
   - las extracciones siguen el circuito de la skill `extraccion-de-datos`: hash
     del resultado, cifrado, vencimiento y tope de descargas.
4. **El rezago de la réplica es un número con dueño:** rezago máximo tolerado
   **30 segundos**; la métrica `replica_rezago_segundos` con alerta al superarlo, y
   los reportes regulatorios estampan el LSN/momento de corte con el que se
   generaron, para que dos corridas sean comparables.
5. **Caída de la réplica ≠ barra libre.** El fallback a la primaria para lecturas
   pesadas está **prohibido por omisión**; solo lo habilita una intervención
   manual registrada, con tope de duración, y dispara incidente operativo — la
   primaria que mueve dinero no se sacrifica por un tablero.
6. **Vistas que cruzan esquemas** viven en un esquema `reportes` de la réplica
   lógica de generación (creado por los generadores, S4), consultable solo por
   `rol_auditor`: la frontera de ADR-017 no se perfora con vistas.

## Motivo

La frontera de datos de ADR-017 vale lo que valga su excepción más floja. Dos
excepciones enumeradas, de solo lectura, contra la réplica, con huella
obligatoria y sin camino a la primaria, dejan la promesa verificable: cualquier
otra aparición de `rol_auditor` es un hallazgo, no una interpretación. Y un
`BYPASSRLS` decidido y compensado es más seguro que uno implícito: los reportes a
la UIF salen completos, y el acceso a datos personales queda con la justificación
que la norma exige.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **`rol_auditor` sin `BYPASSRLS`** | Reportes regulatorios incompletos en silencio: el peor fallo posible ante el supervisor |
| **Un rol de lectura cruzada por servicio que lo pida** | La excepción se vuelve la regla; en seis meses "todos leen todo pero por la réplica" |
| **ETL a un almacén analítico separado** | Correcto a futuro para tableros; hoy agrega una pieza que operar y respaldar sin caso de uso que la exija — se reevalúa cuando los indicadores lo pidan |

## Consecuencias

- ADR-021 y los documentos de fase corrigen la mención a "listados pesados".
- `pg_hba`/roles distinguen primaria y réplica para `rol_auditor` (S4).
- `auditoria` expone reportes por contrato para los servicios que crean necesitar
  lecturas ajenas.

## Cómo se verifica

- [ ] `rol_auditor` no puede conectarse a la primaria (probado en la suite de
      seguridad).
- [ ] Solo dos servicios tienen el `DataSource` de réplica con ese rol (barrido de
      configuración).
- [ ] Consulta con `rol_auditor` sin fila de justificación en
      `registro_acceso_datos` ⇒ falla (prueba negativa).
- [ ] `replica_rezago_segundos` existe, tiene umbral 30 s y alerta con destinatario.
- [ ] Un reporte regulatorio declara su momento de corte y dos corridas sobre el
      mismo corte producen el mismo hash (skill `extraccion-de-datos`).

## Ver también

[[ADR-011 Lecturas y réplica]] (superada) · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-021 Sesión, RLS y pooling]] · `planes/20 · Saneamiento del plan` · [[_Arquitectura]]
