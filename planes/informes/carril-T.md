# Carril T · convergencia — informe de cierre

**Alcance:** la capa `web/` de los catorce servicios, el emisor de tokens, el
cableado de la plataforma, el perfil `todo` del compose y la prueba de arranque.
**Rama:** `dev`

Este carril no agrega funcionalidad: cierra lo que faltaba para que lo ya construido
**se pueda desplegar y usar**. Lo que sigue es lo que se hizo, lo que se encontro roto
y lo que queda declarado sin resolver.

---

## Lo que estaba roto y nadie habia visto

### 1 · Ningun servicio levantaba

Los catorce compilaban y sus casos de uso pasaban contra PostgreSQL real, pero
**ninguno arrancaba**. No lo veia nadie porque ninguna prueba iniciaba el contexto de
Spring: las pruebas de caso de uso cablean las piezas a mano, que es lo correcto para
probar un caso de uso y lo que hace que este hueco sea invisible.

Al escribir la primera `ArranqueTest` aparecieron cuatro fallas encadenadas:

| Que faltaba | Sintoma |
| --- | --- |
| `comun-datos` y `comun-mensajeria` sin registrar como autoconfiguracion | `Datos`, `Outbox`, `Consumidos`, `Reloj` e `Ids` no existian como beans |
| `TodoEndpointDecideSuAcceso` pedia el `RequestMappingHandlerMapping` sin nombrarlo | actuator registra el suyo: dos candidatos y el proceso caia con un mensaje que no hablaba de seguridad |
| Los casos de uso recibian su configuracion por constructor sin `@Value` | Spring no podia construir ninguno |
| No habia `JwtDecoder` | `aportaya.jwt.jwks-uri` estaba en los catorce `application.yml` y no lo leia nadie |

`ArranqueTest` queda en los catorce y corre en `integrationTest`. Levanta el contexto
entero, ejercita la guardia por omision sobre cada endpoint —si uno no declara
`@Permiso` ni `@Publico`, el arranque se cae— y comprueba que ninguna ruta se salio de
sus prefijos reservados.

### 2 · No habia emisor de tokens

ADR-024 define el JWT RS256 con sus reclamos y el JWKS de identidad, y los catorce
estaban configurados como servidores de recurso que lo validan. **Nadie lo emitia.**
Sin emisor, los 138 endpoints del sistema eran inalcanzables.

Ahora identidad firma con RS256, publica su clave publica en
`/.well-known/jwks.json` y CU-04 devuelve el token con los permisos **efectivos**
—calculados al emitir desde las asignaciones vigentes, no los del rol—. Si falta el
segundo factor no se emite token: uno emitido antes del factor es una sesion completa
obtenida con media credencial.

**La clave de firma.** Viene por configuracion en formato JWK. Si no viene, se genera
al arrancar y queda solo en memoria: sirve para desarrollo y para las pruebas de punta
a punta, y tiene el precio de que al reiniciar identidad los tokens vivos dejan de
validar. En cualquier entorno con datos reales se configura, porque una clave que
cambia sola no es una clave.

### 3 · La imagen de Docker nunca se habia construido

`docker build -f despliegue/Dockerfile --build-arg SERVICIO=erp .` fallaba por dos
cosas: la etapa de construccion no tenia `python3` —`comun-web:erroresCatalogo` genera
el catalogo `constraint_name -> R-XXX-nn` con un guion de Python— y `scripts/` y
`sql/` estaban excluidos del contexto por `.dockerignore`.

Corregido y **verificado de punta a punta**: la imagen se construye, el contenedor
arranca, se conecta a PostgreSQL por PgBouncer, sirve sus rutas y responde `401` a una
peticion sin token.

```
docker build -f despliegue/Dockerfile --build-arg SERVICIO=erp -t aportaya/erp:local .
docker compose -f despliegue/compose/base.yml -f despliegue/compose/servicios.yml \
  --profile todo up -d erp
docker inspect -f '{{.State.Health.Status}}' aportaya-erp        # healthy
wget -O - http://127.0.0.1:8080/actuator/health/readiness        # {"status":"UP"}
wget -S http://127.0.0.1:8080/erp/plantillas-de-asiento/…        # HTTP/1.1 401
```

---

## La capa web: 122 de 138 operaciones

Cada controlador implementa la interfaz que **genera su contrato** (ADR-020), asi que
el codigo no se puede apartar del contrato en silencio. Cada endpoint declara su
permiso del catalogo sembrado —comprobado uno por uno contra `sql/60_semillas`— y las
cuatro rutas publicas declaran por que lo son.

Dieciseis operaciones quedan sin implementar **a proposito**. Todas por la misma
razon de fondo: **el caso de uso necesita un hecho que pertenece a otro servicio, y el
contrato HTTP no lo lleva.** Inventarlo en la frontera moveria plata mal o dejaria una
regla escrita y muerta.

| Servicio | Operaciones | Que dato falta y de quien es |
| --- | --- | --- |
| grupos | las once | organizador habilitado, tarifario vigente, quien esta al dia, KYC del entrante, quorum del reglamento |
| nucleo-financiero | `solicitarRetiro` | el costo del retiro, que cotiza `tarifas` |
| | `solicitarCierreBilletera` | si hay obligaciones abiertas (`aportes`) o grupo activo (`grupos`) |
| | `transferirSaldo` | resolver un alias, que vive en `grupos.participante` |
| entregas | `registrarCuentaDestino` | el nombre y documento del titular de la billetera (`identidad`) |
| transparencia | `verificarSorteo` | el paquete publicado del sorteo (`grupos.sorteo_turno`) |

**grupos es el caso extremo y merece su parrafo.** Su contrato declara once
operaciones sin cuerpo ni esquema de respuesta: es un esbozo, no un contrato. Y
completarlo desde la boveda no alcanza, porque los `EntradaCU` del vault
—`{nombre, montoAporte, periodicidad, cupos}`— son la mitad de lo que sus casos de uso
piden. La otra mitad son hechos de otros cuatro servicios. **Antes de escribir ese
contrato hay que decidir quien los resuelve**: un orquestador delante de grupos, o cada
caso de uso llamando afuera antes de abrir su transaccion. Es una decision de
arquitectura, no de implementacion, y por eso no se tomo aca.

Los dieciseis se cierran con un micro-PR `[CONTRATO]` entre los carriles
involucrados, que es exactamente el mecanismo que el contrato de carril §7 define
para esto.

### Dos huecos menores, declarados

- **`R-BIL-09` no se puede exigir hoy.** El retiro necesita el segundo factor del
  titular, y ADR-024 fija los reclamos del JWT sin ninguno que diga si lo hubo. Se
  agrego el puerto `SegundoFactor` con un adaptador local que **deniega por omision**;
  `aportaya.mfa.exigido=false` lo apaga y solo tiene sentido en desarrollo.
- **CU-34 publica un tarifario sin su simulacion de impacto.** El contrato no trae
  `escenarioJson` ni `resultadoJson`; van vacios y esta anotado donde ocurre.

---

## Configuracion: donde quedaron los numeros

Cada valor que un caso de uso recibia por constructor se movio a `application.yml`
—donde se ve y se audita, que es lo que pide el invariante 10— con su fuente escrita al
lado. Las estructuras (plazos por severidad, documentos por nivel, escalas de riesgo,
cortes de confianza) quedaron en una configuracion por servicio.

**Ninguna cifra quedo dentro del codigo**: `testBarrido` lo comprueba, y de hecho
rechazo dos intentos durante este carril — los umbrales de desvio de perfil y las
escalas de reputacion terminaron en YAML por eso.

Los secretos (`SEGURIDAD_PIMIENTA`, `WEBHOOK_SECRETO`, `CERTIFICADOS_CLAVE_FIRMA`)
entran por variable de entorno sin valor por omision: **si falta, el proceso no
levanta**, que es lo correcto.

## Dos piezas que cuatro carriles duplicaban

- `CalendarioDelCatalogo` — los feriados de `catalogo.dia_no_habil`, que es esquema
  comun y de nadie en particular, asi que leerlo no cruza el invariante 11. Estaba a
  punto de escribirse cuatro veces.
- `TransaccionAparte` como bean, y `MensajeriaSimulada` como adaptador por omision:
  acepta y deja constancia sin salir a la red, y **no registra el destinatario en el
  log** — un telefono en una linea de log es un dato personal que se replica en cada
  agregador por el que pase.

---

## El perfil `todo` del compose

`scripts/generar_compose.py` genera `despliegue/compose/servicios.yml` con los catorce
servicios. Las variables que cada uno exige **no se escriben a mano**: salen de leer su
`application.yml` y buscar los `${...}`, asi que una clave nueva sin valor declarado
hace fallar el generador en vez de aparecer como un contenedor que no arranca.

```
python3 scripts/generar_compose.py
docker compose -f despliegue/compose/base.yml -f despliegue/compose/servicios.yml \
  --profile todo config          # valido, 19 servicios contando la infraestructura
```

---

## Lo que queda, y que hace falta para cada cosa

**No se hizo, y no se declara hecho.**

| Entregable de la Fase 17 | Que falta |
| --- | --- |
| 17.1 · los seis recorridos E2E | El compose `todo` ya levanta. Los recorridos necesitan un modulo propio (`settings.gradle.kts` solo descubre `servicios/`) y las catorce imagenes construidas. La primera imagen ya se verifico; construir las catorce es la siguiente corrida larga |
| 17.2 · rendimiento | Falta `scripts/generar_carga.py` y una corrida de k6 contra el compose. **Ningun numero medido todavia**: los objetivos de `planes/06` siguen siendo punto de partida, no compromiso |
| 17.3 · resiliencia | Las cuatro pruebas de caos exigen el stack completo arriba |
| 17.4 · respaldos | El ensayo de restauracion **no se ejecuto**. «Hay backup» no es una afirmacion valida sin una restauracion ejecutada, asi que no se afirma |
| 17.5 · seguridad | `verificar_seguridad.py` en TODO OK con dos avisos. Falta el informe con los doce controles y la correspondencia ISO |
| 17.6 · observabilidad | Trazas y metricas existen; falta verificarlas con un caso real de punta a punta |
| 17.7 · despliegue a `ensayo` | Necesita un entorno remoto que este parque no tiene |

**Y los tres casos de uso que el verificador lista como pendientes no lo estan:**
CU-05 vive en `cumplimiento` y CU-51 en `nucleo-financiero` —sus tablas estan en esos
esquemas, y sus fichas apuntan a otro servicio—, y CU-57 es **obsoleto por ADR-039**:
sus tablas ya no existen y el documento dice literalmente que no se implementa.

---

## Verificado por maquina

```
./gradlew verificar                                    BUILD SUCCESSFUL in 5m 01s
./gradlew integrationTest                              BUILD SUCCESSFUL
ArranqueTest en los catorce servicios                  los catorce levantan
docker build --build-arg SERVICIO=erp                  imagen construida
contenedor erp contra PostgreSQL real                  healthy · 401 sin token
python3 scripts/verificar_boveda.py                    TODO OK
python3 scripts/verificar_carriles.py                  TODO OK
python3 scripts/verificar_criterios.py                 Sin divergencias
python3 scripts/verificar_seguridad.py                 TODO OK · 2 avisos
python3 scripts/generar_compose.py                     14 servicios
```

## Lo que no verifica ninguna maquina

**¿Los nombres dicen lo que las cosas son?** `ArranqueTest` se llama asi porque eso es
lo que prueba: que el proceso arranca. `MapeoDe<Servicio>` es traduccion y nada mas —
si aparece un `if` sobre una regla del pasanaku ahi, esta mal ubicado.

**¿La frontera transaccional es la correcta?** La capa web no abre ninguna. Las dos
excepciones son deliberadas y estan escritas: CU-32 consulta al servicio fiscal
**antes** de entrar al caso de uso, porque una llamada de red dentro de la transaccion
es el invariante 6; y `ConsultarSaldo` abre transaccion aunque solo lea, porque sin
ella el `SET LOCAL` no aplica y la consulta devuelve las cuentas de todos.

**¿Que supuse que no estaba en la boveda?** Los valores de configuracion salieron de
lo que cada carril ya usaba en sus pruebas: no son numeros nuevos, son los mismos
movidos a donde se pueden auditar. Donde no habia ninguno —el emisor de CUF de las
notas de credito— se implemento la derivacion que la propia prueba documentaba.

**¿Que deje peor de como lo encontre?** `buildSrc` y `scripts/verificar_seguridad.py`
cambiaron: el generador de OpenAPI arma el paquete Java desde el nombre real del
servicio, el corredor de integracion incluye `Arranque*Test`, y la lista de rutas de
infraestructura abiertas suma el JWKS. Los tres son troncales y estan explicados donde
viven.
