---
name: dinero-decimal
description: "Manejar importes en AportaYa: tipo Dinero sobre BigDecimal, escala y redondeo, moneda inseparable del importe, serialización como cadena decimal y pruebas de cuadre. Úsala siempre que el código toque un monto, saldo, comisión, impuesto, deuda o total, en backend, app o backoffice."
---

# Dinero y decimales

En un sistema con partida doble, la exactitud del importe no es un detalle de estilo:
es la diferencia entre un libro exacto y uno aproximado
([[ADR-019 Dinero con BigDecimal]]).

> **Un importe nunca es `double` ni `float`.** Vive como `BigDecimal` dentro de
> `Dinero` en todo el backend, y viaja como **cadena decimal** por la API.

> **Java tiene decimal nativo, y eso resuelve la mitad del problema — no todo.**
> `BigDecimal` a secas no impide sumar bolivianos con dólares, no fija la escala, y
> deja `equals` a un descuido de distancia. Por eso el objeto de valor.

## Las seis reglas

### 1 · `Dinero`, con moneda inseparable

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

```java
var cuota   = Dinero.de("150.00", BOB);
var recargo = Dinero.de("7.50",  BOB);
var total   = cuota.mas(recargo);              // 157.50 BOB
cuota.mas(Dinero.de("10.00", USD));            // ⇒ error del dominio, no conversión silenciosa
```

### 2 · Nada de punta flotante, en ninguna capa

Ningún `double` ni `float` en firmas, campos ni columnas. No hay excepción de
rendimiento que lo justifique en este dominio. Lo verifica el análisis estático.

### 3 · `compareTo`, nunca `equals`

`new BigDecimal("1.10").equals(new BigDecimal("1.1"))` es **falso**: son iguales en
valor y distintos en escala. Comparar importes con `equals` es un defecto que pasa
todas las pruebas hasta que no las pasa. El análisis estático lo prohíbe.

### 4 · `RoundingMode` explícito, siempre

`BigDecimal.divide` sin modo de redondeo lanza `ArithmeticException`, y eso es una
funcionalidad, no un estorbo: te obliga a decidir. Un fallo ruidoso en desarrollo vale
más que un descuadre silencioso en producción.

### 5 · Escala 2 con `HALF_UP`, fijada al construir

Es el redondeo comercial que usa el modelo. Se aplica en el constructor de `Dinero`,
una vez, y no en cada operación.

### 6 · En JSON, cadena decimal — nunca número

```json
{ "monto": "150.00", "moneda": "BOB" }
```

**Esta es la única de las seis que no resuelve el lenguaje**, y por eso es la que más
se olvida: el cliente es JavaScript, y un `number` de JSON se convierte en un doble
del otro lado. Que el backend sea exacto no sirve de nada si el importe pierde
precisión al cruzar el cable.

El serializador es global, vive en `plataforma/comun-web` y hay un barrido que revisa
**todas** las respuestas.

## Redondeo

- **Una sola vez**, al cerrar el cálculo, nunca en un paso intermedio.
- La regla la fija el tarifario ([[concepto_tarifa]]), no el criterio de quien
  programa.
- Cuando un total se reparte entre varios (prorrateo de una deducción, división de una
  bolsa), **el residuo se asigna deliberadamente**: se define a quién le toca el
  centavo y se prueba que la suma de las partes es igual al total.

## Serialización

| Frontera | Forma |
| --- | --- |
| Base ⇄ backend | `numeric(14,2)` ⇄ `BigDecimal` — jOOQ lo mapea de forma nativa |
| Backend ⇄ cliente | `{"monto": "150.00", "moneda": "BOB"}` |
| Contrato OpenAPI | `type: string, pattern: '^-?\d+\.\d{2}$'` + `enum: [BOB, USD]` |
| **Entre servicios** | Igual que al cliente: cadena decimal en el cliente generado |
| Vista | El átomo `Monto` formatea; **nunca** calcula |

El cliente **no recalcula** una comisión ni un total para mostrarlo: pide el valor
cotizado (CU-30) o lo recibe con la respuesta. Si lo recalcula, tarde o temprano
muestra algo distinto de lo que la base guardó.

## En la base

- `DECIMAL(14,2)`, o `16,2` para acumulados, **siempre** con `moneda CHAR(3)`.
- Los agregados y cuadres se hacen en SQL, con `numeric`: es exacto y es más rápido que
  traer diez mil filas al proceso.
- El saldo **no se guarda**: se deriva de movimientos ([[transaccion_billetera]]). La
  caché de saldo se sincroniza dentro de la misma transacción, nunca por fuera.

## Errores que ya conocemos

| Error | Cómo se ve | Consecuencia |
| --- | --- | --- |
| Un `double` en un DTO intermedio | `double monto` en una clase de transporte | Pérdida de precisión invisible |
| Comparar con `equals` | `saldo.equals(cero)` | El saldo `0.00` no es igual a `0` y la rama nunca entra |
| Serializar como número JSON | `{"monto": 150.0}` | El cliente lo recibe como doble; descuadre en el exportador o en el PDF |
| Redondear en cada paso | `setScale` en tres funciones | Diferencias de centavos que no cierran contra el banco |
| Sumar sin mirar la moneda | `bob.add(usd)` sobre `BigDecimal` desnudo | Consolidado sin sentido |
| Porcentajes con dos decimales | `0.07` para 7,25 % | Comisión mal devengada |
| Formatear a mano en la vista | Formateo local en el componente | Muestra distinto de lo guardado |

## Pruebas obligatorias

- [ ] **Cuadre**: la suma de los movimientos de una transacción es exactamente `0.00`.
- [ ] **Asiento equilibrado**: débitos = créditos, verificado en SQL.
- [ ] **Propiedad**: mil operaciones aleatorias mantienen el cuadre exacto (jqwik).
- [ ] **Prorrateo**: la suma de las partes es igual al total, con residuo asignado.
- [ ] **Moneda**: operar monedas distintas lanza un error del dominio.
- [ ] **Frontera**: lo que devuelve la API es **cadena** con dos decimales.

## Ver también

`contabilidad-partida-doble` · `facturacion-sin` · `implementar-desde-boveda` ·
`datos-jooq` · `contratos-api` · `pruebas-cu` ·
`docs/Arquitectura/ADR-019 Dinero con BigDecimal.md`
