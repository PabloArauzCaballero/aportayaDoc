---
tags:
  - arquitectura
  - adr
titulo: "ADR-020 — Contratos: OpenAPI escrito primero, código generado"
estado: aceptada
fecha: 2026-08-16
---

# ADR-020 — Contratos OpenAPI primero

> Supera a [[ADR-006 Contratos y validación]], que escribía el contrato en Zod
> compartido y derivaba OpenAPI de él.

## Contexto

El contrato de un caso de uso tiene ahora **cuatro** consumidores, no dos: el
servicio que lo implementa, la app móvil, el backoffice y **los otros trece
servicios** que pueden necesitar llamarlo ([[ADR-014 Arquitectura de servicios]]).

La decisión anterior escribía el contrato una vez en TypeScript y lo compartía como
paquete. Eso ya no es posible: el servidor está en Java y los clientes en tres
lenguajes. La pregunta pasa a ser **cuál de los cuatro artefactos es la fuente**.

Y aparece un requisito que antes no existía: un carril tiene que poder **programar
contra un servicio que todavía no está implementado**. Es la condición para que las
olas de carriles funcionen — si `aportes` tiene que esperar a que
`nucleo-financiero` esté terminado, la concurrencia se evapora.

## Decisión

**La especificación OpenAPI 3.1 se escribe a mano, primero, y es la fuente. Todo lo
demás se genera de ella.**

```
servicios/<nombre>/src/main/resources/openapi/<nombre>.yaml   ← fuente, la escribe la persona
        ↓ openapi-generator
        ├── interfaces de controlador (Spring)   → el servicio las implementa
        ├── cliente Java                          → lo consumen los otros servicios
        └── cliente TypeScript                    → lo consumen app y backoffice
```

- **El contrato se escribe antes que la implementación.** Un caso de uso empieza por
  su fragmento de especificación, derivado de la sección **Contrato** del `CU-NN` de
  la bóveda.
- **El controlador implementa una interfaz generada.** Si la especificación cambia y
  el controlador no, **no compila**. La divergencia entre contrato e implementación
  deja de ser posible en lugar de ser detectada.
- **Validación con Bean Validation** (`jakarta.validation`) sobre los tipos
  generados, más `additionalProperties: false` en todo esquema de entrada: un campo
  desconocido es un error, no un dato ignorado.
- **Los códigos de error viajan en la especificación**, uno por respuesta
  documentada, con el formato `AP-CU<NN>-<nn>` de siempre.
- **Los ejemplos de la especificación son los ejemplos del caso de uso**, copiados de
  la bóveda, y el CI valida que la especificación los acepte.

### El contrato es lo que desbloquea el carril

Un carril que necesita un servicio ajeno **no espera**: toma su `.yaml` ya escrito,
genera el cliente y programa contra un doble. Cuando el otro servicio esté listo, el
cliente ya es el correcto porque salió de la misma fuente.

Por eso las **especificaciones se escriben antes que las implementaciones, en la ola
anterior a la que las necesita**. Es el único artefacto que cruza carriles, y por eso
es el único que se planifica con anticipación.

## Motivo

**Con cuatro consumidores en tres lenguajes, la fuente tiene que ser neutral.**
Derivar OpenAPI de Zod funcionaba cuando el servidor y los clientes hablaban el mismo
idioma. Derivarlo de anotaciones de Java tendría el mismo defecto en espejo: el
contrato quedaría escondido en el código del servidor y los clientes lo recibirían de
segunda mano.

**Escribir el contrato primero es lo que hace posible la concurrencia.** Es la
diferencia entre catorce carriles que se esperan y catorce que avanzan. Este es el
argumento decisivo, y es de organización del trabajo antes que de arquitectura.

**Un archivo por servicio elimina el conflicto que el plan anterior tuvo que
neutralizar.** El `openapi.json` único generaba un conflicto de merge por PR y se
«resolvía» regenerando, que es como se cuela una divergencia. Catorce archivos, cada
uno con un dueño, no tienen ese problema.

**Generar la interfaz del servidor y no solo el cliente** es lo que convierte el
contrato en obligatorio. Un contrato que solo genera clientes es documentación; uno
que el servidor tiene que implementar es un contrato.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Código primero, con `springdoc` generando la especificación desde anotaciones** | Lo más cómodo y lo que hace la mayoría. El contrato deja de ser un acuerdo y pasa a ser un reflejo: no se puede escribir antes de la implementación, y por lo tanto no desbloquea al carril que espera. |
| **Seguir con Zod como fuente** | Requiere un generador de Java desde Zod que no existe con calidad, y pone la fuente del contrato del backend en un paquete de TypeScript. |
| **gRPC / Protobuf entre servicios** | Mejor rendimiento y contrato más estricto, y a cambio: dos protocolos que mantener (porque el cliente móvil y el navegador quieren REST), y una segunda definición del mismo contrato. Se reevalúa si la latencia interna se vuelve un problema medido. |
| **Sin contrato formal, JSON acordado por conversación** | Con catorce servicios y cinco máquinas, es la forma más rápida de tener catorce interpretaciones. |
| **Un repositorio central de contratos** | Devuelve el archivo compartido que se acaba de eliminar. La especificación vive con el servicio que la cumple. |

## Consecuencias

**A favor**

- La divergencia entre contrato e implementación no compila.
- Un carril puede empezar contra un servicio inexistente.
- El cliente de la app y el del backoffice se generan, no se escriben: se recupera
  buena parte de lo que se perdió al dejar el tipo compartido.

**En contra, y hay que asumirlo**

- **Escribir YAML a mano es más incómodo que anotar código.** Es el costo directo de
  que el contrato sea anterior a la implementación, y se paga con una plantilla
  generada desde el `CU-NN` que deja el esqueleto hecho.
- **El código generado hay que revisarlo la primera vez** por servicio: el generador
  tiene opciones que producen resultados muy distintos, y se fijan una sola vez en la
  Ola 0.
- Un cambio de contrato rompe a los consumidores en tiempo de compilación. Eso es lo
  deseado, y obliga a versionar la ruta cuando el cambio no puede ser compatible.
- La app y el backoffice pierden el tipo compartido escrito a mano. El generado es
  menos elegante y más fiable.

## Cómo se verifica

- [ ] Existe un `openapi/<servicio>.yaml` por servicio, y ninguna ruta fuera de él.
- [ ] Todo controlador implementa una interfaz generada; ninguno declara `@GetMapping`
      suelto.
- [ ] Todo esquema de entrada declara `additionalProperties: false`.
- [ ] Los ejemplos de la especificación son los de la bóveda y validan contra ella.
- [ ] Todo código `AP-CU<NN>-<nn>` del caso de uso aparece como respuesta
      documentada.
- [ ] El cliente TypeScript regenerado no produce diff: si lo produce, alguien lo
      editó a mano.

## Ver también

[[ADR-006 Contratos y validación]] · [[ADR-014 Arquitectura de servicios]] · [[ADR-022 Comunicación entre servicios]] · [[ADR-004 Frontend]] · [[_CasosDeUso]] · [[_Arquitectura]]
