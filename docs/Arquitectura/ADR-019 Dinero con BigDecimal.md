---
tags:
  - arquitectura
  - adr
titulo: "ADR-019 — Dinero: BigDecimal de punta a punta"
estado: aceptada
fecha: 2026-08-16
---

# ADR-019 — Dinero con BigDecimal

> Supera a [[ADR-005 Dinero y decimales]], que sostenía la exactitud con
> `decimal.js` y tres reglas de disciplina porque JavaScript no tiene decimal nativo.

## Contexto

El modelo guarda importes en `numeric(14,2)` y exige que la suma de débitos iguale a
la de créditos **al centavo**. La decisión anterior tenía que construir esa exactitud
sobre un lenguaje sin decimal: un parser explícito para que el driver devolviera
`numeric` como texto, un tipo `Dinero` sobre `decimal.js`, y una regla de lint que
prohibía `number` en toda variable monetaria. Funcionaba, y cada una de las tres era
un punto donde un descuido costaba un descuadre.

[[ADR-015 Lenguaje, runtime y framework]] cambia el piso: Java tiene `BigDecimal`.

## Decisión

**`BigDecimal` en todas las capas, envuelto en un objeto de valor `Dinero` que lleva
importe y moneda.**

```java
public record Dinero(BigDecimal importe, Moneda moneda) {
    public Dinero {
        Objects.requireNonNull(importe);
        if (importe.scale() > 2) throw new EscalaInvalida(importe);
        importe = importe.setScale(2, RoundingMode.HALF_UP);
    }
    public Dinero mas(Dinero otro) { exigirMismaMoneda(otro); … }
}
```

Las reglas, todas verificables:

| # | Regla | Se verifica con |
| :-: | --- | --- |
| 1 | **Ningún `double` ni `float` en el dominio, en el contrato ni en la base.** No hay excepción de rendimiento que lo justifique | análisis estático sobre todo `servicios/**` |
| 2 | **`RoundingMode` siempre explícito.** `BigDecimal.divide` sin modo de redondeo lanza excepción, y eso es una funcionalidad, no un estorbo | compilador y prueba |
| 3 | **Escala fija a 2 al construir**, con `HALF_UP` — el redondeo comercial que usa el modelo | invariante del constructor |
| 4 | **`compareTo`, nunca `equals`**, para comparar importes: `1.10` y `1.1` son iguales en valor y distintos en `equals` | análisis estático: `equals` sobre `BigDecimal` es error |
| 5 | **La moneda viaja con el importe.** Sumar BOB con USD no compila mal: falla en tiempo de ejecución con un error del dominio | tipo `Dinero` |
| 6 | **En JSON se serializa como cadena decimal**, nunca como número: un `number` de JSON es un doble del otro lado | serializador propio + barrido de respuestas |

La regla 6 es la que sobrevive del ADR anterior sin cambios, y por el mismo motivo:
el cliente es JavaScript. Que el backend sea exacto no sirve de nada si el importe se
convierte en `double` al cruzar el cable.

jOOQ mapea `numeric(14,2)` a `BigDecimal` de forma nativa: no hace falta un
conversor propio del lado de la base ([[ADR-016 Acceso a datos con jOOQ]]).

## Motivo

**Lo que era disciplina pasa a ser tipo.** De las tres reglas que el ADR anterior
declaraba obligatorias, dos las resuelve el lenguaje: no hay parser que configurar y
no hay biblioteca que importar. Queda la de serialización, que es del protocolo y no
del lenguaje.

**El descuido cambia de consecuencia.** En JavaScript, olvidarse una regla produce un
número que parece correcto y redondea mal en el tercer decimal. En Java, `divide` sin
`RoundingMode` lanza `ArithmeticException` en la primera prueba. Un fallo ruidoso en
desarrollo vale más que un descuadre silencioso en producción.

**El objeto de valor sigue haciendo falta.** `BigDecimal` a secas no impide sumar
bolivianos con dólares ni fija la escala. `Dinero` es un átomo del dominio, y sigue
siendo la única forma en que un importe circula por el sistema.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **`BigDecimal` desnudo, sin envolver** | Deja sin resolver la moneda y la escala, que son la mitad de los errores reales de dinero. Y hace que `equals` esté siempre a un descuido de distancia. |
| **Enteros de centavos (`long`)** | Exacto y rápido, y pésimo para leer: cada frontera del sistema se vuelve una multiplicación o división por 100 que alguien va a olvidar. Con `numeric(14,2)` en la base, además, habría conversión en cada consulta. |
| **Money API (JSR-354, Moneta)** | Modela moneda y redondeo de forma completa, y agrega una dependencia y un modelo mental para resolver lo que un `record` de doce líneas ya resuelve en este dominio. Se reevalúa si aparece multimoneda de verdad. |
| **`double` con redondeo al mostrar** | Es el defecto que este ADR existe para impedir. |

## Consecuencias

**A favor**

- Exactitud por construcción, no por vigilancia.
- La aritmética monetaria del dominio se lee como aritmética, no como llamadas a una
  biblioteca.
- El invariante 4 del plan maestro deja de depender de una regla de lint escrita a
  medida.

**En contra, y hay que asumirlo**

- `BigDecimal` es más verboso que un operador: `a.mas(b)` en vez de `a + b`. Se
  acepta; en dinero, la verbosidad es una función.
- La serialización a cadena hay que declararla en los catorce servicios. Va en la
  biblioteca de plataforma y se verifica con un barrido, no con memoria.
- Un importe que llega del cliente como número JSON hay que rechazarlo en la
  validación de entrada, no convertirlo en silencio.

## Cómo se verifica

- [ ] Ningún `double` ni `float` en `servicios/**`, ni en firmas ni en campos.
- [ ] Ningún `equals` sobre `BigDecimal`.
- [ ] Todo campo monetario de toda respuesta se serializa como cadena decimal.
- [ ] Prueba de cuadre: en cada caso de uso con dinero, la suma de débitos iguala a
      la de créditos al centavo.
- [ ] Prueba de moneda: sumar dos `Dinero` de monedas distintas falla con un error
      del dominio, no con un resultado.
- [ ] Prueba de redondeo: un reparto que no divide exacto asigna la diferencia y no
      la pierde.

## Ver también

[[ADR-005 Dinero y decimales]] · [[ADR-015 Lenguaje, runtime y framework]] · [[ADR-016 Acceso a datos con jOOQ]] · [[ADR-020 Contratos OpenAPI primero]] · [[Restricciones]] · [[_Arquitectura]]
