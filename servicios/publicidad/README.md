# publicidad — anunciantes, campanas, entrega y liquidacion

El servicio que deja que un organizador promocione su pasanaku y que un negocio de
afuera pague por aparecer en la app. Cubre las cinco piezas del ciclo: quien anuncia,
que campana arma, que se le deja mostrar, cuando se le muestra y cuanto se le cobra.

**Lo que este servicio no hace: emitir comprobantes fiscales ni abrir cuentas por
cobrar.** Las dos cosas viven en otros esquemas —`tarifas.factura_electronica` y
`erp.cuenta_por_cobrar`— y publicidad no las escribe (invariante 11). Llegan como dato
a la liquidacion, ya emitidas por quien orquesta la corrida mensual.

## Casos de uso

| CU | Que resuelve | Entrada por |
| --- | --- | --- |
| CU-110 | Postular un socio comercial, verificarlo y abrir la cuenta del anunciante | `POST /publicidad/anunciantes` |
| CU-111 | Armar la campana con sus conjuntos, y aprobarla o rechazarla | `POST /publicidad/campanas` |
| CU-112 | Subir una pieza creativa y moderarla antes de que se muestre | `POST /publicidad/piezas-creativas` |
| CU-113 | Elegir que anuncio entregar, y medir impresion, clic y conversion | `POST /publicidad/espacios/{id}/entrega` |
| CU-114 | Facturar el gasto del mes de una cuenta publicitaria | `POST /publicidad/cuentas/{id}/liquidaciones` |

El contrato completo, con los codigos de error de la boveda y las divergencias
declaradas, esta en [`openapi/publicidad.yaml`](src/main/resources/openapi/publicidad.yaml).

## Las cuatro decisiones que gobiernan este carril

**1 · Un anunciante tiene un dueno, y solo uno.** O es un organizador de la plataforma
o es un negocio externo (R-PUB-01). Con dos referencias, el gasto de una campana
tendria dos deudores posibles y la liquidacion podria cobrarsele a cualquiera. El
CHECK lo sostiene; el caso de uso lo comprueba antes para poder decir cual de las dos
sobra.

**2 · Nada se muestra sin que una persona lo haya mirado.** Moderacion previa, nunca
posterior. El trigger `fn_pub_creativa_aprobada` rechaza el alta de un anuncio cuya
pieza no este APROBADA: aunque el caso de uso tuviera un defecto, no hay camino por el
que una pieza sin revisar llegue a pantalla. Y quien sube no se autoaprueba (R-PUB-05).

**3 · Nunca se entrega por encima del presupuesto del dia.** El gasto no se guarda en
una columna contador —seria un candado sobre el conjunto, y las entregas son
concurrentes por definicion— sino que se suma de las impresiones y los clics de la
fecha. El conjunto se bloquea primero y se suma despues, **en dos sentencias**: bajo
READ COMMITTED, `FOR UPDATE` releé la fila bloqueada pero no el resto de la consulta,
y una sola sentencia dejaria pasar la segunda entrega con la foto vieja del gasto.

**4 · El redondeo se hace una vez, sobre el total.** Una impresion cuesta fracciones de
centavo y se guarda con cuatro decimales; la factura va al centavo. Redondear cada
impresion y despues sumar convertiria un error de milesimas en uno de bolivianos sobre
millones de entregas.

## Pruebas

```bash
./gradlew :servicios:publicidad:test              # atomos, sin base
./gradlew :servicios:publicidad:integrationTest   # los cinco CU contra Postgres real
./gradlew :servicios:publicidad:testBarrido       # tamano de archivo y umbrales literales
```

Cada CU tiene dos archivos: `CU11nTest` con un `@Test` por criterio de aceptacion, y
`CU11nRechazosTest` con un `@Test` por restriccion citada. Los rechazos se comprueban
contra la base, no contra el codigo: `rechazaLaBase(sql, params)` ejecuta el SQL
prohibido y devuelve el mensaje con que Postgres lo frena.

La prueba de concurrencia de CU-113 encontro un sobregasto real: con una sola consulta
las dos entregas simultaneas pasaban. Esta ahi para que no vuelva.

## Huecos declarados

Estan en [`planes/informes/carril-3E-publicidad.md`](../../planes/informes/carril-3E-publicidad.md).
Ninguno se relleno con una suposicion.
