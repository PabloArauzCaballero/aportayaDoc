---
name: trabajos-outbox
description: "Programar efectos externos y trabajos con fecha en AportaYa: patrón outbox en PostgreSQL con relevo a Kafka y cron con ShedLock, idempotencia del consumidor, reintentos, trabajos cron con bloqueo entre réplicas y adaptadores de proveedores. Úsala al enviar una notificación, llamar a una pasarela, emitir una factura, remitir un reporte o programar un cierre."
---

# Trabajos, outbox y proveedores externos

Regla base: **el caso de uso no llama a nadie de afuera**. Registra el evento y encola
el trabajo dentro de su transacción; el worker lo procesa después
([[ADR-003 Trabajos, outbox y planificador]]).

```
CU  →  evento_dominio + encolar   (misma transacción)  →  COMMIT
                                                          ↓
worker  →  valida idempotencia  →  adaptador  →  registra intento
```

Si la transacción se revierte, el trabajo **no existe**. Si confirma, el trabajo
existe. No hay tercer resultado — y por eso la cola vive en la misma PostgreSQL.

## Encolar

```ts
await this.eventos.registrarYEncolar(tx, {
  tipo: 'aporte.confirmado',
  agregadoId: pago.id,
  clave: `aporte:${pago.id}`,        // clave de idempotencia del efecto
  cargaUtil: { pagoId: pago.id },    // identificadores, no datos derivados
})
```

- La carga útil lleva **identificadores**, no copias de datos: el worker vuelve a leer
  el estado actual.
- La clave se **deriva del evento**, nunca se genera en el worker.
- Un evento, un efecto. Si un caso de uso produce tres efectos, son tres trabajos.

## Consumir

```ts
export const enviarAvisoDeAporte = async ({ pagoId }, helpers) => {
  await conContexto(ctxSistema, async (tx) => {
    if (await yaEnviado(tx, pagoId)) return          // al menos una vez ⇒ idempotente
    const aviso = await construirAviso(tx, pagoId)
    const res   = await mensajeria.enviar(aviso)     // borde externo
    await registrarIntento(tx, pagoId, res)          // evidencia de cada intento
  })
}
```

| Regla | Por qué |
| --- | --- |
| **Entrega al menos una vez** | El consumidor debe ser idempotente; no hay excepciones |
| **Un trabajo, un efecto** | Nada que haga tres cosas y falle en la segunda |
| **Reanudable** | Si muere a la mitad, el reintento no duplica |
| **Cada intento se registra** | Es la evidencia con la que se responde un reclamo |
| **Reintento con retroceso exponencial y tope** | Después del tope, falla visible; nunca bucle infinito |
| **El worker corre con su propio rol** | Sin permisos sobre tablas append-only |

## Trabajos con fecha

Cierre diario (CU-51), conciliación de custodia (CU-50), remisión mensual a la UIF
(CU-43), vencimiento de reclamos (CU-52).

```ts
// crontab del worker
'0 1 * * *  cierre_diario   ?jobKey=cierre:diario&jobKeyMode=preserve_run_at'
```

- **Bloqueo por identificador**: con varias réplicas, corre **una sola vez**. El
  cierre diario ejecutado dos veces duplica asientos.
- El trabajo verifica su propia precondición: si el día ya está sellado, termina sin
  hacer nada.
- Los plazos legales **ya están persistidos** en la fila (`vence_en`,
  `plazo_respuesta`): el trabajo los consulta, no los recalcula.
- Todo trabajo con consecuencia legal alerta **antes** de vencer, no al vencer.

## Adaptadores de proveedores

Cada borde externo —pasarela QR, WhatsApp Business Cloud API, SIAT del SIN, KYC—
entra por una interfaz de dominio, con:

| Elemento | Obligatorio |
| --- | --- |
| Clave de idempotencia propia | Sí, enviada al proveedor cuando lo soporta |
| Timeout explícito | Sí; sin timeout, un proveedor lento bloquea la cola |
| Modo de prueba | Sí, para integración |
| Registro de petición y respuesta | Sí, sin datos sensibles en claro |
| Doble fiel para pruebas | Timeout, duplicado, respuesta fuera de orden, error permanente |

**Los webhooks entrantes llegan repetidos y fuera de orden.** El diseño lo asume: se
valida la firma, se guarda el evento crudo, se procesa por clave y una confirmación
tardía nunca revive algo ya revertido.

## Monitoreo

| Métrica | Alerta cuando |
| --- | --- |
| Profundidad de la cola | Crece sostenidamente |
| Edad del trabajo más viejo | Supera el plazo del flujo |
| Fallos por adaptador | Un proveedor concreto se degrada |
| Cierre diario | No se ejecutó a la hora esperada |
| Reporte con plazo legal | Falta menos de X para vencer |

Solo alerta lo que requiere que alguien actúe. El resto es panel.

## Antipatrones

- Llamar al proveedor dentro de la transacción del caso de uso.
- Cola fuera de PostgreSQL (SQS, Rabbit, Redis) para el outbox.
- `cron` del sistema operativo para trabajos con consecuencia contable.
- Un trabajo que asume que corre una sola vez.
- Reintentar sin tope, o descartar en silencio tras fallar.

## Ver también

`idempotencia-reintentos` · `notificaciones-consentimiento` · `reportes-regulatorios` · `observabilidad` · `back-spring` · `pruebas-cu` · `implementar-desde-boveda` ·
`docs/Arquitectura/ADR-003 Trabajos, outbox y planificador.md`
