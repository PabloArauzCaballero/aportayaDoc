---
tags:
  - plan
  - informe
  - carril
titulo: "Carril 3D — 04_entregas"
ola: 3
fase: 3
modulo: 04_entregas_fondo
rama: pablo/feature/carril-3D-entregas
estado: en curso
---

# Carril 3D — entregas

**Fase** 3 · **Casos de uso** 18, 22, 28 · **Máquina** mac

## Casos de uso

| CU | Contrato | Dominio | Infra | Aplicación | HTTP | Pruebas | Gate |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| CU-18 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-22 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |
| CU-28 | ✅ | ✅ | ✅ | ✅ | ⬜ | ✅ | ✅ |

## Lo que este carril decidió, y por qué

### 1 · Un intento de desembolso es una fila

La primera versión abría el intento al emitir la orden (`PENDIENTE`, número 1) y
**agregaba otra fila** al recibir la respuesta. Dos filas para un intento. El conteo
que decide si se reintenta —`intentosDe`— pasaba a contar el doble, y el tercer
reintento habría llegado al sexto.

Se corrigió: la respuesta **cierra** el intento abierto. Si no hay ninguno abierto —el
proveedor contestó algo que no se le pidió— se registra igual, porque perder esa
respuesta sería perder la única noticia que hay de esa plata.

### 2 · Los errores definitivos no se reintentan

`ReintentoDeDesembolso.DEFINITIVOS` distingue lo transitorio —el proveedor no
respondió— de lo que no cambia por esperar: cuenta inexistente, cuenta cerrada, titular
que no coincide, moneda incompatible. Insistir contra una cuenta cerrada no la reabre:
sólo demora el momento en que alguien mira el caso, mientras la plata del beneficiario
sigue retenida.

La espera crece por intento y es **determinista**. Un azar ahí haría que dos rearranques
del proceso programaran reintentos distintos para la misma orden.

### 3 · El número de cuenta no existe en claro en ninguna parte

La prueba de CU-18 no se conforma con verificar que la columna del cifrado tiene el
prefijo correcto: busca el número **en todas las columnas de texto de la fila y en el
payload del evento**. Un número completo en una columna termina en un respaldo, en un
volcado de desarrollo y en la pantalla de cualquiera con lectura — y para entonces ya
no hay forma de saber quién lo vio.

La pimienta se exige: `CuentaEnmascarada` falla si no la recibe. Sin ella el hash es
adivinable probando, porque el espacio de números de cuenta posibles es chico.

## Huecos declarados

| # | Qué falta o diverge | Dónde | Qué se hizo |
| :-: | --- | --- | --- |
| H-1 | El cifrado del número lo hace el almacén de llaves, que todavía no existe | `aplicacion/CU18` | El número **ya cifrado** entra como parámetro. El caso de uso nunca ve la llave, que es como tiene que ser; lo que falta es quien cifre |
| H-2 | `AP-CU18-05 MONEDA_INCOMPATIBLE` y `AP-CU28-03 SIN_PROVEEDOR_DISPONIBLE` no tienen caso que los ocupe. El enrutamiento de proveedor es de CU-99, en `aportes` | `openapi/entregas.yaml` | Declarados como reservados, no inventados |
| H-3 | Los nombres de varios `AP-CU18-nn` y `AP-CU28-nn` no describen el caso que el código ocupa | ídem | Se conservan los **números** —que son los que la bóveda ata a cada rechazo— y el contrato documenta qué significa cada uno. Renombrarlos es tocar `docs/` |
| H-4 | Ningún trabajo programado está cableado | `trabajos/` vacío | El motor que toma las órdenes con `reintentable_en` vencido es el candidato obvio. Sin el ShedLock del servicio arrancado no se puede probar |
| H-5 | La confirmación de recepción del beneficiario (`confirmacion_recepcion`) no tiene caso de uso en este carril | `sql/` | La tabla existe y nadie la escribe todavía. No se inventó un flujo para llenarla |

## Supuestos declarados

1. **La titularidad se compara por documento y por nombre.** El CU sólo dice «a nombre
   de otra persona». El documento es lo único que identifica sin ambigüedad; el nombre
   ataja el caso del documento mal tipeado que por azar coincide.
2. **Un tope de cuentas por usuario.** No está en la bóveda. Diez destinos distintos
   para la misma persona es un patrón, no una comodidad.
3. **La ventana de enfriamiento aplica también al desembolso**, no sólo al retiro. El
   CU-18 la menciona para el retiro; CU-28 la respeta porque el riesgo es el mismo.
4. **Reverificar no reinicia la ventana.** Si lo hiciera, reverificar sería una forma de
   manipular el reloj a voluntad.

## Fronteras transaccionales respondidas

### CU-18 · Registrar y verificar la cuenta
1. **Todo junto o nada:** la cuenta y su evento; después, la verificación con su plazo.
2. **Fuera del commit:** el cifrado y la verificación con la entidad, que llegan hechos.
3. **Clave de idempotencia:** el hash de la cuenta, único por usuario.
4. **Qué se bloquea:** `uq_cuenta_benef_hash` y `uq_cuenta_benef_principal`.
5. **Si el proceso muere tras el commit:** la cuenta existe; el reintento la devuelve.

### CU-22 · Liquidar y entregar
1. **Todo junto o nada:** la entrega, sus deducciones y el evento. Los totales los
   recalcula el trigger dentro de la misma transacción.
2. **Fuera del commit:** el movimiento de dinero, de `nucleo-financiero`.
3. **Clave de idempotencia:** el **turno**, único en la base.
4. **Qué se bloquea:** la fila de la entrega con `FOR UPDATE`, más versión optimista.
5. **Si el proceso muere tras el commit:** el estado dice en qué punto quedó.

### CU-28 · Emitir el desembolso
1. **Todo junto o nada:** la orden, su primer intento y el evento.
2. **Fuera del commit:** la llamada al proveedor. Quien orquesta la hace y trae el
   resultado (invariante 6).
3. **Clave de idempotencia:** `(entrega, clave)`, más «una orden viva por entrega».
4. **Qué se bloquea:** la fila de la orden con `FOR UPDATE`.
5. **Si el proceso muere tras el commit:** la orden quedó enviada; la respuesta la
   cierra cuando llegue.

## Piezas declaradas por nivel

| Pieza | Nivel | CU | Estado |
| --- | --- | :-: | :-: |
| `CuentaEnmascarada` | átomo | 18 | ✅ |
| `TitularidadDeCuenta` | átomo | 18 | ✅ |
| `VentanaDeEnfriamiento` | átomo | 18, 28 | ✅ |
| `LiquidacionDeEntrega` | átomo | 22 | ✅ |
| `ReintentoDeDesembolso` | átomo | 28 | ✅ |
| `CuentaDestinoRepositorio` | molécula | 18, 28 | ✅ |
| `EntregaRepositorio` | molécula | 22, 28 | ✅ |
| `DesembolsoRepositorio` | molécula | 28 | ✅ |
| `CU18`, `CU22`, `CU28` | organismos | 18, 22, 28 | ✅ |

## Micro-PR abiertos al troncal

| Rama | Qué agrega | Estado |
| --- | --- | :-: |
| — | Ninguno. Todo lo que hizo falta ya estaba | — |

## Bloqueos

Ninguno.

## Matriz de gates

| Área | Gate | Evidencia | Estado |
| --- | --- | --- | --- |
| Especificación | Criterios de aceptación cubiertos | `verificar_criterios.py --servicio entregas`: «3 verificados · Sin divergencias» | ✅ |
| Datos | Restricciones citadas con prueba de rechazo | 6+7+9 = 22 rechazos, uno por restricción citada | ✅ |
| Seguridad | El número de cuenta no aparece en claro | `CU18Test.criterio1` lo busca en toda la fila y en el evento | ✅ |
| Plazos | Vencimiento y aviso previo | `bloqueada_hasta` persistido al verificar | ✅ |
| Arquitectura | Piezas por nivel, sin saltos | tabla de arriba | ✅ |
| Operación | Health, readiness, trazas | pendiente: capa `web/` | ⬜ |
| Entrega | Pruebas | `integrationTest` en verde | ✅ |

## Gate de salida — evidencia

- [x] `./gradlew :servicios:entregas:integrationTest` — **BUILD SUCCESSFUL**
- [x] `python3 scripts/verificar_criterios.py --servicio entregas` — Sin divergencias
- [x] Cada criterio de aceptación con su `@Test` + `@DisplayName` nombrado igual
- [x] Cada `R-XXX-nn` citado con prueba de rechazo
- [ ] `./gradlew spotlessCheck check`

> Lo verificado es lo que tiene su comando pegado arriba. Lo que **no** está verificado:
> la capa HTTP, el arranque del servicio, el cifrado real del número de cuenta, y el
> motor de reintentos.

## Ver también

[[informe]] · [[07 Carriles de trabajo concurrente]] · [[carril-3A]] · [[carril-2B]]
