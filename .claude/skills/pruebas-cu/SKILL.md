---
name: pruebas-cu
description: "Escribir las pruebas de un caso de uso de AportaYa con JUnit 5 y Testcontainers sobre PostgreSQL real: criterios de aceptación, pruebas de rechazo de restricciones, reintento, concurrencia, plazos y cuadre de dinero. Úsala al implementar cualquier caso de uso y antes de declarar algo terminado."
---

# Probar un caso de uso

La garantía de este sistema vive en la base. Una suite contra dobles o contra SQLite
prueba **otro sistema** ([[ADR-008 Pruebas]]): acá se prueba contra PostgreSQL 16
real, con el esquema aplicado desde `sql/aplicar.sql` y los catálogos sembrados.

## Los tres niveles

| Nivel | Qué prueba | Velocidad | Archivo |
| --- | --- | --- | --- |
| **Átomo** | Cálculo puro, objeto de valor | ms | `<Atomo>Test.java` |
| **Molécula** | Repositorio, adaptador — incluye el **rechazo** de la base | Contenedor | `<Molecula>Test.java` |
| **Organismo** | El caso de uso completo, en su transacción | Contenedor | `CU<NN>Test.java` |

## Entorno

```ts
// una vez por corrida: contenedor, esquema, semillas
const pg = await new PostgreSqlContainer('postgres:16').start()
await aplicar('sql/aplicar.sql')
await aplicar('sql/60_semillas/sembrar.sql')   // sin catálogo, denegar por omisión bloquea todo
```

Sin las semillas, **todo falla**, y es correcto: `R-LIM-01` y `R-LIC-01` rechazan
cualquier operación sin límite, tarifario o licencia vigente. Cada prueba corre en su
propia transacción revertida al final, o en su propio esquema si necesita
concurrencia real.

## Las seis pruebas obligatorias de un caso de uso

### 1 · Un test por criterio de aceptación

El nombre **cita** el criterio, tal como está escrito en el caso de uso:

```ts
it('CU-21 · CA-3: un aporte por debajo del monto de la obligación se rechaza', …)
```

### 2 · Rechazo de cada restricción citada

No basta el camino feliz. Se provoca la violación y se espera **el error de la base**,
no un `if` de la aplicación:

```ts
await expect(insertarDuplicado()).rejects.toThrow(/uq_pago_clave_idempotencia/)
```

Si la prueba pasa porque la aplicación validó antes, no probó la restricción: hay que
ejercerla saltándose la capa de aplicación.

### 3 · Reintento

Misma operación, misma clave de idempotencia ⇒ **misma respuesta y cero efectos
nuevos**. Se verifica contando filas antes y después.

### 4 · Concurrencia

Dos ejecuciones simultáneas sobre la misma obligación o la misma cuenta: una gana, la
otra recibe un error claro, y **el saldo queda correcto**. Sin `sleep`: se coordina
con promesas y bloqueos reales.

### 5 · Plazo, si el flujo tiene consecuencia legal

Se adelanta el reloj **inyectado** (nunca `Date.now()` real) y se verifica el
vencimiento, la alerta previa y el estado resultante.

### 6 · Cuadre, si mueve dinero

```sql
-- la suma de movimientos de la transacción es exactamente cero
SELECT sum(monto) FROM transaccion_billetera WHERE transaccion_id = $1;  -- 0.00
```

Más el asiento equilibrado y, donde haya aritmética, una prueba de propiedad sobre el
átomo de cálculo.

## Dobles de proveedores externos

La pasarela QR, WhatsApp, el SIAT y el KYC se prueban contra dobles que implementan
la **interfaz de dominio** y reproducen sus fallas reales:

| Falla | Qué debe pasar |
| --- | --- |
| Timeout | El trabajo se reintenta; no hay doble efecto |
| Respuesta duplicada | El segundo webhook no produce efecto nuevo |
| Respuesta fuera de orden | La confirmación tardía no revive algo revertido |
| Error permanente | El trabajo se marca fallido con evidencia, no en bucle infinito |

## Trabajos y cola

Nada de esperar con `sleep`. La cola se **drena de forma determinista**: se ejecuta el
worker una vez y se verifica el efecto. Además:

- [ ] Transacción revertida ⇒ el trabajo **no** está encolado.
- [ ] Mismo evento procesado dos veces ⇒ **un** efecto.
- [ ] Dos réplicas del worker ⇒ el trabajo con fecha corre **una** vez.

## Qué no hacer

| Antipatrón | Por qué |
| --- | --- |
| Base compartida entre corridas | Estado sucio; fallas que dependen de quién corrió antes |
| Mockear el repositorio en la prueba del caso de uso | Deja fuera exactamente lo que garantiza el dinero |
| `sleep(500)` para esperar algo | Prueba lenta y frágil |
| Probar el camino feliz solamente | Los incidentes de una billetera están en el infeliz |
| Perseguir un porcentaje de cobertura | Se revisa **qué del dinero no está probado**, no el número |

## Definición de terminado

- [ ] Todos los criterios de aceptación, como pruebas nombradas.
- [ ] Prueba de rechazo por cada restricción citada en el caso.
- [ ] Prueba de reintento y de concurrencia.
- [ ] Prueba de vencimiento si hay plazo legal.
- [ ] Prueba de cuadre si mueve dinero.
- [ ] Las consultas de verificación de [[Restricciones]] devuelven cero filas al final
      de la suite.

## Ver también

`idempotencia-reintentos` · `semillas-catalogos` · `contabilidad-partida-doble` · `implementar-desde-boveda` · `restriccion` · `dinero-decimal` · `trabajos-outbox` ·
`docs/Arquitectura/ADR-008 Pruebas.md`
