---
tags:
  - arquitectura
  - adr
titulo: "ADR-033 — Puertos y adaptadores dentro de la composición atómica"
estado: aceptada
fecha: 2026-08-19
---

# ADR-033 — Puertos y adaptadores

> Complementa a [[ADR-023 Composición atómica en Java]]: **no cambia las cuatro
> capas ni les cambia el nombre**. Le agrega lo que aquel ADR dejó sin escribir:
> dónde vive la interfaz de todo lo que está afuera, y cómo se elige cuál de sus
> implementaciones corre en cada entorno.

## Contexto

[[ADR-023 Composición atómica en Java]] descartó el vocabulario hexagonal
(`ports`, `adapters`) con un argumento que sigue en pie: renombrar lo que ya
nombran 62 skills y 99 casos de uso cuesta más de lo que aporta. Pero descartar el
**vocabulario** no es lo mismo que descartar el **mecanismo**, y el mecanismo hace
falta, porque hay cosas que van a tener más de una implementación desde el primer
día:

| Lo que está afuera | Por defecto | Después, o en otro entorno |
| --- | --- | --- |
| Mandar un mensaje | Bandeja interna y correo ([[ADR-035 Canales por defecto]]) | WhatsApp, SMS, voz |
| Guardar un archivo | Disco local ([[ADR-034 Almacenamiento de archivos]]) | S3 / MinIO |
| Cobrar y pagar | Simulador de dev | QR interoperable, ACH, adquirencia |
| Facturar | Simulador de dev | Servicio de impuestos |
| Firmar y verificar la sesión | Llaves locales | Gestor de secretos |

Sin una regla escrita, cada uno de esos cinco se resuelve distinto, y el `if
proveedor == …` termina dentro del caso de uso, que es exactamente donde no puede
estar: el organismo es la transacción del negocio, no el lugar donde se elige
proveedor.

Y hay una segunda razón, más inmediata: **en desarrollo nada tiene que salir a
internet**. Sin adaptador local por defecto, el primer día de trabajo depende de
credenciales de terceros que todavía no existen.

## Decisión

**Todo lo que está afuera del proceso entra por un puerto, y un puerto es una
interfaz de `dominio/`. Las implementaciones son adaptadores de
`infraestructura/`, se eligen por configuración, y siempre hay una local por
defecto.**

```
bo.aportaya.<servicio>
├── web/
├── aplicacion/                    ← conoce el puerto; no conoce ningún adaptador
├── infraestructura/
│   ├── repositorios/              jOOQ
│   ├── clientes/                  otros servicios
│   └── adaptadores/
│       ├── mensajeria/            BandejaAdaptador · CorreoSmtpAdaptador · WhatsAppAdaptador
│       ├── archivos/              AlmacenLocalAdaptador · AlmacenS3Adaptador
│       └── pagos/                 SimuladorAdaptador · QrInteropAdaptador
└── dominio/
    └── puertos/                   EnvioDeMensaje · AlmacenDeArchivos · PasarelaDePago
```

**Las cinco reglas del puerto**

1. **El puerto lo define el dominio, no el proveedor.** Se escribe con las palabras
   del glosario (`enviar`, `guardar`, `cobrar`), nunca con las del SDK. Si la firma
   del método tiene un tipo del proveedor, el puerto está mal.
2. **Una interfaz por puerto, en `dominio/puertos/`.** Sin anotaciones de Spring y
   sin excepciones de terceros: los errores que declara son del dominio.
3. **Los adaptadores viven en `infraestructura/adaptadores/<puerto>/` y su nombre
   termina en `Adaptador`.** El nombre de la marca aparece ahí y **en ningún otro
   lugar del servicio**.
4. **La implementación se elige por configuración, no por código.** Una propiedad
   por puerto, con el adaptador local como valor por omisión:

   ```yaml
   aportaya:
     adaptadores:
       mensajeria: bandeja,correo     # dev: nada sale a internet
       archivos:   local              # dev y arranque: disco
       pagos:      simulador
   ```

   ```java
   @Component
   @ConditionalOnProperty(name = "aportaya.adaptadores.archivos",
                          havingValue = "local", matchIfMissing = true)
   class AlmacenLocalAdaptador implements AlmacenDeArchivos { … }
   ```

   `matchIfMissing = true` en el local: **la omisión nunca elige un tercero.**
5. **Todo puerto tiene tres implementaciones vivas**: la local (por defecto), la
   real (la que corre en producción) y la falsa de pruebas. La falsa se mantiene
   con el mismo cuidado que las otras dos, porque es la que corre miles de veces.

**Qué hace un adaptador, siempre y sin excepción** (lo detalla la skill
`proveedores-externos`):

- Declara **qué soporta**: canales, monedas, tamaños, límites por segundo.
- Traduce el error del tercero a un **error del dominio**. Un `SocketTimeout` no
  llega al caso de uso, y menos a la respuesta HTTP (`errores-api`).
- Tiene **tiempo de espera y reintento acotados**, y no reintenta lo no idempotente.
- **Mide su salud** y su costo real; la conmutación la decide quien enruta, no el
  adaptador ([[ADR-022 Comunicación entre servicios]]).
- **No abre transacciones ni escribe en tablas del negocio.** Lo que hay que
  registrar lo registra el organismo.

**El adaptador no es la frontera transaccional.** Un efecto externo que tiene que
ocurrir "sí o sí junto con" el cambio de estado no se manda desde el adaptador
dentro de la transacción: se encola en el outbox y el relevo llama al adaptador
después del `COMMIT` ([[ADR-018 Outbox transaccional y mensajería]]). El puerto no
cambia esa regla; se usa **desde** el relevo.

## Motivo

**No contradice a ADR-023: le pone el mecanismo que le faltaba.** Las capas y sus
nombres quedan iguales, la dirección de dependencia queda igual, y las pruebas de
ArchUnit se amplían en vez de reescribirse. Lo único nuevo es que el borde con el
mundo deja de ser "una molécula cualquiera" y pasa a tener contrato propio.

**Empezar sin credenciales de nadie.** Con el local por defecto, el primer día se
programa, se prueba y se demuestra el flujo completo —incluido el correo, que cae
en un buzón local— sin haber firmado un contrato con ningún proveedor. Eso es lo
que permite arrancar el desarrollo ya.

**Cambiar de proveedor sin tocar un caso de uso.** El día que se firme con un
proveedor de WhatsApp, lo que se escribe es un adaptador y una línea de
configuración. Ningún `CU<NN>` cambia, y por lo tanto ninguna prueba de aceptación
cambia: si cambiara, sería la señal de que el proveedor se había filtrado adentro.

**La prueba deja de necesitar la red.** Un caso de uso que depende de un puerto se
prueba con la implementación falsa en milisegundos. Hoy la alternativa sería
levantar un doble HTTP por proveedor, que es lento y frágil.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Renombrar las capas a `ports` / `adapters`** | Es la discusión que [[ADR-023 Composición atómica en Java]] ya cerró. El vocabulario del proyecto es átomo/molécula/organismo y lo hablan la bóveda, las skills y los casos de uso. Se adopta la mecánica, no el diccionario. |
| **Un módulo Gradle por adaptador** | Aísla más y cuesta decenas de proyectos y un ciclo de compilación mucho más lento, para un problema que ArchUnit ya resuelve. |
| **Elegir el proveedor con un `if` en el caso de uso** | Mete al proveedor en el organismo: la prueba de aceptación del CU empieza a depender de con quién se firmó un contrato comercial. |
| **Elegir con perfiles de Spring (`@Profile("dev")`)** | El perfil describe **entornos**, no proveedores. Con perfiles no se puede tener correo real y pagos simulados en el mismo entorno, que es justo lo que hace falta en QA. |
| **Interfaz "por si acaso" para todo** | Un puerto para algo que nunca va a tener una segunda implementación es una indirección sin beneficio. Se escribe puerto cuando ya hay dos implementaciones o cuando la segunda es la de pruebas. |

## Consecuencias

**A favor**

- El desarrollo arranca sin depender de terceros, y dev no manda nada afuera.
- El nombre de un proveedor se busca con `grep` y aparece en un solo paquete.
- Los casos de uso se prueban sin red y sin dobles HTTP.

**En contra, y hay que asumirlo**

- **Tres implementaciones por puerto es mantenimiento real.** La falsa se
  desactualiza en silencio si nadie la mira; por eso la prueba de contrato de puerto
  (abajo) corre contra **todas** las implementaciones, la falsa incluida.
- **Un puerto mal definido es peor que ninguno.** Si se calca la API del primer
  proveedor, el segundo adaptador no entra y hay que rehacer el puerto y sus
  llamadas. La regla 1 existe por esto.
- El local por defecto puede **esconder** un problema que solo aparece con el
  proveedor real. Se compensa con un entorno de QA que corre los adaptadores reales
  contra los sandbox de cada proveedor.

## Cómo se verifica

- [ ] ArchUnit: toda clase de `dominio/puertos/` es una **interfaz**, y no depende
      de Spring, jOOQ ni de ningún paquete de proveedor.
- [ ] ArchUnit: nadie fuera de `infraestructura/adaptadores/..` implementa un puerto.
- [ ] ArchUnit: ninguna clase de `aplicacion/` importa `..infraestructura.adaptadores..`.
- [ ] ArchUnit: toda clase de `..adaptadores..` termina en `Adaptador` y es
      package-private o `@Component`.
- [ ] Prueba de contrato de puerto: **una** suite por puerto, parametrizada, que
      corre contra el adaptador local, el falso y —cuando hay credenciales de
      sandbox— el real. Si un adaptador no la pasa, no se despliega.
- [ ] Arranque con configuración vacía: todos los puertos resuelven a su adaptador
      local. Ningún `NoSuchBeanDefinitionException`, ninguna llamada saliente.
- [ ] `grep` del nombre de cada proveedor: cero apariciones fuera de
      `infraestructura/adaptadores/` y de las semillas de catálogo.

## Ver también

[[ADR-023 Composición atómica en Java]] · [[ADR-034 Almacenamiento de archivos]] ·
[[ADR-035 Canales por defecto]] · [[ADR-018 Outbox transaccional y mensajería]] ·
[[ADR-022 Comunicación entre servicios]] · [[Método de arquitectura]] ·
`proveedores-externos` · `back-spring`
