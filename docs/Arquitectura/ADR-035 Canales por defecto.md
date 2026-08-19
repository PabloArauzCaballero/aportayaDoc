---
tags:
  - arquitectura
  - adr
titulo: "ADR-035 — Canales por defecto: correo y bandeja interna"
estado: aceptada
fecha: 2026-08-19
---

# ADR-035 — Canales por defecto

> Aplica [[ADR-033 Puertos y adaptadores]] al puerto de mensajería, y fija cuáles
> de sus adaptadores están encendidos mientras no haya contrato con ningún
> proveedor.

## Contexto

El módulo 05 modela la mensajería con todo lo que hace falta: eventos notificables,
plantillas versionadas, proveedores, cadena de respaldo, cola, cola muerta,
supresión y bandeja de entrada. Lo que el catálogo tenía sembrado, en cambio, era un
sistema **WhatsApp primero**: 30 de los 37 eventos lo listaban como canal, y diez de
las dieciocho plantillas eran de WhatsApp.

Eso tiene tres problemas concretos hoy:

1. **No se puede probar nada.** WhatsApp Business exige contrato con un proveedor,
   número verificado y plantillas aprobadas por Meta. Sin eso, el flujo de
   notificación no corre ni en la máquina de nadie ni en QA.
2. **Cuesta plata por mensaje**, y el proyecto todavía no tiene tarifario aprobado
   ni presupuesto de mensajería.
3. **La app ya tiene dónde mostrar el aviso**: `bandeja_entrada` existe en el modelo
   y no depende de nadie.

## Decisión

**Los canales por defecto son `CORREO` y `IN_APP`, con `PUSH` como aviso de que hay
algo en la bandeja. WhatsApp, SMS y voz quedan como adaptadores opcionales,
apagados.**

| Canal | Adaptador | Estado por defecto | Qué es |
| --- | --- | --- | --- |
| `IN_APP` | `BandejaAdaptador` | **activo, siempre** | Fila en `bandeja_entrada`: el expediente del usuario |
| `PUSH` | `PushAdaptador` | activo (simulado en dev) | El aviso en el celular de que hay algo nuevo |
| `CORREO` | `CorreoSmtpAdaptador` | activo (buzón local en dev) | La constancia con texto completo |
| `WHATSAPP` | `WhatsAppAdaptador` | apagado | Opcional, cuando haya contrato |
| `SMS` | `SmsAdaptador` | apagado | Opcional, respaldo de cobranza |
| `LLAMADA_VOZ` | `VozAdaptador` | apagado | Opcional, último recurso |

### La bandeja no es un envío, y por eso no está en la cadena de respaldo

Es la diferencia que ordena todo el módulo:

```
notificacion ──┬── bandeja_entrada    se escribe en LA MISMA transacción · no puede fallar
               └── envio_notificacion  uno por canal externo · proveedor, cola, reintento, DLQ
```

`IN_APP` se escribe junto con la notificación y por eso **no compite** con los otros
canales ni entra en `cadena_respaldo`: no hay a qué caer cuando algo no puede fallar.
`PUSH` y `CORREO` sí son envíos con proveedor, y por eso tienen intento, estado,
reintento con retroceso y cola muerta.

De ahí la cadena que quedó sembrada en los 37 eventos:

| Tipo de evento | `canales_permitidos` | `cadena_respaldo` |
| --- | --- | --- |
| Seguridad, regulatorio o prioridad `CRITICA` | `IN_APP,PUSH,CORREO` | `CORREO>PUSH` |
| Todo lo demás | `IN_APP,PUSH,CORREO` | `PUSH>CORREO` |

Lo crítico arranca por correo porque es el canal que **deja constancia con texto
completo** y sobrevive a que el usuario desinstale la app; lo cotidiano arranca por
push porque es inmediato y gratis.

### El consentimiento sigue aplicando — salvo a la bandeja

`IN_APP` **no se suprime y no se consiente**: es el registro de lo que la plataforma
le comunicó a esa persona, y sin él un descargo o una sanción no tendrían
notificación probada (`debido-proceso`). `CORREO` y `PUSH` sí respetan
`preferencia_notificacion`, `lista_supresion` y el tope diario, con la excepción de
siempre: lo obligatorio (`es_obligatorio = true`) no se suprime por preferencia.

### Qué cambió en las semillas

| Antes | Ahora |
| --- | --- |
| 37 eventos con WhatsApp en la cadena | 37 eventos con `IN_APP,PUSH,CORREO` |
| 18 plantillas (10 de WhatsApp) | 122 plantillas: las 3 por defecto en los 37 eventos, más las 11 externas que se conservan |
| 5 proveedores de mensajería, todos apagados | 6: se agrega `BANDEJA_INTERNA`, el único que nace **activo** |
| — | En dev, `01-entorno-tecnico.json` enciende correo (buzón local) y push simulado |

`BANDEJA_INTERNA` nace activo porque no depende de ningún tercero, no cuesta nada y
no puede caerse. Es la única excepción a la regla de sembrar los proveedores
apagados, y está escrita como excepción en `seeders/README.md`.

## Motivo

**Se puede desarrollar y demostrar el sistema entero sin firmar con nadie.** El
recorrido completo —aporte acreditado, aporte por vencer, entrega, sanción con
descargo— se ve en la bandeja de la app y en el buzón local. Es la condición para
empezar a programar ya.

**El correo es la constancia que el regulador espera.** Buena parte de los avisos de
este sistema no son marketing: son notificación de un acto que abre un plazo. El
correo tiene asunto, cuerpo completo, adjunto y fecha, y no depende de que el usuario
tenga la app instalada.

**La bandeja convierte "avisamos" en un hecho verificable.** Con la fila en
`bandeja_entrada` escrita en la misma transacción, la pregunta "¿le avisaron?" se
responde con un `SELECT`, no con el registro de un proveedor externo al que hay que
pedirle datos.

**WhatsApp sigue siendo el canal que el cliente boliviano lee**, y por eso no se
elimina: queda modelado, con plantillas, listo para encenderse el día que haya
contrato. Lo que se rechaza es que sea el camino **por defecto** de un sistema que
todavía no puede usarlo.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Dejar WhatsApp por defecto y simularlo en dev** | El simulador miente en lo que más importa —plantilla aprobada, ventana de 24 h, rechazo del proveedor—, y el equipo aprende un flujo que en producción no se comporta así. |
| **Solo bandeja interna, sin correo** | Un aviso que abre un plazo legal no puede depender de que la persona abra la app. Y con la app desinstalada no queda ningún rastro del lado del cliente. |
| **Solo correo, sin bandeja** | El correo se pierde, se marca como no deseado y rebota. La bandeja es la única entrega que no puede fallar, y es donde la app muestra el historial. |
| **SMS por defecto para lo crítico** | Cuesta por mensaje, no lleva texto largo, no deja constancia del contenido y depende de un operador. Queda como respaldo opcional de cobranza. |
| **Push como canal principal** | Depende de que la app esté instalada, con permiso concedido y con token vigente; tres cosas que fallan seguido. Es el aviso, no la notificación. |

## Consecuencias

**A favor**

- El módulo 05 se puede implementar y probar entero desde el primer día.
- El costo de mensajería en dev y QA es cero.
- "Le avisamos" es una fila de `bandeja_entrada`, no la palabra de un tercero.

**En contra, y hay que asumirlo**

- **104 plantillas nuevas se sembraron con texto derivado de un solo texto base por
  evento.** Están marcadas `APROBADA` para que el flujo corra, pero **el texto todavía
  no lo aprobó nadie**: el catálogo lo dice y `seeders/README.md` lo marca como
  "textos por aprobar". Antes de producción, cada uno pasa por legal.
- **El correo tiene su propia trampa**: entregabilidad, SPF/DKIM/DMARC y reputación
  del dominio. No se resuelve con el adaptador; se resuelve configurando el dominio, y
  hay que hacerlo antes de mandar el primer correo real.
- Cuando se encienda WhatsApp, la cadena de respaldo de los eventos de cobranza va a
  querer cambiar. Es un cambio de semilla, con su revisión: no de código.

## Cómo se verifica

- [ ] Toda notificación creada tiene su fila en `bandeja_entrada`, en la misma
      transacción. Prueba: revertir la transacción no deja bandeja huérfana.
- [ ] Ningún evento tiene `WHATSAPP` ni `SMS` en `canales_permitidos` mientras su
      proveedor esté `activo = false`.
- [ ] Todo evento activo tiene plantilla vigente en los tres canales por defecto, en
      `es-BO`. Consulta de verificación en `sql/50_verificacion/`.
- [ ] En dev, cero conexiones salientes al mandar una notificación: el correo cae en
      el buzón local.
- [ ] `es_obligatorio = true` ignora la supresión; `false` la respeta. Prueba de
      rechazo por cada uno.
- [ ] El tope diario cuenta `CORREO` y `PUSH`, y **no** cuenta `IN_APP`.

## Ver también

[[ADR-033 Puertos y adaptadores]] · [[ADR-027 Infraestructura de mensajería en el modelo]] ·
[[ADR-018 Outbox transaccional y mensajería]] · [[ADR-036 Android primero]] ·
`notificaciones-consentimiento` · `proveedores-externos` · `debido-proceso`
