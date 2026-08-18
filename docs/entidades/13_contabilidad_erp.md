# Módulo 13 — Contabilidad Financiera y ERP

> **Pregunta de negocio que responde este módulo:**
> *¿Puede la empresa cerrar un mes, saber cuánto le deben, cuánto debe, y mostrar
> un balance que cuadre — sin que nadie tenga que armarlo a mano en una hoja de
> cálculo?*

AportaYa ya lleva partida doble desde el día uno: `cuenta_contable`,
`asiento_contable` y `movimiento_contable` (módulo 03) registran cada pago,
cada entrega y cada cobertura del fondo de garantía como un asiento cuadrado.
Eso alcanza para explicar el saldo de un participante. No alcanza para operar
la empresa como negocio: pagarle a un proveedor de mensajería, presupuestar el
área de soporte, depreciar los servidores, o cerrar diciembre y decir "esto es
lo que ganamos este año".

Este módulo no crea un segundo libro contable. **Extiende el que ya existe**
con todo lo que le falta a una empresa real: jerarquía de cuentas, períodos que
se cierran de verdad, centros de costo, presupuesto, terceros comerciales,
activos fijos y estados financieros reproducibles. Cada tabla nueva que mueve
dinero termina, sin excepción, generando un `asiento_contable` en el mismo
libro de siempre.

> [!important] Un solo libro mayor, más orígenes
> `asiento_contable.origen_tipo` gana tres valores (`FACTURA_PROVEEDOR`,
> `COBRO_CXC`, `DEPRECIACION_ACTIVO`). No hay un `asiento_contable_erp` paralelo.
> Si algún día alguien propone una tabla de asientos "solo para lo
> administrativo", eso es una regresión: hay dos libros y dejan de cuadrar
> entre sí tarde o temprano.

## Plan de cuentas jerárquico (extensión de `cuenta_contable`, M3)

`cuenta_contable` era plana: una fila por cuenta, sin padre ni nivel. Un plan
de cuentas real necesita agrupar — "1.1 Activo Corriente" contiene "1.1.01
Caja" y "1.1.02 Bancos" — para poder sumarizar un balance sin recorrer cada
cuenta hoja a mano.

- **`cuenta_padre_id`** arma el árbol. Una cuenta sin padre es de primer
  nivel (activo, pasivo, patrimonio, ingreso, gasto).
- **`nivel`** es la profundidad, para renderizar el plan de cuentas indentado
  sin recalcularlo en cada consulta.
- **`es_cuenta_de_movimiento`** distingue una cuenta sumarizadora (un total,
  como "1.1 Activo Corriente") de una cuenta que efectivamente recibe líneas
  en `movimiento_contable` (como "1.1.01 Caja"). Cargar un movimiento contra
  una cuenta sumarizadora es un error de captura, no una operación válida —
  eso se hace CHECK/trigger en la fase de persistencia (fase 5).

**Por qué debe existir**: sin jerarquía, un balance general es una lista de
doscientas cuentas sin agrupar — ilegible para cualquiera que no memorizó el
plan de cuentas. Con jerarquía, `estado_financiero_generado` puede sumarizar
por rama del árbol.

## Ejercicio y período contable

| Tabla | Qué es |
| --- | --- |
| `ejercicio_fiscal` | El año contable completo, con apertura y cierre. |
| `periodo_contable` | Un mes dentro de un ejercicio. |
| `cierre_periodo_contable` | El acto de cerrar un mes: quién, cuándo, y el cuadre que quedó. |

**Para qué sirve (negocio)**: sin período, "cerrar el mes" es una convención
verbal — alguien decide que enero "ya está" pero nada en el sistema lo impide.
Con `periodo_contable.estado`, un asiento contra un período `CERRADO` se
rechaza en el modelo, no en la buena voluntad de quien lo captura.

**Por qué debe existir**: la regulación boliviana (y cualquier auditoría)
exige poder decir con certeza "los estados financieros de enero ya no
cambiaron después del 5 de febrero". Sin un período que se cierra de verdad,
esa afirmación no se puede sostener.

**A nivel de sistema**: cerrar es **irreversible por diseño** — no existe caso
de uso de "reabrir un período". Un ajuste posterior a un mes cerrado se
registra en el período siguiente, con su propia glosa que referencia el
período corregido. Eso es lo mismo que ya hace `asiento_contable` con
`asiento_reversa_id`: no se edita el pasado, se corrige hacia adelante.
`cierre_periodo_contable` es append-only y guarda `total_debe`/`total_haber`
como fotografía del momento del cierre, para poder demostrar después que en
ese momento cuadraba.

## Centros de costo y presupuesto

| Tabla | Qué es |
| --- | --- |
| `centro_costo` | Unidad de costeo: un área, un producto, una campaña. |
| `presupuesto` | Cabecera de presupuesto de un centro de costo para un ejercicio. |
| `partida_presupuestaria` | Línea de presupuesto: cuánto se autorizó gastar en una cuenta contable, en un período, y cuánto se ejecutó. |

**Para qué sirve (negocio)**: separa "cuánto cuesta el área de soporte" de
"qué cuenta contable es". Sin centro de costo, una factura de proveedor de
mensajería y una de infraestructura de servidores se ven igual en el balance
— ambas son "5.2 Gastos operativos" — y nadie puede responder cuánto cuesta
cada área del negocio por separado.

**Por qué debe existir**: sin presupuesto por centro de costo, "nos pasamos de
presupuesto" es una sensación, no un hecho verificable. `partida_presupuestaria.monto_ejecutado`
compara contra `monto_presupuestado` con datos, no con memoria.

**A nivel de sistema**: `centro_costo` es intencionalmente independiente de
`cuenta_contable` — la misma cuenta contable (ej. "5.1 Sueldos") puede
ejecutarse contra varios centros de costo. `campana_publicitaria` (módulo 14)
puede tener su propio centro de costo cuando se quiera medir el costo interno
de operar publicidad, no solo el ingreso que genera.

## Terceros comerciales, compras y cuentas por pagar

| Tabla | Qué es |
| --- | --- |
| `tercero_comercial` | Un proveedor o cliente comercial: a quién la empresa le compra o le vende fuera del circuito del pasanaku. |
| `orden_compra` | Una compra autorizada, previa a recibir la factura. |
| `factura_proveedor` | La cuenta por pagar: una factura recibida. |
| `pago_a_proveedor` | El egreso que la cancela, total o parcial. |

**Por qué debe existir, y por qué no es lo mismo que lo que ya existe**:
`tercero_comercial` **no es** `usuario` ni `proveedor_pago` (módulo 03).
`usuario` es alguien con billetera en la plataforma; `proveedor_pago` es la
pasarela que procesa cobros. `tercero_comercial` es la imprenta que factura
por las tarjetas de bienvenida, el proveedor de hosting, el estudio contable
externo — gente a la que la empresa le compra, no que compra en la
plataforma. Fusionarlo con `usuario` obligaría a darle una billetera a un
proveedor de oficina, que no tiene sentido.

**A nivel de sistema — segregación de funciones**: `factura_proveedor.aprobada_por`
(quien autoriza que la factura es legítima y se debe pagar) y
`pago_a_proveedor.autorizado_por` (quien ejecuta el pago) **no pueden ser la
misma persona sobre la misma factura**. Es el mismo principio que ya aplica
"autorizar entrega / ejecutar desembolso" en `roles-y-accesos`: la
verificación se hace al asignar el rol, no al momento de pagar. El par se
agrega a la tabla de segregación de funciones en fase 4.

`factura_proveedor` es **append-only** (mueve dinero): se anula con un
registro de anulación, nunca se borra ni se edita el monto.

## Cuentas por cobrar

| Tabla | Qué es |
| --- | --- |
| `cuenta_por_cobrar` | Un cobro pendiente a un tercero, de origen polimórfico. |
| `cobro_cuenta_por_cobrar` | El ingreso que la cancela. |

**Por qué debe existir**: hasta este módulo, todo lo que la plataforma cobra
pasa por la billetera de un `participante` (módulo 03/10). Pero la
facturación de publicidad (módulo 14) es a un `anunciante` — que puede ser un
`socio_comercial` externo sin billetera en la plataforma. `cuenta_por_cobrar`
es el puente: `origen_tipo = 'FACTURA_PUBLICIDAD'` apunta a
`factura_publicidad.id` (M14), y desde ahí todo el seguimiento de cobro es
igual de riguroso que el de cualquier otra plata que entra: pendiente,
cobrada parcial, cobrada, o declarada incobrable con motivo.

**A nivel de sistema**: `origen_tipo`/`origen_id` es polimórfica, igual que
`asiento_contable.origen_id` en M3 — mismo patrón, no uno nuevo. Es la
**única** tabla de M13 que M14 conoce: la publicidad no inventa su propio
circuito de cobro.

## Activos fijos y depreciación

| Tabla | Qué es |
| --- | --- |
| `categoria_activo_fijo` | Catálogo: tipo de bien, vida útil, método de depreciación y las tres cuentas contables que le corresponden. |
| `activo_fijo` | Un bien de uso concreto: laptop, servidor, mobiliario. |
| `depreciacion_activo` | Una corrida mensual de depreciación de un activo. |

**Para qué sirve (negocio)**: una empresa que compra servidores y
equipamiento tiene que reconocer que ese gasto se consume con el tiempo, no
todo el primer mes. Sin esto, el estado de resultados de enero se ve
artificialmente golpeado por la compra de una laptop que en realidad sirve
tres años.

**Por qué debe existir**: es un requisito contable básico para cualquier
empresa formal, y hoy no hay ninguna tabla que lo represente.

**A nivel de sistema**: `activo_fijo.valor_en_libros` es una columna
`GENERATED` = `costo_adquisicion - depreciacion_acumulada`, para que nunca
quede desincronizada de las corridas de `depreciacion_activo`. Cada corrida es
append-only y genera su propio `asiento_contable` (`DEBE` la cuenta de gasto
por depreciación, `HABER` la cuenta de depreciación acumulada) usando las
cuentas que define `categoria_activo_fijo` — así ningún operador elige a mano
contra qué cuenta contabilizar cada depreciación, evitando que dos activos de
la misma categoría terminen en cuentas distintas por error de captura.

## Plantillas de asiento y estados financieros

| Tabla | Qué es |
| --- | --- |
| `asiento_plantilla` / `linea_plantilla_asiento` | Una plantilla de asiento recurrente (alquiler, planilla mensual) para no recapturar el mismo asiento a mano cada mes. |
| `estado_financiero_generado` | Una fotografía de balance general o estado de resultados de un período, ya calculada y guardada. |

**Por qué debe existir**: sin plantilla, un asiento recurrente se recaptura
manualmente cada mes — fuente típica de error de tipeo en montos que después
no cuadran. Sin `estado_financiero_generado`, cada vez que alguien pide "el
balance de marzo" hay que recalcularlo desde cero contra la tabla completa de
movimientos, lo cual es lento y — peor — puede dar un resultado distinto si se
corre dos veces sobre datos que cambiaron entre medio (algo que no debería
poder pasar en un período cerrado, pero la fotografía lo hace verificable).

**A nivel de sistema**: `estado_financiero_generado` es append-only, con
`hash_contenido` para poder demostrar que el balance que se mostró en una
auditoría es exactamente el que se generó ese día — mismo patrón que
`registro_sellado` (módulo 09).

## Qué decisiones de diseño se descartaron

- **No se creó un "libro mayor" separado.** Se evaluó y se descartó: dos
  libros que deben coincidir es una fuente de descuadres, no una separación de
  responsabilidades.
- **No se fusionó `tercero_comercial` con `usuario`.** Un proveedor de oficina
  no necesita billetera, KYC ni MFA; forzarlo a esas tablas sería agregarle
  complejidad regulatoria a algo que no la necesita.
- **No se modeló una "subasta" de puja separada para activos fijos ni para
  presupuesto.** Innecesario: son procesos administrativos internos, no
  mercados.

## Ver también

- Skills: `contabilidad-partida-doble`, `dinero-decimal`, `datos-jooq`,
  `boveda-modelo`.
- [[cuenta_contable]] · [[asiento_contable]] · [[movimiento_contable]] (M3)
- [[07_organizador_automatizacion|Módulo 07]] — por qué el organizador nunca
  aparece como beneficiario en ninguna tabla de este módulo.
- [[14_publicidad_campanas|Módulo 14]] — origen de `cuenta_por_cobrar` vía
  `factura_publicidad`.
