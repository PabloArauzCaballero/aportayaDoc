---
tags:
  - arquitectura
  - adr
titulo: "ADR-021 — Sesión, RLS y pooling con catorce servicios"
estado: aceptada
fecha: 2026-08-16
---

# ADR-021 — Sesión, RLS y pooling

> Supera a [[ADR-007 Sesión, RLS y pooling]], que fijaba el contexto con `SET LOCAL`
> sobre PgBouncer en modo transacción, con un solo proceso de API.

## Contexto

Las políticas de fila de PostgreSQL leen `app.usuario_id` y `app.rol` de la sesión.
Si ese contexto no está puesto en **la misma conexión** que la transacción, la
política no filtra o filtra por el usuario equivocado. El ADR anterior lo llamaba,
con razón, el riesgo más grave del proyecto: una fuga de datos entre usuarios.

Con catorce servicios cambian tres cosas:

1. **Catorce pools** en lugar de uno. El total de conexiones contra el clúster deja
   de ser un número que se elige y pasa a ser una suma que hay que dimensionar.
2. **Catorce roles de base**, uno por servicio ([[ADR-017 Propiedad de datos por servicio]]). El rol ya no identifica al *tipo de usuario*: identifica al
   *servicio*. El rol del usuario tiene que viajar aparte.
3. **El contexto tiene que cruzar la red.** Cuando `aportes` le pide a
   `nucleo-financiero` que escriba un asiento, la operación se ejecuta a nombre de un
   usuario que `nucleo-financiero` nunca autenticó.

## Decisión

**`SET LOCAL` dentro de la transacción, en toda consulta, en los catorce servicios.
El contexto de sesión se propaga entre servicios y se vuelve a fijar en cada uno.**

### El contexto se fija en el organismo, con la transacción

```java
@Transactional
public <T> T conContexto(ContextoSesion ctx, Function<DSLContext, T> fn) {
    if (ctx.usuarioId() == null || ctx.rol() == null) throw new SinContextoDeSesion();
    dsl.execute("select set_config('app.usuario_id', ?, true)", ctx.usuarioId());
    dsl.execute("select set_config('app.rol',        ?, true)", ctx.rol());
    dsl.execute("select set_config('app.traza',      ?, true)", ctx.traza());
    return fn.apply(dsl);
}
```

`set_config(…, true)` es `SET LOCAL`: **muere en el `COMMIT`**. Nunca `SET` plano —
con un pool, un `SET` plano sobrevive a la petición y contamina a la siguiente.

**Sin contexto no hay consulta.** Una transacción que llega sin `usuarioId` falla
antes de tocar la base. No se «asume el usuario del sistema»: la ausencia de contexto
es un defecto, no un caso.

### El contexto cruza la red y se vuelve a verificar

La llamada entre servicios propaga el **token del usuario original**, no un usuario
técnico:

| Se propaga | Cómo |
| --- | --- |
| Identidad del usuario | El JWT original, reenviado en `Authorization` |
| Traza | `x-request-id`, propagado por el cliente generado |
| Idempotencia | `Idempotency-Key` derivada del hecho, no reenviada tal cual |

El servicio receptor **valida el JWT él mismo** y arma su propio `ContextoSesion`.
No confía en una cabecera que diga quién es el usuario: eso convertiría a cualquier
servicio comprometido en un suplantador universal
([[ADR-024 Autenticación y sesión distribuida]]).

Cuando un trabajo programado o un consumidor de evento actúa sin usuario, corre con
un contexto de sistema **explícito y nombrado** (`app.rol = 'sistema'`), con sus
propias políticas de fila. Es un rol, no una excepción a las políticas.

### Pooling

| Pieza | Decisión |
| --- | --- |
| Pool en el proceso | **HikariCP**, el de Spring Boot, `maximumPoolSize` por servicio |
| Pool externo | **PgBouncer en modo transacción**, una instancia por delante del clúster |
| Sentencias preparadas | Con PgBouncer en modo transacción, `prepareThreshold=0` en el driver |
| Dimensionamiento | La suma de los catorce `maximumPoolSize` **más** los relevos de outbox no supera `max_connections` menos el margen de administración. Se declara en un solo documento y el arranque lo advierte |
| Réplica de lectura | Un segundo `DataSource` con `rol_auditor`, solo para `auditoria` y para listados pesados ([[ADR-011 Lecturas y réplica]]) |

Los servicios de bajo tráfico arrancan con pools chicos (5) y los de dinero con pools
mayores (20). El número total es una decisión de operación, no de cada carril: un
servicio que sube su pool sin mirar la suma le roba conexiones a los otros trece.

## Motivo

**Es la misma decisión de [[ADR-007 Sesión, RLS y pooling]], y sigue siendo
correcta.** `SET LOCAL` dentro de la transacción es la única forma de que el contexto
y la consulta compartan conexión con un pool en el medio. Lo que cambia es que ahora
hay que sostenerla catorce veces, y por eso vive en la biblioteca de plataforma y no
en cada servicio.

**Propagar el token del usuario y no un usuario técnico** es lo que mantiene la RLS
con sentido en un sistema distribuido. Si `nucleo-financiero` ejecutara todo como
«servicio de billetera», las políticas de fila dejarían de proteger nada en la mitad
más sensible del sistema.

**Revalidar el JWT en cada servicio** cuesta unos microsegundos de verificación de
firma y elimina la clase entera de ataques de confianza transitiva. Es barato y no
se negocia.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Cabecera `X-Usuario-Id` puesta por el gateway y creída por los servicios** | Cómodo y frágil: cualquiera que alcance la red interna suplanta a cualquiera. La red interna no es un perímetro de confianza. |
| **Un usuario técnico por servicio, sin identidad del usuario final** | Anula la RLS donde más importa y borra del rastro de auditoría quién pidió la operación. |
| **`SET` plano al tomar la conexión** | Con pool, el contexto sobrevive a la petición. Es el defecto que este ADR existe para impedir. |
| **PgBouncer en modo sesión** | Permitiría `SET` plano y sentencias preparadas, a cambio de fijar una conexión por cliente: con catorce servicios, insostenible. |
| **Sin PgBouncer, solo HikariCP** | Catorce pools contra la primaria sin nada que los amortigüe. Funciona hasta el primer pico. |
| **Filtrar por usuario en el `WHERE` de la aplicación, sin RLS** | Un `WHERE` olvidado es una fuga; una política de fila olvidada es cero filas. La segunda falla es la que se quiere. |

## Consecuencias

**A favor**

- El aislamiento entre usuarios lo hace cumplir la base, en los catorce servicios,
  con el mismo mecanismo.
- La identidad del usuario original llega hasta el asiento contable, y el rastro de
  auditoría es completo de punta a punta.

**En contra, y hay que asumirlo**

- **Tres consultas de `set_config` por transacción.** Es medible y es el precio de la
  garantía. Van en el mismo viaje que el `BEGIN`.
- **El dimensionamiento de conexiones se vuelve un problema global** que ningún
  carril puede resolver solo. Por eso se declara en un lugar y se advierte al
  arrancar.
- Validar el JWT en cada salto significa que la rotación de claves tiene que llegar a
  los catorce servicios; se resuelve con JWKS publicado por `identidad`.
- Un token vencido a mitad de una cadena de llamadas hace fallar la cadena. El TTL se
  fija con holgura sobre la operación más larga.

## Cómo se verifica

- [ ] **Prueba negativa, obligatoria en cada servicio:** una consulta con el contexto
      de otro usuario devuelve **cero filas**, no un error.
- [ ] Ninguna consulta a una tabla con RLS ocurre fuera de `conContexto`.
- [ ] Ningún `SET` plano en ningún servicio: solo `set_config(…, true)`.
- [ ] Dos peticiones consecutivas que reutilizan la misma conexión del pool no
      comparten contexto.
- [ ] Una llamada entre servicios con un JWT inválido es rechazada por el receptor,
      aunque venga de la red interna.
- [ ] La suma de los pools declarados es menor que `max_connections`, verificada al
      arrancar.

## Ver también

[[ADR-007 Sesión, RLS y pooling]] · [[ADR-017 Propiedad de datos por servicio]] · [[ADR-024 Autenticación y sesión distribuida]] · [[ADR-011 Lecturas y réplica]] · [[Flujo de una transacción]] · [[_Arquitectura]]
