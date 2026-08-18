---
tags:
  - arquitectura
  - adr
titulo: "ADR-030 — Revocación de sesión y validación de respaldo"
estado: aceptada
fecha: 2026-08-18
---

# ADR-030 — Revocación de sesión y validación de respaldo

## Contexto

[[ADR-024 Autenticación y sesión distribuida]] hace que los trece servicios validen
el token localmente y reciban la lista de revocación **por Kafka**; su verificación
exige que un `jti` revocado deje de aceptarse "en menos de un segundo". Pero la
única vía de publicación permitida por [[ADR-018 Outbox transaccional y mensajería]]
es el outbox, cuya latencia es "del orden del intervalo del relevo" y que
explícitamente **no se acelera** (contradicción C-6, planes/20 §1.4). Además el
arranque en frío de un servicio "pide validación a `identidad`" contra un endpoint
que ninguna spec definía — reintroduciendo sin decirlo el punto único de fallo que
ADR-024 existe para evitar.

## Decisión

**La revocación es señal de seguridad, no hecho de negocio: sale directo a Kafka
por un tema compactado, con un endpoint de validación como respaldo declarado y un
criterio de latencia medible.**

1. **Excepción única y nombrada al outbox.** `identidad` publica la revocación
   (logout, robo, bloqueo por autoridad, rotación forzada) **directo** al tema
   compactado `aportaya.identidad.revocacion`, clave = `jti`, en el mismo commit
   de aplicación que la marca en su base. Es la única publicación del sistema que
   no pasa por outbox, y este ADR es su registro. El hecho de negocio asociado
   (p. ej. «cuenta bloqueada por autoridad») sí sale, además, por el outbox normal.
2. **Consumo.** Cada servicio mantiene la lista en memoria desde el tema (KTable /
   caché local), TTL igual al del token de acceso. Tema compactado: el arranque
   rehidrata leyendo desde el inicio, y la compactación lo mantiene chico.
3. **Respaldo de arranque en frío.** El contrato de `identidad` incorpora
   `GET /sesion/validez/{jti}` (interno, autenticado con token de cliente de
   [[ADR-028 Mecánica de saga|ADR-028]]). Un servicio que aún no está al día con el
   tema **falla del lado seguro**: valida contra ese endpoint hasta alcanzar el
   offset actual. Si `identidad` está caída *y* el servicio está al día con el
   tema, los tokens vigentes se siguen aceptando — la disponibilidad de ADR-024 se
   conserva; la ventana insegura queda acotada al arranque sin rehidratar.
4. **El criterio pasa a ser medible: propagación ≤ 5 segundos** del commit de la
   revocación a su rechazo en cualquier servicio, medido por la prueba de la fase
   17 y por la métrica `revocacion_propagacion_segundos` (alerta si p99 > 5 s).
   Con token de acceso de 15 minutos y refresco rotado, 5 segundos es honesto;
   "menos de un segundo" era inalcanzable por diseño y por eso no protegía nada.
5. **Bloqueo por autoridad es sincrónico además de publicado.** Cuando la orden es
   de autoridad competente (CU de bloqueo), `identidad` revoca el refresco, publica
   el `jti`, **y** el gateway consulta su propia caché de revocación en el borde:
   la ventana efectiva para tráfico nuevo es la del gateway, no la de los trece.

## Motivo

Una lista de revocación que viaja por el outbox hereda una latencia diseñada para
hechos de negocio, y un criterio imposible de cumplir se convierte en una casilla
que se marca sin medir. Separar la señal de seguridad —directa, compactada, con
respaldo sincrónico— del hecho de negocio —outbox, auditable— da a cada uno la
garantía que le corresponde, y deja escrito el único lugar del sistema donde el
outbox no manda, antes de que alguien lo descubra copiando el atajo para otra cosa.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Revocación por outbox** (statu quo) | Latencia del relevo + Kafka; el criterio de seguridad queda esclavo del ritmo de la mensajería de negocio |
| **Introspección sincrónica en cada petición** | Reintroduce a `identidad` como punto único de fallo y de latencia en todas las peticiones; ya descartada en ADR-024, y sigue siendo la reversión si el supervisor exige ventana cero |
| **Tokens de 1–2 minutos** | Multiplica el tráfico de refresco por usuario y castiga la red móvil real de Bolivia; no elimina la ventana, la acorta pagando en disponibilidad |

## Consecuencias

- La spec de `identidad` suma `GET /sesion/validez/{jti}` y el evento compactado
  queda declarado en el contrato de eventos (ADR pendiente 033).
- El gateway consume el tema de revocación (es su única suscripción a Kafka).
- La clave privada RS256 que firma todo esto queda bajo la custodia definida en el
  ADR de secretos (035): KMS/HSM, rotación con dos claves activas y solapamiento
  publicado en JWKS — su pérdida o filtración es incidente crítico reportable.

## Cómo se verifica

- [ ] Revocar un `jti` ⇒ rechazado por los catorce en ≤ 5 s (prueba de fase 17,
      medida, no declarada).
- [ ] Con `identidad` caída, un servicio al día sigue aceptando tokens vigentes y
      rechazando revocados.
- [ ] Un servicio recién arrancado y sin rehidratar **no acepta** un token sin
      consultar el endpoint de validez (prueba de arranque en frío).
- [ ] El tema es compactado y su tamaño no crece sin límite.
- [ ] Ninguna otra publicación del sistema evita el outbox (barrido: la única
      escritura directa a Kafka vive en `identidad` y en ningún otro módulo).

## Ver también

[[ADR-024 Autenticación y sesión distribuida]] · [[ADR-018 Outbox transaccional y mensajería]] · [[ADR-021 Sesión, RLS y pooling]] · `planes/20 · Saneamiento del plan` · [[_Arquitectura]]
