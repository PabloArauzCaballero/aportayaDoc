---
tags:
  - arquitectura
  - adr
titulo: "ADR-023 — Composición atómica en Java: las cuatro capas de un servicio"
estado: aceptada
fecha: 2026-08-16
---

# ADR-023 — Composición atómica en Java

> Supera a [[ADR-009 Composición atómica]], que fijaba los cuatro niveles en carpetas
> de TypeScript dentro de un módulo de NestJS.

## Contexto

La composición atómica —átomo, molécula, organismo, página— es el criterio con el que
este proyecto decide dónde va un archivo nuevo y qué prueba le corresponde. La idea no
cambia con el lenguaje. Lo que cambia es dónde se apoya para hacerse cumplir: en
TypeScript era una regla de lint sobre rutas de importación; en Java hay paquetes,
visibilidad y verificación de arquitectura de primera clase.

Y hay una capa nueva que antes no existía: el código que **llama a otro servicio**.
No es un repositorio (no habla con la base) ni un adaptador de proveedor externo (no
habla con un tercero): es un cliente de un par, y tiene sus propias reglas de
resiliencia ([[ADR-022 Comunicación entre servicios]]).

## Decisión

**Los cuatro niveles se conservan, como subpaquetes de `bo.aportaya.<servicio>`, con
la dirección de dependencia verificada por ArchUnit.**

```
bo.aportaya.<servicio>
├── web/               PÁGINA      implementa la interfaz generada del OpenAPI
├── aplicacion/        ORGANISMO   un caso de uso = una clase = una transacción
├── infraestructura/   MOLÉCULA    repositorios jOOQ, clientes de pares, adaptadores
├── dominio/           ÁTOMO       objetos de valor, cálculos y políticas puras
└── trabajos/                      trabajos programados y consumidores de evento
```

| Capa | Puede depender de | Nunca hace | Su prueba |
| --- | --- | --- | --- |
| `dominio/` — **átomo** | Nada del sistema. Ni Spring, ni jOOQ, ni `java.time.Clock` sin inyectar | IO, SQL, red, reloj o azar sin inyectar | Unitaria pura, sin contexto. `<Atomo>Test.java` |
| `infraestructura/` — **molécula** | `dominio/` | Abrir transacción, orquestar otro caso, contener un `if` de negocio | Integración contra PostgreSQL real. `<Repo>Test.java` |
| `aplicacion/` — **organismo** | `dominio/`, `infraestructura/` | SQL directo, llamar a un proveedor externo por su cuenta | Criterios de aceptación del CU. `CU<NN>Test.java` |
| `web/` — **página** | `aplicacion/` y los tipos generados | Cualquier regla de negocio o cálculo | De API contra el servicio levantado. `CU<NN>WebTest.java` |
| `trabajos/` | `aplicacion/` | Contener el caso de uso: lo invoca, no lo reimplementa | Prueba de consumidor idempotente |

### La transacción vive en el organismo, y en ningún otro lado

```java
@Service
public class CU31DevengarComision {
    @Transactional
    public ResultadoDevengo ejecutar(EntradaDevengo entrada, ContextoSesion ctx) { … }
}
```

`@Transactional` **solo** aparece en clases de `aplicacion/`. Un repositorio anotado
como transaccional es un rechazo de revisión: significa que la frontera transaccional
la está eligiendo la molécula, y entonces deja de haber una transacción por caso de
uso.

### Un servicio también es una composición

La composición no termina en el archivo. Con [[ADR-014 Arquitectura de servicios]],
el nivel superior es el **servicio**, y la regla de dependencia se aplica igual:

```
átomo      →  una clase de dominio
molécula   →  un repositorio, un cliente de par, un adaptador
organismo  →  un caso de uso
página     →  un endpoint
servicio   →  un módulo de la bóveda desplegado
```

**Un servicio no importa clases de otro servicio.** Solo `bo.aportaya.plataforma.*`
y el cliente generado del OpenAPI ajeno. Lo verifica ArchUnit, no la revisión.

### Cuándo se sube algo a plataforma

Un átomo se comparte cuando **dos servicios lo necesitan y significa lo mismo en
ambos**. `Dinero`, `PlazoHabil` y `Periodo` son de plataforma. Un cálculo de mora que
dos servicios usan parecido pero no igual **no** se comparte: se duplica a propósito
y se documenta por qué. Compartir lo que se parece, en lugar de lo que es igual, es
como catorce servicios vuelven a ser uno.

## Motivo

**ArchUnit convierte la regla en prueba.** Lo que en TypeScript era una configuración
de lint sobre rutas —fácil de eludir con un `import` creativo— acá es una prueba que
falla:

```java
@ArchTest static final ArchRule elDominioEsPuro =
    noClasses().that().resideInAPackage("..dominio..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "..infraestructura..", "..web..", "org.springframework..", "org.jooq..");
```

Corre con el resto de la suite, no necesita configuración por servicio y cubre las
clases que se escriban dentro de seis semanas.

**La visibilidad de paquete ayuda gratis.** Un repositorio puede ser
package-private y quedar invisible fuera de `infraestructura/`. En TypeScript había
que confiar en que nadie importara el archivo.

**El cliente de par va en `infraestructura/` y no en una capa nueva** porque su
naturaleza es la misma que la de un repositorio: traer datos de afuera detrás de una
interfaz del dominio. Que el afuera sea otro servicio en vez de una tabla no cambia
quién puede llamarlo ni qué no puede hacer.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Arquitectura hexagonal con nombres canónicos** (`ports`, `adapters`, `application`) | Es prácticamente la misma estructura con otros nombres. Se conservan los del proyecto porque toda la bóveda, las 62 skills y los 99 casos de uso ya hablan de átomos, moléculas y organismos. Cambiar el vocabulario cuesta más que lo que aporta. |
| **Un módulo Gradle por capa dentro de cada servicio** | Haría imposible saltarse la dependencia, y a cambio: cuatro proyectos por servicio, cincuenta y seis en total, y un ciclo de compilación mucho más lento. ArchUnit da la misma garantía sin ese costo. |
| **Paquete por caso de uso** (`bo.aportaya.aportes.cobraraporte.*`) | Agrupa mejor lo que cambia junto, y dispersa los átomos del dominio que varios casos de uso comparten. Con casos de uso que comparten agregado, gana el paquete por capa. |
| **Una capa nueva para los clientes de pares** | Tendría las mismas reglas que `infraestructura/`. Una capa que no restringe nada distinto es solo una carpeta más. |
| **Confiar en la revisión humana** | Con cinco carriles y un revisor, no escala. Es el argumento de todo este proyecto. |

## Consecuencias

**A favor**

- La dirección de dependencia se verifica en cada corrida, en los catorce servicios,
  con la misma prueba.
- El dominio queda sin Spring: se puede probar sin levantar contexto, y esas pruebas
  tardan milisegundos.
- El lugar de un archivo nuevo sigue siendo obvio, que era el objetivo original.

**En contra, y hay que asumirlo**

- **Mantener el dominio libre de Spring exige disciplina** en el punto donde más
  tienta romperla: inyectar un repositorio en una clase de dominio es un `@Autowired`
  de distancia. Por eso la prueba, y no la recomendación.
- Las cuatro capas en un servicio chico —`notificaciones` tiene cuatro casos de uso—
  se sienten sobradas. Se mantienen igual: catorce servicios con la misma forma valen
  más que catorce servicios óptimos cada uno a su manera.
- Duplicar a propósito lo que se parece pero no es igual va a incomodar en cada
  revisión, y hay que sostenerlo con el argumento escrito.

## Cómo se verifica

- [ ] Prueba de ArchUnit por servicio: `dominio/` no depende de Spring, jOOQ,
      `infraestructura/` ni `web/`.
- [ ] `@Transactional` solo aparece en `aplicacion/`.
- [ ] Ninguna clase importa `bo.aportaya.<otro-servicio>`.
- [ ] Todo caso de uso implementado tiene exactamente una clase en `aplicacion/`.
- [ ] Ninguna clase de `web/` contiene un cálculo o una condición de negocio.
- [ ] Todo lo que está en plataforma lo usan dos o más servicios; lo que lo usa uno
      solo, baja al servicio.

## Ver también

[[ADR-009 Composición atómica]] · [[ADR-014 Arquitectura de servicios]] · [[ADR-015 Lenguaje, runtime y framework]] · [[ADR-022 Comunicación entre servicios]] · [[Estructura del repositorio]] · [[_Arquitectura]]
