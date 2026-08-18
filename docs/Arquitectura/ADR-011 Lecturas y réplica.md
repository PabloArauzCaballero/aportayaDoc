---
tags:
  - arquitectura
  - adr
titulo: "ADR-011 — Lecturas, réplica y proyecciones"
estado: superada por ADR-031
fecha: 2026-08-13
---

# ADR-011 — Lecturas, réplica y proyecciones

> [!warning] Superada el 2026-08-18 por [[ADR-031 Lecturas, réplica y rol auditor]]
> Escrita para el monolito: habla del worker (eliminado por ADR-018), cuenta 274
> tablas (son 307) y reparte la réplica sin decidir los permisos de `rol_auditor`.
> La separación por credencial y la regla "lo que lee lo que acaba de escribir va a
> la primaria" siguen vigentes, re-declaradas en ADR-031. Se conserva como
> expediente.

## Contexto

El sistema tiene dos perfiles de consulta incompatibles entre sí. Por un lado,
operaciones cortas que mueven dinero y necesitan leer lo que acaban de escribir. Por
otro, consultas pesadas —extractos, conciliación de custodia, tableros, reportes a la
UIF— que barren meses de movimientos y no toleran degradar un cobro.

Con 274 tablas y un libro append-only que solo crece, este conflicto no es futuro:
aparece el primer mes de operación real.

## Decisión

**Lectura y escritura separadas por credencial desde el primer día, y la réplica solo
para lo que tolera retraso.**

- Credenciales distintas aunque al principio apunten al mismo servidor: mover la
  lectura a una réplica después no debe tocar código.
- **Todo lo que necesita leer lo que acaba de escribir va a la primaria**, sin
  excepción: saldo tras un aporte, estado tras una entrega.
- Cumplimiento, tableros y exportes van a la réplica, con transacción de solo lectura
  y su `SET LOCAL` de contexto como cualquier otra.
- Las proyecciones (vistas y materializadas) se **generan desde la bóveda**, jamás se
  crean al arrancar la aplicación ni con SQL armado desde entradas del usuario.
- Una vista materializada se refresca desde un trabajo del worker con bloqueo por
  identificador, y **expone la edad de sus datos como métrica**.

## Motivo

**Porque el aislamiento por credencial es gratis hoy y caro después.** Cambiar
cadenas de conexión al principio cuesta una tarde; separarlas cuando ya hay cien
consultas mezcladas cuesta un trimestre.

**Porque el retraso de réplica produce errores que parecen fraude.** Un saldo leído
de una réplica atrasada tras un aporte hace que el usuario vea que su plata "no
llegó". Peor: una conciliación sobre datos viejos reporta un descuadre que no existe,
y ese descuadre dispara procedimientos de cumplimiento.

**Porque una vista no acelera por sí sola.** Es un error frecuente y caro: se crea la
vista, no mejora nada, y ahora hay una capa más que mantener. Por eso el orden de
evaluación —índice, consulta, vista, materializada, tabla de proyección, caché— es
parte de la decisión y no una recomendación.

**Porque un reporte regulatorio no puede salir de datos vencidos.** Si la fuente es
materializada, la edad de los datos es un control, no un detalle de rendimiento.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Una sola conexión para todo** | El primer reporte mensual degrada los cobros del día. |
| **Enrutar por tipo de sentencia automáticamente** | Adivina mal justo en el caso peligroso: lectura inmediata después de escribir. |
| **Caché de aplicación como primera respuesta** | Sin estrategia de invalidación, muestra dinero desactualizado. Último recurso. |
| **Redirigir todo a la primaria si la réplica falla, sin límite** | Convierte una degradación en una caída completa. Permitido solo con tope y alerta. |
| **Proyecciones creadas por la aplicación al arrancar** | Rompe el dueño único del esquema ([[ADR-016 Acceso a datos con jOOQ]]). |

## Consecuencias

**A favor**

- Las consultas de cumplimiento dejan de competir con el dinero.
- Escalar lectura es agregar réplica, no reescribir consultas.

**En contra**

- Hay que decidir, consulta por consulta, si tolera retraso. Es trabajo real y no se
  puede automatizar sin riesgo.
- El retraso de réplica pasa a ser una métrica con alerta y un modo de degradación
  que hay que probar.
- Las vistas materializadas agregan un trabajo de refresco más que vigilar.

## Cómo se verifica

- [ ] Prueba: leer el saldo inmediatamente después de un aporte devuelve el valor
      nuevo, incluso con la réplica retrasada artificialmente.
- [ ] El rol de solo lectura falla al intentar escribir.
- [ ] Ninguna vista se crea fuera de `sql/`.
- [ ] Toda materializada tiene métrica de edad y alerta de refresco fallido.
- [ ] Ningún endpoint acepta columna, tabla o `join` desde el cliente.

## Ver también

[[ADR-016 Acceso a datos con jOOQ]] · [[ADR-021 Sesión, RLS y pooling]] · `lecturas-proyecciones` ·
`extraccion-de-datos` · `indicadores-tablero` · `resiliencia-rendimiento`
