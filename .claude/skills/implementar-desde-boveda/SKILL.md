---
name: implementar-desde-boveda
description: "Programar una funcionalidad de AportaYa usando la bóveda de Obsidian como especificación: en qué orden leer, qué garantizar en la base antes de escribir código de aplicación, cómo estructurar el servicio y qué pruebas exigir. Úsala al empezar a implementar cualquier flujo con dinero, cumplimiento o plazos legales, o cuando alguien pregunte por dónde empezar a codificar."
---

# Implementar a partir de la bóveda

La bóveda `docs/` **es la especificación**. No se programa contra la memoria de una
reunión: se programa contra el caso de uso, y lo que el caso de uso no diga se
resuelve **agregándolo al caso de uso primero**.

## Orden de lectura obligatorio

```
1. docs/CasosDeUso/CU-NN …        ← el flujo, paso a paso
2. docs/Restricciones.md           ← qué garantiza la base (códigos R-XXX-nn)
3. docs/Modelos/Entidades/<tabla>  ← columnas, claves, FK entrantes y salientes
4. docs/entidades/NN_modulo.md     ← por qué la entidad existe (evita rediseñarla mal)
5. docs/Cumplimiento.md            ← qué norma obliga el flujo, si aplica
6. docs/Stack.md + los ADR         ← con qué se construye y por qué
```

Si algo de esos seis falta o se contradice, **eso es el primer bug**: se corrige
la bóveda antes de escribir código.

El caso de uso ya trae, escritas, las cuatro decisiones que normalmente se
improvisan: el **contrato** (entrada, salida y códigos de error), la
**descomposición atómica** (qué pieza en qué nivel), los **eventos, trabajos y
permisos**, y la **interfaz**. No se rediseñan al programar: se implementan. Si al
implementar se descubre que estaban mal, se corrige el caso de uso **en el mismo
PR**.

## Orden de construcción

1. **Migración con restricciones primero.** Antes que cualquier servicio, aplicar
   las restricciones del caso (`scripts/sql/restricciones.sql`). Escribir la lógica
   antes que la barrera es la forma habitual de descubrir en producción que la
   barrera no existía.
2. **Semillas de catálogo.** Umbrales, límites, tarifario, impuestos, catálogo de
   reportes y licencia. Sin catálogo sembrado, la regla de *denegar por omisión*
   bloquea todo, y eso es correcto.
3. **Contrato** en `openapi/<servicio>.yaml`, copiado del caso de uso
   (skill `contratos-api`). Se escribe antes que la implementación.
4. **Átomos** puros del dominio, con sus pruebas en milisegundos.
5. **Moléculas**: repositorios jOOQ y adaptadores externos, cada uno contra un
   solo colaborador y sin abrir transacción.
6. **Organismo**: el caso de uso, única frontera transaccional.
7. **Página**: el controlador, que traduce y delega, sin lógica.
8. **Pruebas**: los criterios de aceptación del caso de uso, traducidos uno a uno.

## Reglas no negociables al codificar

| Regla | Cómo se ve en el código |
| --- | --- |
| **Una transacción por caso de uso** | Todo lo que el caso marca como "en la misma transacción" va en un único `BEGIN…COMMIT`. Nada de "primero guardo y después ajusto el saldo". |
| **Idempotencia en el borde** | La clave llega del cliente o del proveedor y se valida **antes** de cualquier escritura. Reintento = misma respuesta, cero efectos. |
| **El saldo no se escribe: se deriva** | Nunca `UPDATE cuenta SET saldo = saldo - x`. Se insertan movimientos con contrapartida y la caché de saldo se sincroniza dentro de la misma transacción. |
| **Nada se edita** | Corrección = movimiento inverso. Si aparece un `UPDATE` sobre una tabla *append-only*, la base lo rechaza; el código no debería ni intentarlo. |
| **Los plazos se calculan al crear** | `plazo_respuesta`, `fecha_limite`, `plazo_reporte`, `vence_en` se persisten. Prohibido calcularlos en la consulta. |
| **Denegar por omisión** | Falta límite, licencia, tarifario o política vigente → se rechaza. Nunca se asume permitido. |
| **Outbox, no llamadas dentro de la transacción** | Notificaciones, webhooks y reportes se disparan desde `evento_dominio`, no invocando el servicio externo dentro del `COMMIT`. |
| **Contexto de sesión para RLS** | Cada request setea `app.usuario_id` y `app.rol`; sin eso, las políticas de fila no protegen nada. |

## Estructura sugerida por caso de uso

```
openapi/tarifas.yaml               ← entrada, salida, códigos de error
servicios/<servicio>/
  aplicacion/CU31DevengarComision.java      ← ORGANISMO: orquesta la transacción
  dominio/DevengoComision.java              ← ÁTOMO: invariantes, sin IO
  infraestructura/DevengoRepositorio.java   ← MOLÉCULA: SQL, sin lógica
  infraestructura/PasarelaAdapter.java      ← MOLÉCULA de borde, idempotente
  ComisionesController.java                ← PÁGINA: traduce y delega
  pruebas/CU31Test.java                    ← criterios de aceptación del caso
```

Un archivo de aplicación por caso de uso, con el código `CU-NN` en el nombre: hace
que la trazabilidad especificación → contrato → código → prueba sea obvia sin
herramientas. El nivel de cada archivo es el que declara el caso de uso en su
tabla de descomposición.

## Definición de terminado

- [ ] Todos los criterios de aceptación del caso de uso pasan como pruebas.
- [ ] Existe al menos una prueba que **verifica el rechazo** de cada restricción
      citada en el caso (no basta el camino feliz).
- [ ] Prueba de reintento: la misma operación con la misma clave no duplica nada.
- [ ] Las consultas de verificación de `docs/Restricciones.md` devuelven cero filas
      después de correr la suite.
- [ ] Si el flujo tiene plazo legal, hay una prueba de vencimiento.
- [ ] Si el flujo mueve dinero, hay una prueba de que la suma de movimientos de la
      transacción es cero y que el asiento cuadra.
- [ ] La bóveda quedó al día: si algo cambió, se actualizó el caso de uso, la
      restricción o el modelo — no solo el código.

## Señales de que hay que volver a la bóveda

- El código necesita una columna que no existe → skill `boveda-modelo`.
- Aparece un `if` con un número regulatorio adentro → va a catálogo, skill `norma-nueva`.
- Hay una regla que "el backend valida" y protege dinero → skill `restriccion`.
- El flujo real difiere del caso de uso → se actualiza el caso, no se deja
  divergir en silencio.

## Ver también

`docs/CasosDeUso/_CasosDeUso.md` · `docs/Restricciones.md` · `docs/Cumplimiento.md` ·
`docs/Stack.md` · skills `arquitectura-atomica`, `contratos-api`, `datos-jooq`,
`dinero-decimal`, `trabajos-outbox`, `pruebas-cu`, `errores-api`,
`idempotencia-reintentos`.
