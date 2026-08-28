# Carril 3E · publicidad — informe de cierre

**Servicio:** `publicidad` · **Esquema:** `publicidad` · **Rol:** `svc_publicidad`
**Casos de uso:** CU-110 a CU-114 · **Rama:** `dev`

Cinco casos de uso implementados, con sus criterios de aceptacion y sus rechazos
verificados contra Postgres real. Lo que sigue son las divergencias entre lo que la
boveda dice y lo que la base hace. **Ninguna se relleno con una suposicion:** donde el
DDL contradice al caso de uso, gana el DDL, se implementa el comportamiento verdadero
mas cercano y queda escrito aca.

---

## Hueco 1 · `factura_publicidad` no puede cambiar de estado, nunca

`factura_publicidad` es append-only (R-AUD-01). Los pasos 3, 4 y 5 de CU-114 —emitir el
comprobante y pasar a FACTURADA, enlazar la cuenta por cobrar, pasar a COBRADA cuando
el anunciante paga— son todos `UPDATE`, y ninguno se puede ejecutar.

Consecuencia directa: **`factura_electronica_id` y `cuenta_por_cobrar_id` no se pueden
completar despues**. O la fila nace con ellos, o no los va a tener jamas. Y el estado
COBRADA es inalcanzable: no hay forma de que la factura registre que le pagaron.

Lo que se implementó es lo unico que la base admite: los dos identificadores **entran
como dato** de la liquidacion. Quien orquesta la corrida mensual pide el comprobante a
modulo 11 y la cuenta por cobrar a modulo 13, y recien despues llama a este caso de uso.
Eso ademas respeta el invariante 6 —ninguna llamada de red dentro de la transaccion— y
el 11 —publicidad no escribe el esquema de nadie.

`AP-CU114-03 EMISION_FISCAL_FALLIDA` lo devuelve, entonces, quien orquesta, no esta
operacion. Cuando el SIN falla, la liquidacion se llama sin `facturaElectronicaId` y la
factura queda GENERADA — con la salvedad de que ya no va a poder avanzar sola.

Demostrado en `CU114Test > criterio1` y `CU114RechazosTest > rechaza por R-AUD-01`.

---

## Hueco 2 · Un sobregasto real que la prueba de concurrencia encontro

La primera version de `EntregaRepositorio.candidatos` traia el gasto del dia en la misma
consulta que bloqueaba el conjunto, con un `LEFT JOIN LATERAL` y `FOR UPDATE OF cj`. Es
lo natural de escribir, y **deja pasar el sobregasto**.

Bajo READ COMMITTED, cuando el candado se libera PostgreSQL vuelve a leer la fila
bloqueada, pero no reevalua el resto de la consulta: la suma de impresiones se queda con
la foto del comienzo. La segunda entrega leia gasto cero y entregaba igual.

Va en dos sentencias: bloquear el conjunto primero, sumar el gasto despues. En READ
COMMITTED cada sentencia toma su propia foto, asi que la segunda ve lo que la primera
cometio. `CU113Test > concurrencia` falla con la version de una sola consulta.

**No es una divergencia con la boveda sino un defecto propio**, y queda escrito porque
es la clase de error que vuelve en cuanto alguien «simplifique» las dos consultas en
una.

---

## Hueco 3 · R-PUB-05 no alcanza a los socios comerciales

`fn_pub_moderador_distinto` comprueba que quien modera no sea el dueno de la pieza, y
para eso hace `JOIN organizador o ON o.id = a.organizador_id`. **Cuando el anunciante es
un socio comercial, ese join no da fila y el trigger deja pasar cualquier cosa.**

No es un descuido de redaccion: el modelo no tiene por donde llegar del socio comercial
a un usuario. `socio_comercial` guarda `email_contacto` y `verificado_por`, pero ningun
`usuario_id` propio.

El caso de uso hace la misma comprobacion que el trigger, con el mismo limite. Un socio
comercial que llegara a tener credenciales de moderador podria aprobar sus propias
piezas, y ni la base ni la aplicacion lo verian.

Queda declarado para que se decida en la boveda: o `socio_comercial` gana su
`usuario_id`, o el permiso `PUBLICIDAD_MODERAR` se declara incompatible con ser
anunciante.

---

## Hueco 4 · `campana_publicitaria` no tiene donde guardar el motivo del rechazo

CU-111 dice que Operaciones rechaza «con motivo». La tabla tiene `estado` y
`aprobada_por`, y ninguna columna de texto. El motivo viaja en el evento
`publicidad.campana_rechazada` y no queda en la fila.

Consecuencia: el anunciante ve el motivo por notificacion, pero quien mire la campana
seis meses despues solo va a ver RECHAZADA.

---

## Hueco 5 · `ck_campana_pub_aprobacion` deja ACTIVA sin aprobador... casi

El CHECK dice `estado IN ('BORRADOR','EN_REVISION','RECHAZADA') OR aprobada_por IS NOT
NULL`. Cubre bien ACTIVA, PAUSADA y FINALIZADA. Lo que **no** cubre es el camino
inverso: una campana que ya fue aprobada puede volver a BORRADOR conservando su
`aprobada_por`, y el CHECK no se queja.

No se explota en este carril —no hay operacion que retroceda el estado— pero esta
escrito porque el CHECK parece cubrir mas de lo que cubre.

---

## Hueco 6 · publicidad lee y escribe fuera de su esquema

Dos lugares:

1. **Lectura.** CU-110 comprueba que el organizador este HABILITADO leyendo
   `organizador.organizador`. La clave foranea `anunciante.organizador_id` ya ata las dos
   tablas, asi que el acoplamiento lo puso el modelo, no este carril. Preguntarlo por
   HTTP seria una llamada de red dentro de la transaccion (invariante 6).

2. **Escritura, en las pruebas.** `FixturaDePublicidad` crea filas en
   `organizador.organizador`, `erp.tercero_comercial`, `erp.cuenta_por_cobrar` y
   `tarifas.factura_electronica`, porque las claves foraneas de publicidad apuntan ahi.
   **En produccion no se escribe ninguna de las cuatro**; en las pruebas no hay otro modo
   de montar el escenario sin levantar cuatro servicios.

Es el mismo hueco que el carril de erp declaro para `nucleo_financiero`. La decision es
de la boveda: o los servicios exponen vistas de solo lectura con contrato propio, o el
invariante 11 admite explicitamente la lectura de las tablas a las que uno ya tiene
clave foranea.

---

## Hueco 7 · `revision_creativa` no tiene unico por pieza

`AP-CU112-01 PIEZA_YA_REVISADA` no lo puede sostener la base: no hay
`UNIQUE (pieza_creativa_id)` en `revision_creativa`. Dos moderadores simultaneos podrian
dejar dos revisiones sobre la misma pieza, y la segunda pisaria el estado que dejo la
primera.

Lo sostiene la aplicacion con `SELECT … FOR UPDATE` sobre `pieza_creativa` antes de
decidir. `CU112Test > concurrencia` lo demuestra: de dos moderadores a la vez, uno solo
deja revision.

Un unico en la tabla seria mas barato y no dependeria de que nadie escriba por fuera.

---

## Hueco 8 · El costo de una impresion no esta en ningun catalogo

CU-113 dice «`costo` segun `modelo_puja` del conjunto» y no dice mas. La conversion
concreta —en CPM la puja es por mil impresiones, en CPC la impresion es gratis y se
cobra el clic— es la convencion universal del rubro, pero **no esta escrita en la
boveda**.

Esta implementada en `SubastaDelEspacio` con el divisor expresado como
`BigDecimal.ONE.movePointRight(3)` y no como una constante suelta, para que
`testBarrido` no lo confunda con un umbral cableado. Si el negocio quiere otra
conversion, es un cambio de catalogo que hoy no tiene tabla donde vivir.

---

## Hueco 9 · La subasta elige por puja, y eso tampoco esta en la boveda

CU-113 dice «elige un anuncio elegible por espacio, segmentacion y puja» sin definir el
criterio. Se implementó la regla mas simple que cumple lo unico que el CU si exige —no
entregar sin presupuesto—: **gana la puja mas alta entre los que pueden pagar la
impresion**, y se desempata por identificador para que el resultado sea reproducible.

La segmentacion **no filtra todavia**: `segmento_audiencia.criterios` es un `jsonb`
libre y la boveda no define su forma. Un motor que la respete necesita ese contrato
primero.

---

## Verificado por maquina

```
./gradlew :servicios:publicidad:spotlessCheck build     BUILD SUCCESSFUL
./gradlew :servicios:publicidad:integrationTest         BUILD SUCCESSFUL · 51 pruebas
./gradlew :servicios:publicidad:testBarrido             BUILD SUCCESSFUL
python3 scripts/verificar_criterios.py                  Sin divergencias
python3 scripts/verificar_boveda.py                     TODO OK
python3 scripts/verificar_seguridad.py                  sin hallazgos
```

## Lo que no verifica ninguna maquina

**¿Los nombres dicen lo que las cosas son?** `SubastaDelEspacio` se llama asi porque eso
es lo que hace: una subasta por espacio, no un «selector». `ConsumoDelPeriodo` distingue
`bruto()` de `aFacturar()` porque son dos numeros distintos y confundirlos es el defecto
que el atomo existe para evitar.

**¿La frontera transaccional es la correcta?** La entrega escribe la impresion y carga
el costo a la campana en una transaccion: si se separaran, habria impresiones servidas
que nadie paga. El alta de CU-110 mete anunciante y cuenta juntos por lo mismo — un
anunciante sin cuenta no puede financiar nada y solo confunde a quien lo encuentre.

**¿Que supuse que no estaba en la boveda?** Tres cosas, y las tres estan arriba: la
conversion CPM/CPC (hueco 8), el criterio de la subasta (hueco 9) y las rutas HTTP, que
se eligieron bajo el prefijo reservado `/publicidad` siguiendo la forma de los otros
contratos.

**¿Que deje peor de como lo encontre?** Nada de la troncal. El unico cambio fuera de
`servicios/publicidad/` fue agregar `spring.boot.jdbc` a las dependencias de prueba de
su propio `build.gradle.kts`, igual que en los otros carriles que cablean la transaccion
a mano.
