---
tags:
  - seguridad
  - ciberseguridad
  - iso
  - estandar
titulo: "Seguridad de la aplicación — el estándar y su verificación"
fecha: 2026-08-19
alcance: cómo se escribe código seguro en AportaYa, qué lo hace cumplir y con qué comando se comprueba
---

# Seguridad de la aplicación

> ⛔ **Antes de implementar cualquier cosa, leé también
> [[Contrato de implementación para IA]].** Este documento agrega la capa que ese contrato
> no cubre: **cómo se escribe el código para que resista un ataque**, y **con qué se
> comprueba** que se escribió así.

> [!important] La única idea de este documento
> **Un control que nadie ejecuta no es un control: es una intención.** Cada regla de acá
> dice, en la misma fila, quién la hace cumplir y con qué comando se verifica. Si una regla
> no tiene verificación, es una brecha declarada (§7), no una regla que «se respeta».

## 0 · Qué es esto y qué no

| Documento | Responde |
| --- | --- |
| [[Cumplimiento]] | ¿El **modelo de datos** provee la evidencia que exigen ASFI, UIF, BCB, SIN e ISO? |
| [[Restricciones]] | ¿Qué hace **imposible** violar una regla, con su DDL? |
| **este documento** | ¿Cómo se escribe el **código** para que un atacante no entre, y cómo se comprueba? |
| [[Auditoria-Robustez]] | ¿Qué tan robusto es lo que ya hay, y qué falta? |

No es una política corporativa de seguridad de la información: eso es
`politica_interna` con `materia='SEGURIDAD_INFORMACION'`, lo aprueba el Directorio y vive
fuera del repositorio. Este documento es la bajada **técnica** de esa política al código.

## 1 · El principio: denegar por omisión, en las siete fronteras

Un sistema es seguro cuando **lo que no fue autorizado explícitamente, no pasa** — y eso
tiene que ser verdad en cada frontera, no en la que uno recuerde:

| # | Frontera | Qué significa denegar por omisión acá |
| :-: | --- | --- |
| 1 | **Red** | Ningún puerto expuesto salvo el de NGINX; los servicios hablan por red interna ([[ADR-025 Empaquetado y despliegue de los servicios]]) |
| 2 | **HTTP** | Guard global: todo endpoint exige autenticación salvo marca pública explícita (`autenticacion-jwt`) |
| 3 | **Autorización** | El permiso se verifica **contra el recurso concreto**, no contra el rol a secas (`roles-y-accesos`) |
| 4 | **Fila** | RLS forzada: sin `SET LOCAL` de contexto, la consulta no devuelve nada ([[ADR-021 Sesión, RLS y pooling]], `R-SEG-03`) |
| 5 | **Esquema** | Un servicio solo ve su esquema + `catalogo` en lectura; el `GRANT` es la frontera ([[ADR-017 Propiedad de datos por servicio]]) |
| 6 | **Entrada** | Lo que el contrato no declara, se rechaza: `strict()`, sin campos extra ([[ADR-020 Contratos OpenAPI primero]]) |
| 7 | **Salida** | Lo que no se declaró como público no se serializa; el error nunca filtra la causa interna (`errores-api`) |

> [!warning] La grieta típica
> Las siete se implementan y **una** se deja «para después porque es interna». Un servicio
> interno sin guard es un servicio expuesto en cuanto alguien alcance la red interna, que es
> exactamente lo que consigue la primera vulnerabilidad de cualquier otro componente.

## 2 · Contra qué se defiende — el modelo de amenazas

No se escribe código «seguro en general»: se escribe contra ataques concretos. Los seis que
importan en una billetera de pasanaku, con el control que los corta:

| # | Amenaza | Cómo se ve en la práctica | Control que la corta |
| :-: | --- | --- | --- |
| A1 | **Toma de cuenta** | SIM swap, reuso de contraseña, phishing del canal de recuperación | MFA obligatorio, TOTP para operadores, enfriamiento, rotación de refresco (`R-SEG-09`, `R-SEG-10`, `R-SEG-11`, [[CU-04 Autenticar con MFA y registrar dispositivo]]) |
| A2 | **Fraude interno** | Un operador que autoriza y ejecuta el mismo pago, o se amplía permisos | Segregación en la base (`R-SEG-04`, `R-SEG-07`), reautenticación por paso (`R-SEG-12`), bitácora con actor y motivo |
| A3 | **Exfiltración masiva** | Un rol legítimo descargando la base de clientes | [[registro_acceso_datos]] con justificación (`R-SEG-02`), exportación cifrada que caduca (`extraccion-de-datos`), alerta por volumen |
| A4 | **Manipulación de importes** | Recalcular la comisión en el cliente, redondear a favor, doble acreditación | Importes **nunca** desde el cliente, `BigDecimal` (`dinero-decimal`), partida doble con cuadre (`contabilidad-partida-doble`), `clave_idempotencia` UNIQUE |
| A5 | **Inyección y ejecución** | SQL armado con concatenación, deserialización de lo que llegó, plantilla con datos del usuario | jOOQ parametrizado (`datos-jooq`), sin deserialización polimórfica, sin `eval` ni expresiones dinámicas fuera del motor de reglas (`motor-de-reglas`) |
| A6 | **Repudio** | «Yo no hice esa operación» sin nada con qué responder | [[bitacora_evento]] encadenada por hash, [[intento_autenticacion]], [[sesion]] con IP y dispositivo, append-only con `REVOKE` |

Y una que no es ataque pero cuesta igual: **A7 · caída**. Se trata con disponibilidad y
continuidad ([[ADR-037 Alta disponibilidad y balanceo]], [[ADR-013 Respaldo y continuidad]],
[[CU-56 Ejecutar una prueba de continuidad]]).

## 3 · Los controles, capa por capa

Cada fila dice **la regla**, **quién la hace cumplir** y **cómo se verifica**. La columna
«se verifica» distingue tres cosas, y la distinción importa:

- **motor** — la base de datos lo impide; no hay código que lo saltee.
- **gate** — un comando del CI falla si no se cumple.
- **revisión** — lo juzga una persona; es lo más débil y por eso la lista es corta.

### 3.1 · Borde HTTP

| Regla | Hace cumplir | Se verifica |
| --- | --- | --- |
| Guard global; endpoint público solo con marca explícita | Framework | gate (`verificar_seguridad.py`: ninguna marca pública fuera de la lista declarada) |
| `401` ≠ `403`; el error de login no distingue usuario de credencial | Aplicación | gate (los códigos del CU) + revisión |
| Cabeceras: `Content-Security-Policy`, `Strict-Transport-Security`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` | NGINX + servicio | gate (prueba de humo HTTP) |
| CORS por lista blanca; nunca `*` con credenciales | NGINX | gate |
| Backoffice: cookie `httpOnly` + `Secure` + `SameSite` estricto + CSRF | Servicio | revisión + prueba |
| App: bearer en almacenamiento seguro del dispositivo, nunca en `localStorage` de una vista web | App | revisión (`movil-expo`) |
| Tope de tamaño de cuerpo y de profundidad de JSON | Framework | gate |
| Rate limit por usuario **y** por origen en login, recuperación, MFA y dinero | Borde | gate + prueba |
| Ningún token en URL, en log ni en mensaje de error | Aplicación | gate (patrón prohibido) |

### 3.2 · Entrada, contrato y validación

| Regla | Hace cumplir | Se verifica |
| --- | --- | --- |
| El contrato se escribe **antes**; la entrada es `strict()` y rechaza campos extra | OpenAPI generado | gate (`contratos-api`) |
| Lista blanca, nunca lista negra: se enumera lo válido | Aplicación | revisión |
| Identificadores del cliente **nunca** determinan propiedad; se resuelve contra el recurso | Aplicación + RLS | motor (`R-SEG-03`) + revisión |
| Sin deserialización polimórfica ni `readObject` de datos externos | Configuración | gate (patrón prohibido) |
| Subida de archivos: tipo verificado por contenido, tamaño acotado, nombre generado, **fuera** de la raíz servida | Adaptador de archivos | gate + revisión ([[ADR-034 Almacenamiento de archivos]]) |
| Toda salida a un tercero pasa por un **puerto**; sin URL construida con datos del usuario (SSRF) | Puerto + adaptador | gate ([[ADR-033 Puertos y adaptadores]]) |
| Importes: `BigDecimal`, nunca `float`, nunca recalculados en el cliente | Tipo `Dinero` | gate (`dinero-decimal`) |

### 3.3 · Datos

| Regla | Hace cumplir | Se verifica |
| --- | --- | --- |
| RLS **forzada** en toda tabla con datos de titular | PostgreSQL | motor (`R-SEG-03`) + prueba de humo |
| Toda consulta corre dentro de `conContexto`; sin contexto, no hay filas | Aplicación + RLS | gate + prueba |
| Consultas parametrizadas; jOOQ, **JPA prohibido**, concatenación prohibida | jOOQ | gate (patrón prohibido) + [[ADR-016 Acceso a datos con jOOQ]] |
| Un servicio no lee el esquema ajeno | `GRANT` | motor + gate (`verificar_boveda.py`) |
| Lo financiero es append-only con `REVOKE UPDATE, DELETE` | PostgreSQL | motor + prueba de humo |
| El lector privilegiado (`BYPASSRLS`) deja huella obligatoria | Trigger | motor (`R-SEG-02`) · [[ADR-031 Lecturas, réplica y rol auditor]] |
| Borrar es baja lógica, salvo lo financiero que se reversa | Aplicación | revisión |

### 3.4 · Criptografía y secretos

| Regla | Hace cumplir | Se verifica |
| --- | --- | --- |
| Contraseñas y PIN con **Argon2id**; nunca un hash rápido | Aplicación | gate (patrón prohibido: `MD5`, `SHA1`, `SHA-256` como hash de contraseña) |
| Hash de búsqueda = **HMAC con pimienta** fuera de la base; nunca `digest()` desnudo | Función de base | motor (`R-SEG-01`) |
| Toda columna cifrada declara su **versión de llave** | `CHECK` | motor (`R-SEG-01b`) |
| Aleatoriedad criptográfica para tokens, códigos y sales | Aplicación | gate (patrón prohibido: `Math.random`, `java.util.Random`) |
| TLS en todo tramo externo; interno cifrado o red confinada | Despliegue | revisión |
| **Ningún secreto en el repositorio**: ni en código, ni en `.yml`, ni en semillas, ni en pruebas | — | gate (barrido de secretos) |
| Los secretos llegan por variable de entorno o gestor; nunca por columna de base | Despliegue | gate + revisión (`proveedores-externos`) |
| Rotación de llaves con `kid`, sin tumbar sesiones vivas | Aplicación | revisión |

> [!danger] Lo que hace inútil al cifrado
> Cifrar la columna y poner al lado un `digest()` del mismo dato «para poder buscar» es
> exactamente equivalente a no cifrar: un CI boliviano son ~10⁷ valores y la tabla de
> digests se precalcula en segundos. Por eso `R-SEG-01` **obliga** el HMAC con pimienta y
> falla si la pimienta no está configurada.

### 3.5 · Registro, trazabilidad y respuesta

| Regla | Hace cumplir | Se verifica |
| --- | --- | --- |
| Todo lo que pasó tiene actor, hora y hash encadenado | [[bitacora_evento]] | motor + prueba de humo |
| El log **no** lleva PII, credenciales, tokens ni importes de terceros | Aplicación | gate (patrón prohibido) |
| Toda traza lleva `correlation_id`; una operación se sigue de punta a punta | Aplicación | gate (`observabilidad`) |
| El error que ve el usuario **nunca** trae la causa interna, el SQL ni el stack | Traductor de errores | gate (patrón prohibido: `printStackTrace`, traza en respuesta) |
| Un incidente de seguridad guarda su **plazo de reporte** el día del hecho | `CHECK` | motor (`R-SEG-05`) · [[CU-55 Gestionar un incidente de seguridad]] |
| Lectura de datos sensibles con justificación escrita | Trigger | motor (`R-SEG-02`) |

### 3.6 · Dependencias y cadena de suministro

| Regla | Hace cumplir | Se verifica |
| --- | --- | --- |
| Versiones fijadas; nada de rangos abiertos en producción | Gradle / yarn | gate |
| Escaneo de vulnerabilidades en cada PR; una crítica **bloquea** | CI | gate (`ci-calidad`) |
| Una dependencia nueva se justifica en un **ADR** con su matriz | Revisión | gate (`verificar_boveda.py` sobre los ADR) + `decisiones-adr` |
| Lo generado no se edita a mano y no puede divergir de su fuente | CI | gate |
| Sin `curl \| sh` ni descargas no verificadas en ningún script de construcción | Revisión | gate (patrón prohibido) |

### 3.7 · Contenedor y despliegue

| Regla | Hace cumplir | Se verifica |
| --- | --- | --- |
| Imagen multietapa, **sin root**, sin herramientas de más | Dockerfile | gate (`despliegue-contenedores`) |
| Ningún servicio publica puerto al exterior; NGINX es la única entrada | Compose / K8s | gate (`generar_k8s.py`) |
| Nunca una réplica; sondas y límites declarados | Generador | gate ([[ADR-037 Alta disponibilidad y balanceo]]) |
| Secretos por `Secret`/entorno, jamás en el manifiesto versionado | Generador | gate (barrido de secretos) |
| Las semillas de desarrollo **no entran** a una base sin marcar | Guarda SQL | gate (el CI comprueba que la guarda **falla**) |

### 3.8 · Frontend

| Regla | Hace cumplir | Se verifica |
| --- | --- | --- |
| Backoffice detrás de login y `noindex` | Ruta | revisión |
| Nada de `dangerouslySetInnerHTML` ni HTML de servidor interpolado | Revisión | gate (patrón prohibido) |
| La UI **refleja** permisos, no los decide; esconder el botón no es un control | Guard de API | motor + revisión |
| Ningún secreto ni clave de proveedor en el paquete del cliente | Construcción | gate (barrido de secretos) |
| Los cuatro estados en toda pantalla con datos (evita el «error mudo» que esconde un fallo) | Revisión | revisión (`disenar-frontend`) |

## 4 · Correspondencia con las normas ISO

> [!note] Qué significa «cumple» acá
> Que **el repositorio** provee el control técnico y su verificación. La certificación
> agrega políticas, personas y auditoría externa: eso se contrasta en [[Cumplimiento]] §6.

### 4.1 · ISO/IEC 27001:2022 — controles del Anexo A que caen en el código

| Control | Dónde vive en este repositorio | Cómo se verifica |
| --- | --- | --- |
| **A.5.15** Control de acceso | §1 fronteras 2–5, `roles-y-accesos` | gate + motor (`R-SEG-03`) |
| **A.5.16** Gestión de identidades | [[CU-01 Registro y apertura de billetera]], [[CU-08 Asignar y revocar roles de operador]] | motor (`R-SEG-07`, `R-SEG-08`) |
| **A.5.17** Información de autenticación | §3.4, [[CU-04 Autenticar con MFA y registrar dispositivo]], [[CU-09 Cambiar credenciales y solicitar la baja]] | motor (`R-SEG-10`, `R-SEG-11`) |
| **A.5.18** Derechos de acceso | [[asignacion_rol]] con vigencia y revocación | motor (`R-SEG-08`) |
| **A.5.23** Seguridad en servicios de nube | [[ADR-025 Empaquetado y despliegue de los servicios]], [[ADR-037 Alta disponibilidad y balanceo]] | gate (`generar_k8s.py`) |
| **A.5.24–A.5.28** Incidentes y evidencia | [[CU-55 Gestionar un incidente de seguridad]], [[bitacora_evento]] | motor (`R-SEG-05`) |
| **A.5.29–A.5.30** Continuidad TIC | [[ADR-013 Respaldo y continuidad]], [[CU-56 Ejecutar una prueba de continuidad]] | revisión + ensayo |
| **A.5.33** Protección de registros | Append-only + `REVOKE` + cadena de hash | motor + prueba de humo |
| **A.8.2** Acceso privilegiado | [[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] | motor (`R-SEG-10`, `R-SEG-12`) |
| **A.8.3** Restricción de acceso a la información | RLS forzada | motor (`R-SEG-03`) |
| **A.8.5** Autenticación segura | MFA obligatorio para operadores, TOTP | motor (`R-SEG-10`) |
| **A.8.8** Gestión de vulnerabilidades técnicas | §3.6 escaneo en cada PR | gate |
| **A.8.9** Gestión de configuración | Manifiestos generados, no editados | gate |
| **A.8.12** Prevención de fuga de datos | [[registro_acceso_datos]] + exportación que caduca | motor + gate |
| **A.8.15** Registro de eventos | §3.5 | motor + gate |
| **A.8.16** Monitoreo | `observabilidad`, alertas | revisión |
| **A.8.24** Uso de criptografía | §3.4 | motor (`R-SEG-01`, `R-SEG-01b`) + gate |
| **A.8.25** Ciclo de vida de desarrollo seguro | [[Procedimiento de desarrollo]] + este documento | gate |
| **A.8.26** Requisitos de seguridad de la aplicación | §3 completo | gate |
| **A.8.27** Arquitectura y principios de ingeniería segura | §1 denegar por omisión, [[ADR-033 Puertos y adaptadores]] | revisión + gate |
| **A.8.28** Codificación segura | `seguridad-aplicacion` + §5 prohibiciones | gate (patrones prohibidos) |
| **A.8.29** Pruebas de seguridad en desarrollo | Prueba de rechazo por restricción (`pruebas-cu`) | gate |
| **A.8.31** Separación de entornos | `seeders/dev` con guarda; el CI comprueba que **bloquea** | gate |
| **A.8.32** Gestión de cambios | `git-flujo`, ADR, generadores | gate |

### 4.2 · Las otras normas que aplican al código

| Norma | Qué exige | Dónde se cumple |
| --- | --- | --- |
| **ISO/IEC 27002:2022** | La guía de implementación de los controles de 27001 | Es el contenido de §3: cada fila es la bajada concreta de un control |
| **ISO/IEC 27034** *Seguridad de aplicaciones* | Controles de seguridad de aplicación **verificables**, con nivel de confianza declarado | §3 (los controles) + §6 (los gates que los miden). El «nivel de confianza» de cada control es su columna: **motor** > **gate** > **revisión** |
| **ISO/IEC 27005** *Gestión del riesgo* | Identificar, analizar y tratar riesgos | §2 modelo de amenazas + [[evento_riesgo_operativo]] + `alertas-riesgo-temprano` |
| **ISO/IEC 27017** *Nube* | Responsabilidad compartida, aislamiento, administración segura | [[ADR-025 Empaquetado y despliegue de los servicios]], §3.7 |
| **ISO/IEC 27018** *PII en la nube* | Tratamiento de datos personales por el encargado | [[Cumplimiento]] §5 + §3.3 + [[CU-07 Ejercer derechos sobre datos personales]] |
| **ISO/IEC 27701** *Privacidad* | Derechos del titular, minimización | [[CU-07 Ejercer derechos sobre datos personales]], [[proceso_anonimizacion]] |
| **ISO 22301** *Continuidad* | RTO/RPO probados | [[plan_continuidad]], [[prueba_continuidad]] |
| **ISO 31000** *Riesgo* | Marco y tratamiento | [[control_interno]], [[plan_accion_riesgo]] |
| **OWASP ASVS** | La lista técnica que instancia 27034 | §3 y §5 están escritos contra ella; no se cita como norma sino como checklist |
| **PCI DSS** | No almacenar el PAN | [[instrumento_fondeo]] con `token_proveedor` y `hash_identificador` |

## 5 · Prohibiciones absolutas — se rechaza en revisión, sin discusión

Estas no se discuten en un PR. Si aparecen, el cambio vuelve:

1. **Un secreto en el repositorio.** Clave, token, certificado, cadena de conexión con
   contraseña, credencial de proveedor. Ni en pruebas, ni «temporal», ni comentado.
2. **SQL armado con concatenación de texto.** Aunque el dato «venga de adentro».
3. **Un endpoint sin decisión consciente de autenticación.** El guard es global; marcar algo
   público es un acto explícito y revisable.
4. **Un permiso verificado solo en el controlador**, sin comprobar el recurso concreto.
5. **Una consulta fuera de `conContexto`.** Es un defecto de seguridad, no de estilo.
6. **`Math.random()` o `java.util.Random`** para un token, un código, una sal o un
   identificador que alguien no deba adivinar.
7. **Hash rápido para una contraseña** (MD5, SHA-1, SHA-256 desnudo). Argon2id o nada.
8. **`digest()` desnudo como hash de búsqueda** sobre un dato adivinable.
9. **PII, credenciales o tokens en un log**, completos o truncados de forma reversible.
10. **Una traza, un SQL o un mensaje interno en la respuesta HTTP.**
11. **Deserializar datos externos con tipo polimórfico.**
12. **Construir una URL de salida con datos del usuario** sin lista blanca (SSRF).
13. **`dangerouslySetInnerHTML`** o equivalente con contenido que no sea constante.
14. **Confiar en que la interfaz esconde el botón.**
15. **Desactivar una restricción, un trigger o una política de RLS para que pase una prueba.**
    Si la prueba choca contra el motor, la prueba está mal o el diseño está mal.
16. **Bajar un gate del CI para desbloquear un merge.** El gate se arregla o se discute; no
    se apaga.
17. **Inventar un código de permiso.** Si un caso de uso exige uno que el catálogo no tiene,
    es un hueco (S-8): se declara y se para. Colgarlo de un permiso más amplio «mientras
    tanto» rompe el mínimo privilegio sin dejar rastro de que se rompió.
18. **Encender un canal apagado para destrabar un flujo.** SMS y WhatsApp están apagados por
    decisión ([[ADR-035 Canales por defecto]]); encenderlos para que salga un token reabre
    el vector que `R-SEG-10` cierra. Se contrata el canal con evaluación de tercero, o el
    flujo espera.

## 6 · Cómo se verifica — los gates, en orden

```bash
python3 scripts/verificar_seguridad.py     # el estándar de este documento, sobre el repositorio
python3 scripts/verificar_boveda.py        # coherencia de bóveda, CU, ADR y restricciones
python3 scripts/verificar_carriles.py      # skills asignadas y carriles alineados
python3 scripts/generar_ddl.py             # lo derivado no diverge de su fuente
python3 scripts/generar_k8s.py             # ninguna réplica única, ningún puerto de más
psql -f sql/aplicar.sql                    # el esquema y las 140 restricciones aplican en limpio
psql -f sql/50_verificacion/prueba_humo.sql  # cero FALLA
```

Y lo que **no** verifica ningún comando: que la frontera transaccional sea la correcta y que
el nombre diga lo que la cosa es. Eso lo mira una persona (`revision-codigo`).

> [!important] La regla que sostiene todo lo anterior
> **No se afirma «es seguro», «compila» o «pasa» sin haberlo ejecutado**
> (`definicion-de-terminado`). Una tabla de controles con todos los casilleros marcados y
> ningún comando corrido es peor que no tener la tabla: da confianza sin sustento.

## 7 · Lo que este documento NO da, y hay que decirlo

| # | Brecha | Tipo | Qué falta |
| :-: | --- | --- | --- |
| S-1 | **Pruebas de intrusión** y ejercicio de equipo rojo | Operativa | Contratar y calendarizar; el resultado entra como [[hallazgo_auditoria]] o [[incidente_seguridad]] |
| S-2 | **Gestión de llaves** (KMS/HSM) y cifrado en reposo | Infraestructura | El modelo marca qué columnas lo exigen (`*_cifrado`, `version_llave`); la implementación es de plataforma |
| S-3 | **DLP** y análisis de comportamiento | Herramienta | Detectable por volumen sobre [[registro_acceso_datos]]; la herramienta es externa |
| S-4 | **WAF** y protección de denegación de servicio | Infraestructura | Delante de NGINX; fuera del repositorio |
| S-5 | **Inteligencia de amenazas** (A.5.7) | Proceso | Suscripción y revisión periódica; solo el resultado entra al modelo |
| S-6 | **Firma de artefactos y SBOM** | Cadena de suministro | Falta generar y firmar el SBOM por imagen |
| S-7 | Aprobador de restablecimiento sin columna donde vivir | Modelo | Declarado en [[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]; hoy la sostiene la aplicación |
| S-8 | **El catálogo de permisos está incompleto respecto de la especificación** | Catálogo | 24 códigos que los CU exigen en su columna «Exige» no existen en `seeders/minimos/10-roles-y-permisos.json` (`COBRANZA_GESTIONAR`, `ORGANIZADORES_SANCIONAR`, `TESORERIA_OPERAR`…). Quien implemente esos flujos **inventará el permiso o lo colgará de uno más amplio**, que es la forma silenciosa de romper el mínimo privilegio. Cada uno necesita `recurso`, `accion`, `requiere_mfa` y su rol: es una decisión de seguridad, no de implementación. `verificar_seguridad.py` los lista en cada corrida |
| S-9 | **Tres propósitos de token sin canal entregable** | Catálogo | Con los adaptadores por defecto ([[ADR-035 Canales por defecto]]), `VERIFICACION_TELEFONO` (`SMS,WHATSAPP,LLAMADA_VOZ`), `CAMBIO_CORREO` (`SMS,WHATSAPP`) e `INICIO_SESION_SIN_CONTRASENA` (`SMS,WHATSAPP`) no tienen **ningún** canal activo. El riesgo no es que el flujo se trabe: es que alguien encienda SMS «temporalmente» y reabra el intercambio de SIM que `R-SEG-10` cerró. Se decide con seguridad de la información: o se contrata el canal con evaluación de tercero, o el propósito cambia de canal, o el flujo se declara no disponible. `SEGUNDO_FACTOR` **no** está en la lista: `APP_AUTENTICADORA` no depende de ningún proveedor, que es exactamente por qué es el factor del operador |

Las brechas S-1 a S-5 corresponden a B-5 y B-6 de [[Cumplimiento]] §8. S-7 a S-9 son de
este repositorio y salieron de la revisión de endurecimiento del 2026-08-20. Estar
declaradas es lo que las distingue de un descuido: ninguna es «se nos pasó», todas son
«sabemos que falta y sabemos quién lo decide».

## 8 · Cómo se mantiene vivo

1. **Una amenaza nueva** entra en §2 con el control que la corta, o no entra.
2. **Un control nuevo** nace con su columna «se verifica» llena. Si no se puede verificar
   hoy, se escribe igual y se agrega a §7 como brecha, nunca como control cumplido.
3. **Cada restricción `R-SEG-*` nueva** se agrega a [[Restricciones]] con su DDL y su prueba
   de rechazo, y se cita desde el CU que la obliga.
4. **Revisión con cada norma nueva** (`norma-nueva`) y en cada revisión anual de
   `politica_interna` con `materia='SEGURIDAD_INFORMACION'`.

## Ver también

[[Cumplimiento]] · [[Restricciones]] · [[Contrato de implementación para IA]] ·
[[Procedimiento de desarrollo]] · [[Auditoria-Robustez]] ·
[[ADR-024 Autenticación y sesión distribuida]] ·
[[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]] ·
[[CU-04 Autenticar con MFA y registrar dispositivo]] ·
[[CU-08 Asignar y revocar roles de operador]] ·
[[CU-09 Cambiar credenciales y solicitar la baja]] ·
[[CU-55 Gestionar un incidente de seguridad]] ·
`seguridad-aplicacion` · `autenticacion-jwt` · `seguridad-sesion-rls` · `roles-y-accesos` ·
`ci-calidad` · `revision-codigo` · `definicion-de-terminado`
