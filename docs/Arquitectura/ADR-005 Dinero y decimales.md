---
tags:
  - arquitectura
  - adr
titulo: "ADR-005 — Dinero y decimales"
estado: superada por ADR-019
fecha: 2026-08-12
---

# ADR-005 — Dinero y decimales

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-019 Dinero con BigDecimal]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

El modelo guarda importes en `DECIMAL(14,2)` —`16,2` para acumulados— siempre
acompañados de `moneda CHAR(3)` ISO-4217. El libro de la billetera es append-only
con partida doble interna ([[transaccion_billetera]]) y las restricciones exigen que
la suma de movimientos de una transacción sea exactamente cero.

**JavaScript no tiene decimal nativo.** `0.1 + 0.2 !== 0.3`, y el driver `pg`, si no
se le dice lo contrario, entrega `numeric` como `number`. Un `float` que atraviesa
una capa convierte un sistema contable exacto en uno aproximadamente exacto, que es
lo mismo que decir incorrecto.

Este es el costo conocido de haber elegido TypeScript ([[ADR-001 Lenguaje y runtime]]).
Este ADR es cómo se paga.

## Decisión

**Un importe nunca es `number`. Nace como *string* desde la base, vive como
`Decimal` en el dominio y viaja como *string* por la API.**

Tres reglas, obligatorias desde el primer commit:

1. **Parser explícito del driver**: `numeric` (OID 1700) y `int8` se leen como
   *string*. Se configura una sola vez, al crear el pool.
2. **`decimal.js` en el dominio**, encapsulado en un objeto de valor `Dinero` que
   lleva monto **y moneda** juntos. Operar dos `Dinero` de distinta moneda lanza.
3. **Lint que prohíbe `number`** en cualquier tipo, campo o parámetro cuyo nombre
   denote dinero (`monto`, `importe`, `saldo`, `comision`, `total`, `deuda`…), y
   prohíbe `parseFloat`, `Number()` y aritmética con `+`/`-` sobre esos valores.

El redondeo es **explícito y una sola vez**, al cierre del cálculo, con la regla que
fije el tarifario; nunca implícito en una división intermedia.

## Motivo

**Porque la exactitud aquí no es calidad, es cumplimiento.** Un centavo perdido en
un redondeo hace que el encaje de custodia (CU-50) no cuadre contra el banco, y ese
descuadre es exactamente lo que un supervisor busca.

**Porque el error de punto flotante no falla ruidosamente.** No hay excepción ni
alerta: hay un asiento que cuadra por 0,00000001 de diferencia y una restricción que
lo rechaza tres semanas después, en producción, sin forma de reconstruir el origen.
La única defensa barata es que el tipo incorrecto **no compile**.

**Porque `Dinero` sin moneda es un bug esperando.** El modelo siempre las guarda
juntas; el código debe hacer lo mismo, o alguien va a sumar bolivianos con dólares
en una consolidación.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Enteros de centavos (`bigint`)** | Correcto y rápido, pero obliga a convertir en cada frontera con una base que ya guarda `DECIMAL`, y las tasas de comisión con más de dos decimales vuelven a necesitar decimal. |
| **`number` con redondeo cuidadoso** | Depende de que nadie se olvide nunca. No es una estrategia. |
| **Hacer toda la aritmética en SQL** | Ideal en exactitud, pero deja la regla de negocio dispersa entre consultas y dificulta probarla. Se usa para agregados y cuadres, no como única defensa. |

## Consecuencias

**A favor**

- La exactitud queda al nivel de `BigDecimal`, que era el argumento fuerte de Java.
- El objeto `Dinero` centraliza formateo, redondeo y comparación: la app y el
  backoffice muestran el mismo número que la base guarda.

**En contra**

- Verbosidad: `a.mas(b)` en vez de `a + b`. Se acepta.
- Costo de rendimiento de `decimal.js`, irrelevante frente a la latencia de la base.
- Hay que revisar cada dependencia que toque importes (serializadores, exportadores
  a Excel, generadores de PDF): un `JSON.parse` descuidado reintroduce el `float`.

## Reglas de uso

| Regla | Cómo se ve |
| --- | --- |
| Un importe siempre lleva moneda | `Dinero.de('150.00', 'BOB')` |
| Nada de aritmética suelta | `total = comision.mas(impuesto)` |
| Redondeo explícito y único | `.redondear(2, ReglaTarifa)` al final del cálculo |
| La API serializa como *string* | `{"monto": "150.00", "moneda": "BOB"}` |
| La vista formatea, no calcula | El cliente nunca recalcula una comisión |
| Los cuadres se verifican en SQL | La prueba comprueba suma cero contra la base real |

## Cómo se verifica

- [ ] Prueba: `SELECT sum(monto)` de los movimientos de una transacción = `0.00`.
- [ ] Prueba de propiedad: mil operaciones aleatorias mantienen el cuadre exacto.
- [ ] Regla de lint activa en CI, sin excepciones marcadas con `eslint-disable`.
- [ ] Inspección: `grep` de `parseFloat|Number(` en el dominio devuelve vacío.

## Ver también

[[ADR-002 Acceso a datos]] · [[ADR-006 Contratos y validación]] · [[asiento_contable]] · [[Restricciones]]
