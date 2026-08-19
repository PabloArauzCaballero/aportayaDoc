---
name: notificaciones-consentimiento
description: "Avisarle algo al usuario en AportaYa: eventos notificables, plantillas versionadas, canales, consentimiento y supresión, tope de mensajes, recordatorios y respuestas entrantes. Úsala al agregar cualquier aviso, al integrar un proveedor de mensajería, o cuando haya que decidir si un mensaje se puede suprimir."
---

# Notificaciones

Dos errores opuestos, ambos caros: **avisar de menos** (el usuario no se entera de
que le vence el aporte y cae en mora por nuestra culpa) y **avisar de más** (el
usuario silencia el canal y deja de recibir lo que sí importa). El modelo separa
ambos casos desde el catálogo.

## Obligatorio vs. informativo

```
evento_notificable
  categoria        TRANSACCIONAL | REGULATORIO | RECORDATORIO | COMERCIAL
  es_obligatorio   si es true, NO se puede suprimir
```

| Categoría | Ejemplos | ¿Se puede silenciar? |
| --- | --- | --- |
| `TRANSACCIONAL` | Aporte acreditado, entrega ejecutada, retiro confirmado | No |
| `REGULATORIO` | Cambio de condiciones, respuesta a reclamo, incidente que lo afecta | No |
| `RECORDATORIO` | Vence tu aporte en 3 días | Sí, con límites |
| `COMERCIAL` | Promoción, invitación | Sí, y por omisión **apagado** |

`lista_supresion` guarda la baja **por categoría**, no global: darse de baja de lo
comercial no puede silenciar un aviso regulatorio (`R-NOT-02`). Y lo comercial
requiere consentimiento expreso previo, no una casilla premarcada.

## Plantillas versionadas

```
plantilla_mensaje → version_plantilla
```

El mensaje enviado se guarda con **la versión que se usó**. Cambiar la plantilla no
reescribe lo ya enviado: si el usuario reclama por lo que decía el aviso, hay que
poder mostrar el texto exacto que recibió.

Reglas de contenido:

- Nada de datos sensibles en el cuerpo: ni saldo completo, ni número de cuenta, ni
  documento. El aviso invita a abrir la app; no reemplaza la app.
- Nunca un aviso que revele una investigación de inteligencia financiera
  (`cumplimiento-uif`).
- El monto y la fecha, cuando van, salen del hecho registrado, no de un cálculo
  aparte que puede diferir.

## Canales por defecto — correo y bandeja interna

[[ADR-035 Canales por defecto]]: los canales encendidos son **`IN_APP`** (la bandeja
de la app), **`PUSH`** (el aviso en el celular) y **`CORREO`** (la constancia).
WhatsApp, SMS y voz están **apagados** hasta que haya contrato con proveedor: no se
usan en un flujo nuevo.

| | `IN_APP` | `PUSH` y `CORREO` |
| --- | --- | --- |
| Qué es | Fila en `bandeja_entrada` | Fila en `envio_notificacion` |
| Cuándo se escribe | En la **misma transacción** que la notificación | Después, por el relevo del outbox |
| ¿Puede fallar? | No | Sí: cola, reintento y cola muerta |
| ¿Entra en `cadena_respaldo`? | **No** | Sí |
| ¿Se suprime? | No, nunca: es el expediente | Sí, salvo `es_obligatorio` |
| ¿Cuenta para el tope diario? | No | Sí |

La cadena sembrada: `CORREO>PUSH` para seguridad, regulatorio y prioridad `CRITICA`;
`PUSH>CORREO` para el resto. Los 37 eventos tienen plantilla vigente en los tres
canales por defecto.

## Despacho

```
notificacion → envio_notificacion → evento_entrega_mensaje
     qué                a qué canal, con clave_idempotencia    qué dijo el proveedor
cola_envio / cola_muerta          proveedor_mensajeria / canal_vinculado
```

| Regla | Por qué |
| --- | --- |
| Se encola **dentro** de la transacción del hecho (*outbox*) | Nunca se avisa de algo que después se revirtió |
| `clave_idempotencia` por envío | El reintento no manda el mensaje dos veces |
| Un canal no verificado no recibe | Enviar a un número sin verificar es enviarle a un desconocido |
| Fallo permanente ⇒ cola muerta visible | Un usuario que dejó de recibir avisos es un incidente, no un dato de registro |
| Tope diario por usuario (`tope_diario_mensajes`) | Protege al usuario y al costo; lo obligatorio **no cuenta contra el tope** |

Si el canal preferido falla, se intenta el siguiente según preferencia; que ninguno
funcione es un estado que hay que poder ver, no un silencio.

## Recordatorios

`programacion_recordatorio` calcula los avisos a partir de la fecha de vencimiento
de la obligación: se programan una vez, no se recalculan en cada corrida. Reglas:

- Si la obligación se paga, **los recordatorios pendientes se cancelan**. Cobrarle a
  alguien que ya pagó es la queja más frecuente de este tipo de sistema.
- El recordatorio nocturno no existe: hay ventana horaria.
- Reprogramar el vencimiento reprograma los recordatorios en la misma transacción.

## Respuestas entrantes

`respuesta_entrante` y `bandeja_entrada`: el usuario contesta el mensaje. Casos que
hay que resolver:

| Entrada | Qué hace |
| --- | --- |
| Palabra de baja ("BAJA", "STOP") | Alta en `lista_supresion` de la categoría correspondiente, y confirmación |
| Consulta | Ticket de soporte, no un mensaje perdido |
| Algo que parece un reclamo | Se ingresa como reclamo: el canal es válido (`reclamos-consumidor`) |
| Ruido | Se registra y se descarta, sin responder |

Una baja recibida y no aplicada es un incumplimiento, no un detalle.

## Checklist

- [ ] El evento está en `evento_notificable` con categoría y obligatoriedad.
- [ ] Lo obligatorio no es suprimible, con prueba de ese rechazo.
- [ ] La plantilla está versionada y el envío guarda su versión.
- [ ] El encolado ocurre dentro de la transacción del hecho.
- [ ] `clave_idempotencia` por envío, probada contra doble despacho.
- [ ] El pago cancela los recordatorios pendientes.
- [ ] El tope diario no bloquea lo obligatorio.
- [ ] Ningún dato sensible en el cuerpo del mensaje.
- [ ] La cola muerta es visible y accionable.

## Ver también

`trabajos-outbox` · `observabilidad` · `reclamos-consumidor` ·
`cumplimiento-uif` · CU-80, CU-81, CU-82 · familia `R-NOT`
