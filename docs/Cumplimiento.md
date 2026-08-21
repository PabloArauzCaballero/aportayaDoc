---
tags:
  - moc
  - cumplimiento
titulo: "Cumplimiento normativo y estándares — AportaYa"
fecha_revision: 2026-08-11
entidades_modelo: 273
modulos: 12
---

# Cumplimiento normativo y estándares

> **Qué es este documento.** El contraste, requisito por requisito, entre el modelo
> de datos de este repositorio y la normativa boliviana aplicable a una billetera
> móvil que cobra comisión, más los estándares internacionales de auditoría y
> buenas prácticas. Para cada requisito se indica **la fuente**, **qué tabla o
> columna lo soporta** y **el estado real**, sin maquillar lo que falta.

---

## 0. Alcance y advertencias (léase antes que nada)

> [!warning] Lo que un modelo de datos puede y no puede cumplir
> Un esquema de base de datos **no obtiene licencias, no aprueba políticas y no
> ejecuta controles**. Lo que sí hace —y es la mitad del trabajo de cumplir— es
> garantizar que cada obligación tenga **dónde registrarse, con qué trazabilidad y
> con qué evidencia**. Este documento clasifica cada requisito en:
>
> | Estado | Significado |
> | --- | --- |
> | ✅ **Cubierto** | El modelo tiene la estructura completa para registrar y evidenciar el requisito |
> | 🟡 **Parcial** | El modelo lo soporta, pero depende de configuración, seeder o de un proceso externo |
> | 🔵 **Fuera del modelo** | Es un requisito de licencia, capital, proceso u organización: el modelo no puede cubrirlo |
> | ❌ **No cubierto** | Falta estructura. Si aparece acá, es una brecha abierta |

> [!important] Sobre las cifras y los artículos citados
> La normativa boliviana citada fue verificada en agosto de 2026 contra fuentes
> públicas (ver §9). **Aun así, ninguna cifra regulatoria está cableada en el
> modelo**: umbrales, plazos, límites y alícuotas viven en tablas con vigencia
> (`umbral_reporte_uif`, `limite_operativo_billetera`, `catalogo_reporte_regulatorio`,
> `impuesto`) precisamente porque cambian. Antes de sembrarlas en producción, **el
> área legal debe confirmar cada valor y llenar las columnas `base_normativa` /
> `fuente_normativa` / `base_legal`** con la cita exacta. Este documento no es
> asesoramiento legal.

> [!caution] La brecha más grande no es de datos
> Este producto, tal como está diseñado (custodia de saldo + cobro de comisión),
> **requiere autorización de ASFI antes de operar**. Ver §1.1 y §8. Ningún modelo de
> datos sustituye eso.

---

## 1. ASFI — Autorización y régimen aplicable

### 1.1 Reglamento para Empresas de Tecnología Financiera (Res. ASFI/540/2025)

ASFI aprobó mediante **Resolución ASFI/540/2025 de 3 de julio de 2025** el
Reglamento para Empresas de Tecnología Financiera (ETF), vigente desde el
**15 de julio de 2025**, con adecuación obligatoria de las empresas ya operativas
hasta el **31 de diciembre de 2025** —vencido ese plazo sin adecuarse, deben
suspender la prestación de servicios financieros al consumidor.

El reglamento define cinco categorías, y este producto cae de lleno en la segunda:
**pagos y plataformas de pago**.

| Requisito | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Certificado de adecuación y luego licencia de funcionamiento | 🟡 | [[licencia_regulatoria]] (`tipo`, `estado`, `numero_resolucion`, `fecha_otorgamiento`) — el trámite es externo; el modelo registra su estado y lo hace consultable |
| Operar solo dentro del alcance autorizado | ✅ | `licencia_regulatoria.alcance_autorizado` (JSONB) — la aplicación consulta antes de habilitar un servicio |
| Garantía de seriedad en la solicitud | 🟡 | `licencia_regulatoria.garantia_seriedad` |
| Entorno Controlado de Pruebas (sandbox) con límites y garantía | ✅ | [[entorno_prueba_regulado]] (`limite_usuarios`, `limite_monto_operacion`, `garantia_constituida`, `informes_remitidos`) |
| Gobierno corporativo y aprobación de metodologías | ✅ | [[comite_gobierno]], [[acta_comite]], [[politica_interna]] (`aprobada_por_directorio`) |
| Gestión de riesgos | ✅ | [[evento_riesgo_operativo]], [[control_interno]], [[prueba_control]], [[plan_accion_riesgo]] |
| Seguridad de la información y ciberseguridad | ✅ | [[activo_informacion]], [[incidente_seguridad]] + M1 completo (sesiones, MFA, dispositivos) |
| Protección de datos personales | 🟡 | `activo_informacion.contiene_datos_personales`, [[consentimiento]], [[solicitud_datos_personales]], [[proceso_anonimizacion]], [[registro_acceso_datos]] |
| Protección del consumidor y atención de reclamos | ✅ | [[reclamo_cliente]], [[punto_reclamo]], [[instancia_reclamo]] |
| Prevención de legitimación de ganancias ilícitas | ✅ | Todo el circuito de §2 |
| Capital mínimo y constitución societaria | 🔵 | Requisito societario: fuera del modelo |

> [!note] Conclusión de §1.1
> El modelo está construido **para una entidad autorizada**. Si el proyecto sale a
> producción sin certificado de adecuación / licencia, ningún dato del esquema lo
> salva: es actividad financiera no autorizada. La tabla `licencia_regulatoria`
> existe justamente para que ese estado sea visible en el sistema y no un supuesto.

### 1.2 Régimen de billetera móvil y dinero electrónico

Las billeteras móviles las emiten **Empresas de Servicios de Pago Móvil (ESPM)** o
entidades de intermediación financiera autorizadas por ASFI, y deben cumplir los
requisitos operativos mínimos de seguridad establecidos por el **BCB**. Existen
límites regulatorios por operación y por saldo del titular.

| Requisito | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Límite de monto por operación | ✅ | [[limite_operativo_billetera]] con `concepto`, `ventana='OPERACION'`, `monto_maximo` |
| Límite de saldo del titular | ✅ | `limite_operativo_billetera` con `concepto='SALDO_MAXIMO'` |
| Límites acumulados por ventana (día / 5 días / mes) | ✅ | `limite_operativo_billetera.ventana` + [[consumo_limite]] (`ventana_inicio`, `monto_acumulado`) |
| Límites escalonados por nivel de conocimiento del cliente | ✅ | `limite_operativo_billetera.nivel_debida_diligencia`, gobernado por [[calificacion_riesgo_cliente]] |
| Respaldo de los fondos de los clientes | ✅ | [[cuenta_custodia]], [[movimiento_custodia]], [[conciliacion_custodia]] (`ratio_cobertura`, `cumple_encaje`), [[descuadre_custodia]] |
| Separación patrimonial: el saldo es pasivo exigible, no patrimonio | ✅ | Cuentas de tipo `PLATAFORMA_*` separadas de `USUARIO`/`GRUPO` en [[cuenta_billetera]] + espejo en [[cuenta_contable]] |
| Cifras exactas de los límites vigentes | 🟡 | **Deben cargarse por seeder y confirmarse con legal.** El modelo no las cablea |

> [!warning] Valores de límite: confirmar antes de sembrar
> Fuentes secundarias reportan, para dinero electrónico, límites del orden de
> **USD 350 por transacción y USD 700 de saldo por titular**, un tope por operación
> equivalente a **2,5 salarios mínimos**, y un acumulado de cinco días de
> **USD 5.000** para depósitos en efectivo y transferencias enviadas. **No tome
> estas cifras como vigentes sin verificarlas** contra el reglamento del BCB y la
> RNSF actualizados. Por eso son filas de `limite_operativo_billetera` con
> `vigente_desde`, `base_normativa` y sin borrado.

### 1.3 Protección del consumidor financiero (RNSF, Libro 4, Título I)

Este es el capítulo con requisitos más literales y donde el modelo se ajustó al
detalle.

| Requisito normativo | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Constituir formalmente un **Punto de Reclamo (PR)** en la estructura organizacional, en todas las oficinas donde se atiende al público | ✅ | [[punto_reclamo]] (`tipo` incluye APP/WEB/TELEFONO/PRESENCIAL/CORREO, `responsable_id`, `horario`, `activo`) |
| Identificar el PR con la señalización exigida | 🔵 | Requisito físico/visual: fuera del modelo |
| Registrar el reclamo con **número único y correlativo** y comunicarlo al cliente | ✅ | `reclamo_cliente.codigo` UNIQUE, `fecha_ingreso`, `canal_ingreso` |
| Responder en **máximo 5 días hábiles administrativos** | ✅ | `reclamo_cliente.dias_habiles_plazo` + `plazo_respuesta` (se calcula al ingresar y **se guarda**) |
| Si requiere más análisis: comunicar dentro de los 5 días la fecha de respuesta, **hasta 10 días** | ✅ | `plazo_prorrogado_hasta`, `prorroga_comunicada_al_cliente_en`, `justificacion_prorroga` |
| Plazos mayores a 10 días: **comunicación escrita a ASFI y al cliente** justificando | ✅ | `prorroga_comunicada_al_organismo_en` + `justificacion_prorroga` |
| Respuestas oportunas, íntegras y comprensibles | 🟡 | `reclamo_cliente.respuesta` (TEXT) — la calidad es un control de proceso, no de esquema |
| Segunda instancia ante la Central de Reclamos / defensoría | ✅ | [[instancia_reclamo]] (`instancia`, `numero_expediente`, `resolucion`, `monto_resarcido`) |
| **Reporte mensual** de reclamos a ASFI y reporte anual | ✅ | `reclamo_cliente.incluido_en_reporte_mensual` + [[catalogo_reporte_regulatorio]] / [[reporte_regulatorio]] / [[envio_regulatorio]] |
| Conservar la documentación de cada reclamo **no menos de 10 años** | ✅ | `reclamo_cliente.conservar_hasta` (fecha guardada por reclamo, no regla global) |
| Transparencia: tarifario publicado y accesible | ✅ | [[documento_publicado]] (`tipo='TARIFARIO'`, `hash_documento`, vigencias) + [[tarifario]] (`url_publicacion`, `publicado_en`) |
| Aviso previo de modificación de condiciones y tarifas | ✅ | [[cambio_tarifario]] (`dias_preaviso`, `fecha_aviso`, `canal_aviso`, `usuarios_notificados`, `permite_rescision_sin_costo`) |
| Contratos de adhesión y evidencia de aceptación | ✅ | [[contrato_adhesion]] (`registrado_ante_regulador`, `numero_registro`) + [[aceptacion_contrato]] (IP, dispositivo, token de firma, hash) |
| Estados de cuenta / extractos al titular | ✅ | [[estado_cuenta_billetera]] (`url_archivo`, `hash_archivo`, `emitido_en`, `entregado_en`) |

**Consulta de evidencia — reclamos vencidos hoy:**

```sql
SELECT codigo, usuario_id, categoria, fecha_ingreso,
       COALESCE(plazo_prorrogado_hasta, plazo_respuesta) AS vence,
       now() - COALESCE(plazo_prorrogado_hasta, plazo_respuesta) AS atraso
FROM   reclamo_cliente
WHERE  estado IN ('INGRESADO','EN_ANALISIS')
  AND  COALESCE(plazo_prorrogado_hasta, plazo_respuesta) < now()
ORDER  BY atraso DESC;
```

### 1.4 Gestión de riesgo operativo (RNSF, Libro 3, Título V)

El ciclo exigido tiene seis etapas —identificación, medición, monitoreo, control,
mitigación y divulgación— y una base de datos de eventos con reporte a la **Central
de Información de Riesgo Operativo (CIRO)**.

| Requisito | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Base de datos de eventos de riesgo operativo y pérdidas | ✅ | [[evento_riesgo_operativo]] *append-only* |
| **Tipo de evento** (fraude interno, fraude externo, relaciones laborales, clientes/productos/prácticas, daños a activos, fallas en sistemas) | ✅ | `categoria_evento` con los seis valores del CHECK |
| **Factor de riesgo** originante (procesos internos, personas, TI, eventos externos, infraestructura) | ✅ | `factor_riesgo` con los cinco factores |
| Monto de pérdida, recuperaciones y pérdida neta | ✅ | `perdida_bruta`, `recuperacion`, `perdida_neta` (generada) |
| Línea de negocio afectada | ✅ | `linea_negocio` |
| Fechas de ocurrencia, detección y contabilización | ✅ | `fecha_ocurrencia`, `fecha_deteccion`, `fecha_contabilizacion` |
| Acciones correctivas implementadas | ✅ | [[plan_accion_riesgo]] (responsable, fecha comprometida, avance, evidencia) |
| Reporte a la central de riesgo operativo | ✅ | `reportado_central_riesgo_operativo` + `catalogo_reporte_regulatorio` |
| Evaluación de riesgo **antes de lanzar nuevos productos** | ✅ | [[evaluacion_riesgo_producto]] (`requiere_no_objecion`, `estado`, `fecha_aprobacion`) |
| Planes de contingencia y continuidad, **con pruebas documentadas** | ✅ | [[plan_continuidad]] (RTO/RPO) + [[prueba_continuidad]] (resultado, hallazgos, acta) |
| Actas del Comité de Riesgos con quórum y debate real | ✅ | [[acta_comite]] (`asistentes`, `cumple_quorum`, `temas_tratados`, `decisiones`, hash) |
| Aprobación formal de metodologías por Directorio | ✅ | `politica_interna.aprobada_por_directorio` + `acta_comite_id` |
| Verificación independiente por Auditoría Interna | ✅ | [[hallazgo_auditoria]] (`origen='AUDITORIA_INTERNA'`) + [[prueba_control]] |
| Manuales de procedimientos vigentes (su ausencia es falta sancionable) | ✅ | [[politica_interna]] con `version`, `estado`, `proxima_revision` |
| **Segregación de funciones y definición de niveles y montos de autorización** | ✅ | Las políticas y manuales deben contemplar *«la adecuada segregación de funciones, definición de niveles y montos de autorización»*. Lo soportan `R-SEG-04` (quien autoriza no ejecuta), `R-SEG-07`, `limite_operativo_billetera` (los montos, como dato con vigencia) y `politica_interna.aprobada_por_directorio` |

> [!important] ¿Se puede automatizar un desembolso, o lo tiene que aprobar una persona?
> **La norma no exige aprobación humana operación por operación.** Lo que el Título V exige es
> que las políticas y manuales contemplen *«la adecuada segregación de funciones, **definición de
> niveles y montos de autorización**»* — es decir, que la entidad **defina y documente** hasta qué
> monto y bajo qué condiciones se autoriza qué, con esa metodología **aprobada por el Directorio**
> y con rastro auditable. Una política de umbrales es exactamente la forma de **cumplir** ese
> requisito, no una excepción a él.
>
> Revisado también el **Reglamento para Empresas de Tecnología Financiera** (Res. ASFI/540/2025)
> y el **Reglamento de Servicios de Pago** del BCB (RD 079/2022): ninguno impone intervención
> humana por operación. Lo que ambos exigen es autorización previa de la entidad, gestión de
> riesgos documentada, continuidad y evidencia trazable.
>
> **Lo que sí exige una persona, y con nombre**, es la vía del sospechoso: la UIF obliga a que la
> alerta la analice un analista y a que el reporte lo revise **otra identidad**
> ([[CU-44 De alerta de monitoreo a reporte de operación sospechosa]], `AP-CU44-02`). Y todo lo que
> sea debido proceso —declarar un incumplimiento, sancionar— o levantar un bloqueo de autoridad.
>
> **Alcance de esta verificación.** Se consultaron el portal de ASFI, la RNSF y análisis de
> estudios jurídicos bolivianos (§9). **No se leyó el texto íntegro del Título V artículo por
> artículo**, y esto **no sustituye una opinión legal**: la brecha **B-10** sigue abierta y la
> política pasa por [[CU-47 Evaluar el riesgo del producto antes de lanzarlo]] con no objeción del
> comité antes de operar con dinero real. Diseño resultante:
> [[Flujo de pantallas · backoffice administrador]] §3.3.

### 1.5 Gestión de seguridad de la información

ASFI cuenta con un Reglamento para la Gestión de Seguridad de la Información
(vigente desde diciembre de 2017), al que el reglamento de riesgo operativo remite:
la seguridad de la información **no es una capa aparte, es un insumo obligatorio**
del sistema de riesgo operativo.

| Requisito | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Políticas y programa formal documentado | ✅ | `politica_interna` con `materia='SEGURIDAD_INFORMACION'` |
| Responsable de seguridad designado | ✅ | [[designacion_regulatoria]] (`cargo='RESPONSABLE_SEGURIDAD_INFORMACION'`) |
| Inventario y **clasificación** de activos de información | ✅ | [[activo_informacion]] (`clasificacion`, `propietario_id`, `custodio_id`, `criticidad`) |
| Gestión de incidentes de seguridad y su reporte | ✅ | [[incidente_seguridad]] (`plazo_reporte` guardado, `reportado_al_organismo_en`) |
| Control de accesos, autenticación fuerte y trazabilidad de sesión | ✅ | M1: [[credencial_acceso]], [[factor_mfa]], [[sesion]], [[dispositivo]], [[intento_autenticacion]], [[asignacion_rol]] |
| **Autenticación fuerte obligatoria para el acceso privilegiado** | ✅ | `R-SEG-10` impide la [[sesion]] de un operador sin TOTP confirmado; `R-SEG-12` exige segundo factor en toda decisión irreversible ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]) |
| **Restablecimiento de credencial privilegiada con control dual** | 🟡 | `R-SEG-11` corta sesiones, dispositivos y refrescos al cambiar la credencial de un operador; la aprobación por una segunda identidad la sostiene la aplicación (falta la columna: [[Seguridad]] §7 S-7) |
| **Estándar de codificación segura y su verificación** | ✅ | [[Seguridad]] con la correspondencia ISO/IEC 27001 · 27002 · 27034, y `scripts/verificar_seguridad.py` como puerta del CI |
| Registros (logs) inalterables de eventos | ✅ | [[bitacora_evento]] encadenada por hash, *append-only*, con `REVOKE UPDATE/DELETE` |
| Auditoría de **lectura** de datos sensibles | ✅ | [[registro_acceso_datos]] |
| Riesgo tecnológico y dependencia de proveedores | ✅ | [[contrato_tercero]] (`es_critico`, cláusulas, SLA) + [[evaluacion_tercero]] |
| Continuidad tecnológica probada | ✅ | `plan_continuidad` / `prueba_continuidad` |
| Cifrado de datos sensibles en reposo | 🟡 | El modelo lo señala (`numero_cuenta_cifrado`, `token_proveedor`, `hash_*`, `activo_informacion.exige_cifrado`); la implementación es de infraestructura |

### 1.6 Conservación documental (Ley N° 393 de Servicios Financieros)

> Las entidades financieras conservarán los libros y documentos referentes a sus
> operaciones —microfilmados o en medios magnéticos y electrónicos— por un período
> **no menor a diez (10) años desde la fecha del último asiento contable**, sujeto a
> reglamentación de ASFI.

| Requisito | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Conservación ≥ 10 años de libros y documentos | ✅ | [[expediente_cliente]] `retencion_hasta`, [[politica_retencion]] (M9), `reclamo_cliente.conservar_hasta` |
| Integridad de lo conservado | ✅ | `hash_documento` / `hash_archivo` en todas las tablas documentales; cadenas de hash en [[bitacora_evento]], [[transaccion_billetera]], [[saldo_diario_billetera]] |
| Contabilidad no editable | ✅ | [[asiento_contable]] / [[movimiento_contable]] *append-only*, corrección por reversa |
| Compatibilidad con derecho de supresión de datos personales | ✅ | [[proceso_anonimizacion]] consulta `retencion_hasta` antes de depurar (`datos_retenidos_por_ley`) |

---

## 2. UIF — Prevención de LGI/FT/FPADM

El régimen aplicable está en el **Instructivo Específico para Entidades de
Intermediación Financiera con Enfoque Basado en Gestión de Riesgos**, aprobado por
**R.A. UIF/016/2026 de 5 de marzo de 2026** y modificado en sus artículos 52 y 53
por **R.A. UIF/050/2026 de 10 de junio de 2026**, que otorgó **90 días calendario
(prorrogables por 30 más)** para adecuar sistemas informáticos, procedimientos y
manuales internos.

> [!important] Impacto directo en este producto
> La modificación de junio de 2026 introdujo umbrales **específicos para billetera
> móvil**. Si este sistema opera billetera, esos incisos son obligatorios y son la
> razón por la que se agregó [[umbral_reporte_uif]] y se rediseñó
> [[registro_operacion_relevante]].

### 2.1 Formulario PCC-01 — declaración de origen y destino (Art. 52)

| Inciso | Supuesto | Umbral | Ventana | Estado |
| :-: | --- | --- | --- | :-: |
| a | Operación en efectivo individual | ≥ USD 10.000 | por operación | ✅ |
| b | Operaciones en efectivo acumuladas | ≥ USD 10.000 | 1 a 10 días calendario | ✅ |
| c | Cambio de moneda individual | ≥ USD 5.000 | por operación | ✅ |
| d | Cambio de moneda acumulado | ≥ USD 5.000 | 1 a 5 días calendario | ✅ |
| e | Giro nacional individual | ≥ USD 2.000 | por operación | ✅ |
| f | Giros nacionales acumulados | ≥ USD 2.000 | 1 a 5 días calendario | ✅ |
| g | Remesa individual | ≥ USD 1.000 | por operación | ✅ |
| h | Remesas acumuladas | ≥ USD 1.000 | 1 a 5 días calendario | ✅ |
| **i** | **Carga y/o retiro de Billetera Móvil, acumulado** | **≥ USD 1.000** | **1 a 3 días calendario** | ✅ |

**Cómo lo cubre el modelo:** cada inciso es una fila de [[umbral_reporte_uif]]
(`formulario='PCC-01'`, `inciso`, `concepto_operacion`, `es_acumulado`,
`umbral_usd`, `ventana_dias_calendario`, `base_normativa`). La detección escribe en
[[registro_operacion_relevante]].

| Regla fina del artículo | Estado | Dónde |
| --- | :-: | --- |
| La ventana **reinicia** en "la operación posterior a la última que superó el umbral" | ✅ | `registro_operacion_relevante.operacion_inicio_ventana_id` |
| En acumuladas, se declara origen y destino **solo de la última operación** que alcanza el umbral | ✅ | `origen_declarado` / `destino_declarado` + `es_acumulada` |
| Equivalencia en otra moneda | ✅ | `monto_equivalente_usd` + `tipo_cambio_aplicado` (reproducible) |
| Exenciones (operativa propia entre entidades reguladas, pagos con tarjeta, bonos sociales, servicios básicos, impuestos, tasas y regalías) | ✅ | `exento` + `motivo_exencion` |
| Imposibilidad operativa de contar con el formulario: pedir origen y destino y justificar en anexo del manual | 🟡 | `motivo_exencion` + [[politica_interna]] (el anexo del manual es documento) |
| Remisión a la UIF **hasta el 15 de cada mes** de los formularios del mes anterior | ✅ | `periodo_remision` + `catalogo_reporte_regulatorio.plazo_dias` + [[envio_regulatorio]] |
| **Informar también cuando no hubo ninguno** (reporte en cero) | ✅ | `reporte_regulatorio.reporte_en_cero` |
| Uso exclusivo de la información (SO, UIF, ASFI) | ✅ | [[registro_acceso_datos]] + control de accesos M1 |

### 2.2 Reporte de Operaciones Generales — ROG (Art. 53)

| Reporte | Supuesto | Umbral / ventana | Estado |
| :-: | --- | --- | :-: |
| ROG-01 | Retiros en efectivo de moneda extranjera | todos | ✅ |
| ROG-02 | Retiros en efectivo por cambio de moneda extranjera | todos | ✅ |
| ROG-03 | Operación electrónica individual | ≥ USD 2.000 | ✅ |
| ROG-03 | Operaciones electrónicas < USD 2.000 acumuladas | ≥ USD 10.000 en 1 a 10 días | ✅ |
| ROG-03 | Giro nacional por orden electrónica | ≥ USD 2.000 | ✅ |
| ROG-03 | Remesa por orden electrónica | ≥ USD 1.000 | ✅ |
| **ROG-03** | **Transferencias desde Billetera Móvil, acumuladas** | **≥ USD 1.000 en 1 a 3 días** | ✅ |
| ROG-04 | Operaciones con activos virtuales | según catálogo UIF | ✅ (estructura; el producto no los ofrece hoy) |
| — | Remisión hasta el 15 de cada mes + informe en cero | ✅ | `periodo_remision`, `reporte_en_cero` |

### 2.3 Debida diligencia, perfil y monitoreo

| Requisito | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Enfoque basado en riesgo: matriz por cliente, producto, canal y zona geográfica | ✅ | [[matriz_riesgo_lft]] (`dimension`), [[factor_riesgo_evaluado]] |
| Calificación de riesgo del cliente y su vigencia | ✅ | [[calificacion_riesgo_cliente]] (una vigente por usuario, histórico intacto) |
| Debida diligencia simplificada / estándar / ampliada / reforzada / continua | ✅ | [[debida_diligencia]] (`tipo`, `documentos_requeridos` vs `documentos_recibidos`, `vence_en`) |
| Doble revisión en DDD reforzada | ✅ | `aprobada_por` ≠ `segunda_revision_por` |
| **PEP**, incluidos familiares y allegados, y de organismos internacionales | ✅ | [[declaracion_pep]] (`tipo_pep` incluye NACIONAL, EXTRANJERO, ORG_INTERNACIONAL, FAMILIAR, ALLEGADO) |
| Beneficiario final | ✅ | [[beneficiario_final]] |
| Perfil transaccional declarado y su contraste con el observado | ✅ | [[perfil_transaccional]] (`tipo` DECLARADO/OBSERVADO) + [[desvio_perfil]] |
| Actualización periódica del conocimiento del cliente | ✅ | [[revision_periodica_kyc]] (programada según nivel de riesgo) |
| Origen de fondos con respaldo documental | ✅ | [[declaracion_origen_fondos]] (`documento_respaldo_url`, `hash_documento`) |
| Listas restrictivas y coincidencias | ✅ | [[lista_restrictiva_externa]], [[coincidencia_lista]] (M9) |
| Monitoreo por tipologías parametrizables | ✅ | [[regla_monitoreo_lft]] (`expresion` JSONB, `fuente_normativa`, `accion_automatica`) |
| Alertas con tratamiento y **cierre justificado** | ✅ | [[alerta_monitoreo_lft]] (`conclusion` obligatoria) → [[caso_investigacion_lft]] |
| ROS sin límite de monto, con narrativa y radicado | ✅ | [[reporte_operacion_sospechosa]] (M9) enlazado desde el caso |
| Funcionario Responsable / Oficial de Cumplimiento (titular y suplente) designado y comunicado | ✅ | [[oficial_cumplimiento]] + [[designacion_regulatoria]] |
| Capacitación del personal | ✅ | [[capacitacion_cumplimiento]] (tema, horas, aprobación, evidencia) |
| Manual interno con anexos | ✅ | [[politica_interna]] (`materia='LGI_FT'`, versión, acta de aprobación) |
| Congelamiento y atención de requerimientos de autoridad | ✅ | [[requerimiento_autoridad]] → [[bloqueo_saldo]] (M10) |
| Adecuación de sistemas dentro del plazo otorgado | 🔵 | Es un plazo de proyecto: el modelo ya lo soporta, la implementación debe llegar a tiempo |

**Consulta de evidencia — obligación de reporte del mes, con verificación de cero:**

```sql
SELECT u.formulario, r.periodo_remision, count(*) AS registros,
       sum(r.monto_equivalente_usd) AS usd
FROM   registro_operacion_relevante r
JOIN   umbral_reporte_uif u ON u.id = r.umbral_reporte_id
WHERE  r.periodo_remision = to_char(now() - interval '1 month', 'YYYY-MM')
  AND  NOT r.exento
GROUP  BY 1, 2;   -- si vuelve vacío: reporte_regulatorio.reporte_en_cero = TRUE
```

---

## 3. BCB — Sistema de pagos

El **Reglamento de Servicios de Pago, Instrumentos Electrónicos de Pago,
Compensación y Liquidación** fue aprobado por **Resolución de Directorio del BCB
N° 079/2022 de 6 de septiembre de 2022**, que además define a las Administradoras
de Pasarelas de Pago (agregador y facilitador) y derogó las RD 069/2021 y 129/2021.

| Requisito | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Pagos inmediatos con **QR interoperable** | ✅ | [[qr_cobro]] (`payload_emv`, `crc`, `banco_emisor`) + [[orden_cobro]] |
| Responsabilidad de la entidad por servicios deficientes | ✅ | [[incidente_operativo]] (M9), [[evento_riesgo_operativo]], [[reclamo_cliente]], [[devolucion_comision]] |
| Relación con pasarelas/agregadores y su costo | ✅ | [[proveedor_pago]] (M3) + [[costo_proveedor_operacion]] (M11) + [[contrato_tercero]] |
| Trazabilidad de compensación y liquidación | ✅ | [[conciliacion]], [[extracto_bancario]], [[movimiento_bancario]] (M3) + [[conciliacion_custodia]] (M10) |
| Requisitos mínimos de seguridad de instrumentos electrónicos | 🟡 | Soportado por M1 + [[evaluacion_antifraude]] + [[activo_informacion]]; el detalle técnico es de implementación |

---

## 4. Tributario — Facturación electrónica (SIN)

Aplica desde el momento en que se cobra comisión. El sistema de facturación en
línea exige **CUFD** (vigencia de 24 horas, extensible hasta 72 en ciertos casos),
**CUF** por documento, firma digital en la modalidad en línea, y el registro de
**eventos significativos** con plazo de hasta **48 horas** tras concluir la
contingencia para registrar inicio y fin.

| Requisito | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| CUF único por documento | ✅ | `factura_electronica.cuf` UNIQUE |
| CUFD vigente y código de control | ✅ | `cufd`, `codigo_control` |
| Sucursal, punto de venta y numeración correlativa | ✅ | `sucursal`, `punto_venta`, `numero_factura` con UNIQUE compuesto |
| Emisión **offline** por contingencia y envío posterior | ✅ | `estado_fiscal='EMITIDA_OFFLINE'` + [[lote_envio_sin]] (`codigo_recepcion`, `reintentos`) |
| Registro de eventos significativos (inicio/fin) dentro de 48 h | ✅ | [[evento_significativo_sin]] (`codigo_evento`, `fecha_inicio`, `fecha_fin`, `plazo_registro` guardado, `codigo_recepcion_evento`) |
| Anulación y nota de crédito (nunca edición) | ✅ | `anulada_en`, `motivo_anulacion` + [[nota_credito_debito]] |
| Datos del cliente para facturación (NIT/CI, razón social) | ✅ | [[datos_facturacion]] |
| IVA / IT con alícuota vigente y base legal | ✅ | [[impuesto]] (`alicuota`, `base_legal`, vigencias) + [[calculo_impuesto]] |
| Precio publicado = precio final con impuestos | ✅ | `concepto_tarifa.precio_incluye_impuesto` |
| Conciliación entre ingresos facturados y contabilidad | ✅ | [[liquidacion_ingresos]] + `asiento_contable_id` |

---

## 5. Protección de datos personales

> [!warning] Contexto boliviano
> Bolivia **no cuenta todavía con una ley integral de protección de datos
> personales** en vigencia; existen anteproyectos y regulación sectorial. Eso
> **no reduce el riesgo**: reduce la claridad. El modelo se diseñó contra el
> estándar más exigente (principios tipo RGPD + ISO/IEC 27701), de modo que la
> sanción de una ley futura sea una configuración y no un rediseño.

| Principio / derecho | Estado | Cómo lo soporta el modelo |
| --- | :-: | --- |
| Base legal y consentimiento por finalidad | ✅ | [[consentimiento]] (M1), [[aceptacion_contrato]] |
| Información al titular y política publicada | ✅ | [[documento_publicado]] (`tipo='POLITICA_PRIVACIDAD'`) |
| Derechos de acceso, rectificación, oposición y supresión | ✅ | [[solicitud_datos_personales]] (`tipo`, `fecha_limite_legal`) |
| Supresión compatible con conservación legal | ✅ | [[proceso_anonimizacion]] (`datos_retenidos_por_ley`) + `retencion_hasta` |
| Minimización y clasificación | ✅ | `activo_informacion.clasificacion`, `contiene_datos_sensibles` |
| Trazabilidad de accesos a datos sensibles | ✅ | [[registro_acceso_datos]] con justificación |
| Transferencia a terceros y a otros países | ✅ | `contrato_tercero.accede_a_datos_personales`, `pais_procesamiento` |
| Notificación de brechas | ✅ | `incidente_seguridad.datos_personales_afectados`, `notificado_a_titulares_en`, `plazo_reporte` |
| Seudonimización de identificadores sensibles | ✅ | `hash_numero_cuenta`, `hash_identificador`, `numero_enmascarado`, `token_proveedor` |

---

## 6. Estándares internacionales de auditoría y buenas prácticas

> [!note] Qué significa "cumple" acá
> Un modelo de datos **no se certifica**. Lo que se evalúa a continuación es si el
> esquema **provee la evidencia y los registros** que cada estándar exige. La
> certificación requiere además políticas, procesos, personas y auditoría externa.

### 6.1 ISO/IEC 27001:2022 — Sistema de Gestión de Seguridad de la Información

| Control (Anexo A) | Estado | Registro que lo evidencia |
| --- | :-: | --- |
| A.5.9 Inventario de información y activos asociados | ✅ | [[activo_informacion]] |
| A.5.10 Uso aceptable / A.5.12 Clasificación | ✅ | `activo_informacion.clasificacion` |
| A.5.15–A.5.18 Control de acceso, identidades, derechos | ✅ | [[rol]], [[permiso]], [[rol_permiso]], [[asignacion_rol]], [[credencial_acceso]] · `R-SEG-07`, `R-SEG-08` |
| A.5.19–A.5.22 Seguridad en relaciones con proveedores y su seguimiento | ✅ | [[contrato_tercero]], [[evaluacion_tercero]] |
| A.5.24–A.5.28 Gestión de incidentes y recolección de evidencia | ✅ | [[incidente_seguridad]], [[bitacora_evento]] (hash), [[registro_acceso_datos]] |
| A.5.29–A.5.30 Continuidad y preparación TIC | ✅ | [[plan_continuidad]], [[prueba_continuidad]] |
| A.5.31 Requisitos legales y contractuales | ✅ | [[politica_interna]], [[licencia_regulatoria]], `base_normativa` en tablas de umbrales |
| A.5.33 Protección de registros | ✅ | Tablas *append-only* + `REVOKE UPDATE/DELETE` + cadenas de hash |
| A.5.34 Privacidad y protección de PII | ✅ | §5 completo |
| A.5.35–A.5.36 Revisión independiente y cumplimiento | ✅ | [[hallazgo_auditoria]], [[prueba_control]] |
| A.6.3 Concienciación y formación | ✅ | [[capacitacion_cumplimiento]] |
| A.8.2 Derechos de acceso privilegiado | ✅ | [[asignacion_rol]] + [[registro_acceso_datos]] + `R-SEG-10`/`R-SEG-12` ([[ADR-038 Acceso administrativo · segundo factor y recuperación asistida]]) |
| A.8.5 Autenticación segura | ✅ | MFA obligatorio para operadores con TOTP; `R-SEG-10` lo hace cumplir en el motor |
| A.8.10 Borrado de información / A.8.11 Enmascaramiento | ✅ | [[proceso_anonimizacion]], `numero_enmascarado` |
| A.8.12 Prevención de fuga de datos | 🟡 | Detectable vía `registro_acceso_datos` masivo → alerta; la herramienta DLP es externa ([[Seguridad]] §7 S-3) |
| A.8.15 Registro de eventos (logging) | ✅ | [[bitacora_evento]], `evento_dominio`, [[intento_autenticacion]] |
| A.8.16 Actividades de monitoreo | ✅ | [[alerta_cumplimiento]], [[evaluacion_antifraude]], [[alerta_monitoreo_lft]] |
| A.8.24 Uso de criptografía | 🟡 | Señalado en el modelo (`*_cifrado`, hashes con pepper) y normado en [[Seguridad]] §3.4, con gate de patrones prohibidos; la gestión de llaves sigue siendo infraestructura |
| A.8.25–A.8.29 Desarrollo seguro, requisitos, ingeniería, codificación y pruebas de seguridad | ✅ | [[Seguridad]] §3 y §5 + `seguridad-aplicacion` + `scripts/verificar_seguridad.py` en el CI |
| A.8.31 Separación de entornos | ✅ | Guarda de `seeders/dev`; el CI comprueba que **bloquea** una base sin marcar |
| A.8.32 Gestión de cambios | ✅ | ADR obligatorio, generadores y el gate «lo derivado no diverge de su fuente» |
| A.8.8 Gestión de vulnerabilidades técnicas | 🟡 | Escaneo de dependencias en cada PR ([[Seguridad]] §3.6); las pruebas de intrusión siguen abiertas (S-1) |
| A.5.7 Inteligencia de amenazas | 🔵 | Proceso, no esquema; declarado en [[Seguridad]] §7 S-5 |

### 6.2 Otros estándares

| Estándar | Qué exige | Estado | Dónde |
| --- | --- | :-: | --- |
| **ISO 22301** Continuidad del negocio | BIA, RTO/RPO, planes probados | ✅ | [[plan_continuidad]] (`rto_minutos`, `rpo_minutos`), [[prueba_continuidad]] (`rto_obtenido_minutos`) |
| **ISO 31000** Gestión del riesgo | Marco, proceso, registro de riesgos y tratamiento | ✅ | [[evento_riesgo_operativo]], [[control_interno]], [[plan_accion_riesgo]], [[matriz_riesgo_lft]] |
| **ISO 19011** Auditoría de sistemas de gestión | Programa de auditoría, hallazgos, seguimiento | ✅ | [[hallazgo_auditoria]], [[prueba_control]], [[plan_accion_riesgo]] |
| **ISO/IEC 27701** Privacidad | Roles, PII, derechos del titular, terceros | ✅ | §5 |
| **ISO/IEC 27002:2022** Guía de implementación | Cómo se implementa cada control de 27001 | ✅ | [[Seguridad]] §3: una fila por control, con quién lo hace cumplir |
| **ISO/IEC 27034** Seguridad de aplicaciones | Controles de aplicación **verificables**, con nivel de confianza declarado | ✅ | [[Seguridad]] §3 (control) + §6 (gate). El nivel es la columna motor / gate / revisión |
| **ISO/IEC 27005** Gestión del riesgo de seguridad | Identificar, analizar y tratar | ✅ | [[Seguridad]] §2 modelo de amenazas + [[evento_riesgo_operativo]] |
| **ISO/IEC 27017** Seguridad en la nube | Responsabilidad compartida, aislamiento | 🟡 | [[ADR-025 Empaquetado y despliegue de los servicios]] y [[Seguridad]] §3.7; falta el contrato con el proveedor |
| **ISO/IEC 27018** PII en la nube | Tratamiento por el encargado | 🟡 | §5 y [[Seguridad]] §3.3; falta el anexo contractual |
| **ISO/IEC 20000-1** Gestión de servicios TI | Incidentes, SLA, proveedores | 🟡 | [[incidente_operativo]], [[ticket_soporte]], `acuerdo_nivel_servicio` |
| **COSO / COBIT** Control interno y gobierno TI | Ambiente de control, actividades, monitoreo | ✅ | [[comite_gobierno]], [[acta_comite]], [[control_interno]], [[politica_interna]] |
| **PCI DSS** (si se tocan tarjetas) | No almacenar PAN, tokenizar | ✅ | [[instrumento_fondeo]] guarda `token_proveedor` y `hash_identificador`, **nunca el PAN** |
| **ISO 4217 / 8601 / 3166** | Monedas, fechas, países | ✅ | `moneda CHAR(3)`, `TIMESTAMPTZ`, `pais CHAR(2)` en todo el modelo |
| **NIIF / contabilidad de doble partida** | Devengado vs percibido, no edición | ✅ | [[devengo_comision]] separado de [[cargo_comision]]; [[asiento_contable]] con `SUM(debe)=SUM(haber)` y reversa |
| **Principios BCBS 239** (agregación de datos de riesgo) | Linaje, exactitud, oportunidad | 🟡 | Linaje por FK y eventos; la infraestructura de reporting es externa |

---

## 7. Los siete controles que hacen auditable a este modelo

Si hubiera que defender el diseño en una inspección con siete frases:

1. **Nada de dinero se edita.** [[transaccion_billetera]], [[movimiento_billetera]],
   [[asiento_contable]], [[devengo_comision]] y [[movimiento_custodia]] son
   *append-only* con `REVOKE UPDATE, DELETE`. Se corrige reversando.
2. **Todo movimiento tiene contrapartida.** Partida doble interna en la billetera y
   doble partida contable en el mayor, con triggers que validan la igualdad.
3. **El dinero de los clientes se prueba todos los días.**
   [[conciliacion_custodia]] con `ratio_cobertura` y bloqueo del cierre diario si
   hay descuadre.
4. **Nada se acredita dos veces.** `clave_idempotencia` UNIQUE en webhooks, órdenes,
   transacciones, devengos y desembolsos.
5. **Todo lo que pasó tiene autor, hora y hash.** [[bitacora_evento]] encadenada,
   [[registro_acceso_datos]] para lecturas y `evento_dominio` como outbox
   transaccional.
6. **Los plazos se guardan, no se calculan.** Reclamos, casos, requerimientos e
   incidentes almacenan el plazo que regía el día del hecho.
7. **Las cifras regulatorias son datos con vigencia**, con la cita normativa al
   lado, y las versiones anteriores nunca se borran.

---

## 8. Brechas abiertas (lo que todavía falta)

Honestidad sobre lo que este repositorio **no** resuelve:

| # | Brecha | Tipo | Acción recomendada |
| :-: | --- | --- | --- |
| **B-1** | **Autorización de ASFI** (certificado de adecuación → licencia ETF, categoría pagos) | 🔵 Regulatoria | Iniciar el trámite antes de cualquier operación con dinero real. Sin esto, todo lo demás es irrelevante |
| **B-2** | Cifras de límites, umbrales, plazos y alícuotas sin sembrar | 🟡 Seeder | Legal confirma cada valor y llena `base_normativa` en `limite_operativo_billetera`, `umbral_reporte_uif`, `catalogo_reporte_regulatorio`, `impuesto` |
| **B-3** | ~~Evento significativo de facturación sin estructura propia~~ | ✅ Cerrada | Resuelta en esta revisión: se agregó [[evento_significativo_sin]] y `factura_electronica.evento_significativo_id` |
| **B-4** | Formato exacto de los archivos PCC-01 / ROG-01..04 y del módulo de reclamos | 🟡 Integración | Mapear cada campo del formato oficial contra las columnas; `catalogo_reporte_regulatorio.formato` guarda el layout |
| **B-5** | Pruebas de intrusión e inteligencia de amenazas | 🔵 Operativa | **Parcialmente cerrada**: el escaneo de dependencias y el estándar de codificación segura ya son gate ([[Seguridad]] §3.6 y §6). Quedan el pentest y la suscripción de amenazas (S-1, S-5); solo el resultado entra al modelo |
| **B-6** | Gestión de llaves criptográficas y cifrado en reposo | 🔵 Infraestructura | KMS/HSM; el modelo marca qué columnas lo exigen (`version_llave`) y [[Seguridad]] §3.4 fija qué algoritmo va en cada caso (S-2) |
| **B-7** | Políticas, manuales y actas reales | 🔵 Organizacional | Las tablas existen vacías: hay que redactarlos y aprobarlos en Directorio |
| **B-8** | Capital mínimo, estructura societaria y auditoría externa | 🔵 Societaria | Fuera del alcance técnico |
| **B-9** | Plazo de adecuación UIF (90 + 30 días desde junio de 2026) | 🟡 Proyecto | El modelo ya soporta los nuevos artículos 52 y 53; la implementación debe estar en producción dentro del plazo |
| **B-10** | Validación legal de todo este documento | 🔵 Legal | Revisión por abogado especializado en regulación financiera boliviana antes de usarlo como respaldo |

---

## 9. Fuentes consultadas

Verificadas en agosto de 2026. Las normas cambian: revalidar antes de decisiones
de producto.

- ASFI — [Reglamento para Empresas de Tecnología Financiera (Res. ASFI/540/2025)](https://www.asfi.gob.bo/node/1176)
- ASFI — [RNSF Libro IV, Título I — Protección del consumidor financiero (Punto de Reclamo, plazos, conservación)](https://bolivia.infoleyes.com/norma/4590/recopilaci%C3%B3n-de-normas-servicios-financieros-libro-iv-t%C3%ADtulo-i)
- ASFI — [Derechos del consumidor financiero](https://www.asfi.gob.bo/la/derechos-del-consumidor-financiero)
- ASFI — [Reglamento de derechos del consumidor financiero (PDF)](https://www.baneco.com.bo/doc/asfi/ASFI-Reglamento_derechos_del_consumidor.pdf)
- RNSF Libro 3, Título V — [Gestión de riesgo operativo: ciclo, CIRO, categorías y factores](https://www.piranirisk.com/es/academia/especiales/riesgo-operativo-asfi-bolivia)
- ASFI — [Recopilación de Normas para Servicios Financieros (RNSF), índice oficial](https://www.asfi.gob.bo/la/recopilacion-normas-para-servicios-financieros-rnsf) · consultada en agosto de 2026 para la exigencia de **segregación de funciones y definición de niveles y montos de autorización**
- ASFI — [Empresas de tecnología financiera en Bolivia: un nuevo horizonte (documento oficial, 2025)](https://www.asfi.gob.bo/sites/default/files/2025-07/Empresas%20de%20tecnolog%C3%ADa%20financiera%20en%20Bolivia%20Un%20nuevo%20horizonte%20para%20la%20innovaci%C3%B3n%20financiera.pdf)
- BCB — [Reglamento de Servicios de Pago (RD 079/2022), comunicado oficial](https://www.bcb.gob.bo/?q=content/el-bcb-emite-reglamento-que-profundiza-el-proceso-de-modernizaci%C3%B3n-del-sistema-de-pagos)
- ASFI — [Reglamento para la Gestión de Seguridad de la Información](https://redtiseg.com/reglamento-de-seguridad-de-la-informacion-asfi/)
- UIF — [Resolución Administrativa UIF/050/2026 (modifica arts. 52 y 53 del Instructivo aprobado por R.A. UIF/016/2026)](https://www.uif.gob.bo/wp-content/uploads/2026/06/R.A.50.2026-f-con-anexos-y-firma-colores.pdf)
- UIF — [Instructivo Específico para Entidades de Intermediación Financiera con EBR](https://www.uif.gob.bo/wp-content/uploads/2025/09/Instructivo-EIF-RA-42-2022.pdf) · [Normativa externa UIF](https://www.uif.gob.bo/index.php/normativa-externa/)
- BCB — [Reglamento de Servicios de Pago, Instrumentos Electrónicos de Pago, Compensación y Liquidación (RD 079/2022)](https://compliancelatam.legal/bolivia-se-aprobo-el-reglamento-de-servicios-de-pago-instrumentos-electronicos-de-pago-compensacion-y-liquidacion/) · [análisis complementario](https://www.ferrere.com/es/novedades/se-aprobo-el-reglamento-de-servicios-de-pago-instrumentos-electronicos-de-pago-compensacion-y-liquidacion/)
- BCB — [Informe de Vigilancia del Sistema de Pagos 2023](https://www.bcb.gob.bo/webdocs/publicacionesbcb/2024/03/07/SISTEMA%20DE%20PAGOS%202023vf.pdf)
- [Regulación de pagos digitales y e-commerce en Bolivia (FES)](https://library.fes.de/pdf-files/bueros/bolivien/20356.pdf) · [Enfoques regulatorios para servicios financieros móviles en Latinoamérica (AFI)](https://www.afi-global.org/wp-content/uploads/2024/10/mobile_financial_services_in_latin_american_countries_-_sp.pdf)
- [Ley N° 393 de Servicios Financieros (texto actualizado)](https://www.economiayfinanzas.gob.bo/sites/default/files/2021-07/LEY%20393_%20actualizada_el_2019.pdf)
- SIN — [Facturación electrónica / SIAT: CUF, CUFD, código de control](https://siatinfo.impuestos.gob.bo/index.php/informacion/modalidades-facturacion/facturacion-electronica) · [Solicitud de CUFD](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/implementacion-servicios-facturacion/codigos/solicitud-cufd)
- Análisis del Reglamento ETF — [BDA Abogados](https://www.bda-lawfirm.com/news/nuevo-reglamento-para-empresas-de-tecnologia-financiera-etf/) · [Moreno Baldivieso](https://emba.com.bo/autoridad-de-supervision-del-sistema-financiero-asfi-regula-a-las-fintech-en-bolivia/) · [ABI](https://abi.bo/index.php/component/content/article/36-notas/noticias/economia/66198-asfi-aprueba-y-pone-en-vigencia-el-reglamento-para-empresas-de-tecnologia-financiera)
- Protección de datos en Bolivia — [Guía de implementación (Internet Bolivia)](https://internetbolivia.org/wp-content/uploads/2025/05/guia_proteccion_datos_web.pdf) · [Anteproyecto de ley (AGETIC)](https://agetic.gob.bo/sites/default/files/2025-06/DATOS-PERSONALES-PRESENTACION-ANTEPROYECTO-DE-LEY-2024-firmado.pdf)

---

## 9 bis. Cómo se ejecuta cada obligación

Este documento dice **qué obliga** la norma. Los otros dos documentos de la bóveda
completan la cadena:

| Capa | Documento | Responde |
| --- | --- | --- |
| Ejecución | [[_CasosDeUso]] | cómo se cumple, paso a paso, con qué tablas y qué evidencia |
| Garantía | [[Restricciones]] | qué impide, a nivel de base de datos, que se viole |

Ejemplos de la cadena completa:

| Obligación | Caso de uso | Restricción |
| --- | --- | --- |
| Umbral de billetera móvil (UIF art. 52 inc. i) | [[CU-41 Detectar umbral y registrar formulario PCC-01]] | `R-UIF-01`..`R-UIF-05` |
| Respuesta a reclamos en 5 días hábiles | [[CU-52 Atender un reclamo en plazo]] | `R-CON-01`..`R-CON-03` |
| Respaldo de los fondos de clientes | [[CU-50 Conciliar la custodia y verificar el encaje]] | `R-BIL-11`, `R-BIL-12` |
| Preaviso de cambio de tarifas | [[CU-34 Publicar un tarifario nuevo con preaviso]] | `R-TAR-08` |
| Base de eventos de riesgo operativo | [[CU-54 Registrar un evento de riesgo operativo]] | `R-RIS-01`, `R-RIS-02` |
| Operar solo dentro del alcance autorizado | [[CU-46 Verificar el alcance de la licencia]] | `R-LIC-01`, `R-LIC-02` |

---

## 10. Cómo mantener vivo este documento

1. **Cada norma nueva entra como fila, no como parche de código**: `umbral_reporte_uif`,
   `catalogo_reporte_regulatorio`, `limite_operativo_billetera`, `impuesto`.
2. **Cada cambio de norma se registra en [[politica_interna]]** con nueva versión y
   acta de aprobación.
3. **Este archivo se revisa cada seis meses** o ante cualquier resolución nueva de
   ASFI, UIF, BCB o SIN, y se actualiza `fecha_revision` en el frontmatter.
4. **Las brechas de §8 se gestionan como [[hallazgo_auditoria]]** con responsable y
   plazo, no como una lista en un documento.
