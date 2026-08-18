---
name: back-spring
description: "Escribir el backend de AportaYa en Java 21 con Spring Boot: un servicio por módulo de la bóveda, un caso de uso por clase, la transacción en el organismo, contexto de RLS, controladores que implementan la interfaz generada, adaptadores externos e inyección de dependencias. Úsala al crear un endpoint, un caso de uso, un servicio nuevo o al tocar cualquier código de servicios/."
---

# Backend con Spring Boot

Un **servicio desplegable** por módulo de la bóveda; dentro, la
[[ADR-023 Composición atómica en Java|composición atómica]] hecha paquetes.

```
servicios/aportes/
├── build.gradle.kts
├── src/main/resources/
│   ├── application.yml
│   └── openapi/aportes.yaml                  ← el contrato, SE ESCRIBE PRIMERO
└── src/main/java/bo/aportaya/aportes/
    ├── aplicacion/       ORGANISMOS  · CU21CobrarAporte.java      ← abre la transacción
    ├── dominio/          ÁTOMOS      · CalculoDeAporte.java       ← puro, sin Spring
    ├── infraestructura/  MOLÉCULAS   · ObligacionRepositorio.java ← SQL, sin lógica
    │                                 · NucleoFinancieroCliente.java ← otro servicio
    │                                 · PasarelaQrAdapter.java     ← proveedor externo
    ├── web/              PÁGINA      · AportesController.java     ← implementa lo generado
    └── trabajos/                     · cron con ShedLock, consumidores de Kafka
```

## El orden en que se escribe

**El contrato primero, siempre.** Un caso de uso empieza por su operación en
`openapi/<servicio>.yaml`, derivada de la sección **Contrato** del `CU-NN`. No es
formalismo: es lo que permite que otro carril genere el cliente y programe contra vos
sin esperar a que termines ([[ADR-020 Contratos OpenAPI primero]]).

```
1  openapi/<servicio>.yaml        la operación, con sus códigos de error
2  dominio/                       los átomos que el cálculo necesita
3  infraestructura/               repositorios y clientes, sin lógica
4  aplicacion/CU<NN>*.java        el organismo: orquesta y abre la transacción
5  web/<X>Controller.java         implementa la interfaz generada
6  pruebas                        las siete obligatorias
```

## El organismo — un caso de uso, una clase, una transacción

```java
@Service
public class CU21CobrarAporte {

    private final ObligacionRepositorio obligaciones;
    private final NucleoFinancieroCliente nucleo;
    private final Outbox outbox;

    @Transactional
    public ResultadoCobro ejecutar(EntradaCobro entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var obligacion = obligaciones.tomarParaActualizar(dsl, entrada.obligacionId());
            var calculo    = CalculoDeAporte.de(obligacion, entrada.fecha());   // átomo puro
            obligaciones.marcarEnCobro(dsl, obligacion, calculo);
            outbox.emitir(dsl, "aportes.aporte_en_cobro", carga(obligacion));
            return ResultadoCobro.de(obligacion, calculo);
        });
    }
}
```

| Regla | Por qué |
| --- | --- |
| **`@Transactional` solo en `aplicacion/`** | Un repositorio transaccional elige la frontera por su cuenta, y deja de haber una transacción por caso de uso. ArchUnit lo verifica |
| **Ninguna llamada de red dentro** | Ni a un proveedor ni a otro servicio. Lo que haya que preguntar se pregunta **antes** del `BEGIN` |
| **El cálculo va en un átomo** | Si el organismo tiene un `if` de negocio, ese `if` pertenece a `dominio/` |
| **El outbox se escribe en la misma transacción** | O están el hecho y el aviso, o ninguno |

## El controlador no decide nada

```java
@RestController
public class AportesController implements AportesApi {   // ← interfaz GENERADA

    @Override
    public ResponseEntity<RespuestaCobro> cobrarAporte(SolicitudCobro cuerpo,
                                                       String idempotencyKey) {
        var salida = cu21.ejecutar(mapear(cuerpo), contexto.actual());
        return ResponseEntity.ok(mapear(salida));
    }
}
```

- **Implementa la interfaz generada del OpenAPI.** Si el contrato cambia y el
  controlador no, **no compila**: la divergencia deja de ser posible en vez de ser
  detectada.
- Ningún `@GetMapping` suelto. Si aparece uno, la ruta no está en el contrato.
- Cero reglas de negocio, cero cálculos, cero condiciones. Traduce y delega.
- El permiso se declara por endpoint. **Sin permiso declarado, el servicio no arranca.**

## Inyección de dependencias

Por **constructor**, siempre. Nada de `@Autowired` en campos: un campo inyectado
esconde la dependencia y hace imposible construir la clase en una prueba unitaria.

Los adaptadores externos y los clientes de otros servicios entran **detrás de una
interfaz del dominio**, para poder sustituirlos por un doble sin tocar el organismo.

## Lo que este servicio no puede hacer

| Prohibido | Por qué |
| --- | --- |
| Importar `bo.aportaya.<otro-servicio>` | Solo `plataforma/*` y el cliente generado del otro. ArchUnit lo verifica |
| Consultar el esquema de otro servicio | No tiene `GRANT`, y jOOQ no le generó las clases ([[ADR-017 Propiedad de datos por servicio]]) |
| Escribir el libro contable | Solo `nucleo-financiero`. Los demás se lo piden con clave de idempotencia |
| Usar JPA o Hibernate | Prohibido por [[ADR-016 Acceso a datos con jOOQ]], no desaconsejado |
| Confiar en una cabecera que diga quién es el usuario | Cada servicio valida la firma del JWT él mismo ([[ADR-024 Autenticación y sesión distribuida]]) |
| `double` o `float` en cualquier cosa que sea dinero | [[ADR-019 Dinero con BigDecimal]] |

## Configuración

Cada servicio trae su `application.yml` y **valida al arrancar**: si falta una clave,
el proceso no levanta y dice cuál. No hay archivo de configuración compartido — es lo
que elimina el conflicto que un `.env` común producía en cada PR.

`spring.threads.virtual.enabled=true`. La carga es de entrada/salida; los hilos
virtuales la resuelven sin pedir programación reactiva.

## Cuando hay que llamar a otro servicio

Sincrónico solo si la respuesta hace falta para responder; si no, evento por outbox.
Toda llamada lleva timeout, reintento y cortacircuitos, y **nunca ocurre dentro de una
transacción abierta**. Si la operación cruza servicios y mueve dinero, es una saga:
está en la skill `servicios-y-sagas`.

## Antes de dar por terminado

- [ ] La operación está en el OpenAPI y el controlador implementa la interfaz generada
- [ ] `@Transactional` solo en `aplicacion/`
- [ ] Ninguna llamada de red dentro de la transacción
- [ ] El átomo del cálculo no depende de Spring ni de jOOQ
- [ ] Permiso declarado por endpoint
- [ ] Las siete pruebas obligatorias (`pruebas-cu`)
- [ ] ArchUnit en verde

## Ver también

`arquitectura-atomica` · `datos-jooq` · `contratos-api` · `servicios-y-sagas` ·
`dinero-decimal` · `errores-api` · `seguridad-sesion-rls` · `pruebas-cu`
