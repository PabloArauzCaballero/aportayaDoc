---
tags:
  - caso-uso
  - modulo/04-entregas-de-fondo
  - modulo/10-billetera-custodia-y-dinero-electronico
codigo: CU-18
criticidad: alta
actores: [Usuario, Sistema, Soporte]
normas: [ASFI Seguridad de la Información, UIF titularidad, PCI-DSS por analogía]
---

# CU-18 — Registrar y verificar una cuenta bancaria de destino

> **Objetivo.** Que el dinero salga solamente hacia una cuenta que demostradamente
> es del titular, y que el número de esa cuenta nunca quede escrito en claro en
> ninguna parte del sistema.

## Actores y disparador

- **Actor principal:** usuario titular.
- **Disparadores:** primer retiro; primera entrega de fondo que cobra por banco;
  cambio de banco; cuenta rechazada por el proveedor.

## Precondiciones

1. El usuario tiene [[debida_diligencia]] vigente con nombre y documento
   verificados ([[CU-01 Registro y apertura de billetera]]).
2. Existe [[politica_billetera]] con la ventana de enfriamiento aplicable a los
   instrumentos nuevos.

## Flujo principal

1. El usuario informa `tipo_cuenta`, `entidad_financiera`, número de cuenta,
   `titular_nombre`, `titular_documento` y `moneda`.
2. **El número se cifra antes de tocar disco**: se guarda `numero_cuenta_cifrado`,
   el `hash_numero_cuenta` para poder detectar duplicados sin descifrar, y
   `numero_enmascarado` para mostrar. **El número en claro no se persiste, no se
   registra en bitácora y no viaja en ninguna notificación** (`R-SEG-01`).
3. Se compara `titular_documento` contra el documento del titular de la billetera.
   **Si no coinciden, no se registra**: no se transfiere a cuentas de terceros desde
   una billetera personal, y esa regla existe por lavado, no por comodidad.
4. Se verifica la titularidad por uno de los métodos habilitados, que queda en
   `metodo_verificacion`:
   - **micro-depósito**: se acredita un importe pequeño y aleatorio y el usuario lo
     informa; se compara contra lo enviado;
   - **consulta al proveedor**: el banco o la pasarela confirma nombre y documento;
   - **comprobante documental**: extracto o certificado, revisado por soporte con
     evidencia archivada.
5. Verificada, se escribe `estado_verificacion = 'VERIFICADA'` y `verificada_en`. La
   cuenta queda `bloqueada_hasta` el fin de la ventana de enfriamiento: **una cuenta
   recién agregada no cobra ese mismo minuto**.
6. La primera cuenta verificada queda `es_principal = true`. Al designar otra como
   principal, **en la misma transacción** la anterior deja de serlo: nunca hay dos.
7. Todo alta, verificación o baja de cuenta se notifica por los canales verificados
   y se registra en [[bitacora_evento]].

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 3a | El documento del titular de la cuenta no coincide | Rechazo `TITULAR_NO_COINCIDE`. Si insiste, se registra como señal para [[CU-44 De alerta de monitoreo a reporte de operación sospechosa]] |
| 2a | La cuenta ya está registrada por otro usuario | Se detecta por `hash_numero_cuenta` sin descifrar nada. Puede ser legítimo (cuenta conjunta) o no: queda en revisión manual con evidencia |
| 4a | El micro-depósito se informa mal tres veces | La verificación se cancela y hay que empezarla de nuevo; el importe enviado se recupera |
| 4b | El proveedor no responde | La cuenta queda `PENDIENTE`; se reintenta con espera creciente y el usuario ve el estado real, no un "listo" prematuro |
| 5a | Se intenta retirar antes de que termine el enfriamiento | Rechazo con el tiempo restante ([[CU-11 Retirar saldo]]) |
| 6a | Se da de baja la cuenta principal existiendo otra verificada | La otra pasa a principal en la misma transacción |
| — | El proveedor rechaza la cuenta al desembolsar | Pasa a `RECHAZADA` con el motivo y se pide otra; la entrega no se pierde, se reintenta ([[CU-28 Emitir la orden de desembolso y ejecutar el intento]]) |
| — | Cambio de nombre del titular por matrimonio o rectificación | Se revalida contra el documento actualizado; no se edita a mano el nombre guardado |

## Postcondiciones

- Todo destino de dinero externo tiene titularidad verificada, con método y fecha.
- Ningún número de cuenta existe en claro en base, registro ni respaldo.

## Contrato · `openapi/entregas.yaml`

```ts
export const EntradaCU18 = z.object({
  tipoCuenta:        z.enum(['CAJA_AHORRO', 'CORRIENTE', 'BILLETERA_EXTERNA']),
  entidadFinanciera: z.string().min(2).max(60),
  numeroCuenta:      z.string().min(6).max(34),   // nunca se persiste así
  titularNombre:     z.string().min(3).max(120),
  titularDocumento:  z.string().min(5).max(30),
  moneda:            z.enum(['BOB', 'USD']),
  esPrincipal:       z.boolean().default(false),
}).strict()

export const EntradaVerificarCU18 = z.object({
  cuentaId: z.string().uuid(),
  metodo:   z.enum(['MICRODEPOSITO', 'CONSULTA_PROVEEDOR', 'COMPROBANTE']),
  montoInformado: MontoSchema.optional(),
}).strict()

export const SalidaCU18 = z.object({
  cuentaId:           z.string().uuid(),
  numeroEnmascarado:  z.string(),
  estadoVerificacion: z.enum(['PENDIENTE', 'VERIFICADA', 'RECHAZADA']),
  esPrincipal:        z.boolean(),
  bloqueadaHasta:     z.string().datetime().nullable(),
}).strict()

export const ErroresCU18 = {
  TITULAR_NO_COINCIDE:   'AP-CU18-01',
  CUENTA_YA_REGISTRADA:  'AP-CU18-02',
  ENTIDAD_NO_SOPORTADA:  'AP-CU18-03',
  VERIFICACION_FALLIDA:  'AP-CU18-04',
  MONEDA_INCOMPATIBLE:   'AP-CU18-05',
  LIMITE_DE_CUENTAS:     'AP-CU18-06',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `TITULAR_NO_COINCIDE` | El documento del titular de la cuenta no es el del titular de la billetera |
| `CUENTA_YA_REGISTRADA` | El mismo `hash_numero_cuenta` ya existe para ese usuario (`R-BIL-17`) |
| `ENTIDAD_NO_SOPORTADA` | Ningún [[proveedor_pago]] activo desembolsa a esa entidad |
| `VERIFICACION_FALLIDA` | Se agotaron los intentos del método elegido |
| `MONEDA_INCOMPATIBLE` | La moneda de la cuenta no corresponde a la de la billetera de origen |
| `LIMITE_DE_CUENTAS` | Supera el máximo de cuentas activas que fija la política |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `enmascarar(numero)` | Deja visibles los últimos cuatro dígitos; puro |
| Átomo | `coincideTitularidad(cuenta, titular)` | Compara documento y nombre normalizados; puro |
| Molécula | `CifradorDeInstrumentos` | Cifra, descifra bajo demanda justificada y calcula el hash |
| Molécula | `CuentaBeneficiariaRepositorio` | Persistencia, unicidad por hash y principal única |
| Molécula | `VerificadorDeTitularidad` | Un adaptador por método, con la misma interfaz |
| Organismo | `CU18RegistrarCuenta` · `CU18VerificarCuenta` | Transacción: escribe, marca principal, bitácora y evento |
| Página | `POST /cuentas-bancarias` · `POST /cuentas-bancarias/:id/verificacion` | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `cuenta_beneficiaria.registrada` | Aviso por canales verificados | `BILLETERA_OPERAR` |
| `cuenta_beneficiaria.verificada` | Habilita retiro y desembolso pasado el enfriamiento | — |
| `cuenta_beneficiaria.rechazada` | Aviso con el motivo y pedido de otra cuenta | — |
| — | Trabajo que concilia los micro-depósitos enviados y no informados | — |

## Interfaz

- **App:** *Cobros → Mis cuentas*: lista con el número enmascarado, el banco, el
  estado de verificación y cuál es la principal. Agregar una cuenta explica desde el
  primer paso que tiene que estar a nombre del titular.
- **Backoffice:** revisión de verificaciones por comprobante, con la evidencia y sin
  exponer el número completo salvo justificación registrada (`R-SEG-02`).

## Restricciones aplicables

`R-SEG-01` · `R-SEG-02` · `R-BIL-09` · `R-BIL-17` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[cuenta_bancaria_beneficiario]] · [[bitacora_evento]] · [[registro_acceso_datos]] ·
`evento_dominio` · [[instrumento_fondeo]]

## Criterios de aceptación

```gherkin
Dado un usuario verificado
Cuando registra una cuenta a su propio nombre
Entonces se guarda numero_cuenta_cifrado y hash_numero_cuenta
Y no existe el número en claro en ninguna columna ni en la bitácora

Dado un usuario que registra una cuenta a nombre de otra persona
Cuando envía el formulario
Entonces se rechaza con TITULAR_NO_COINCIDE

Dada una cuenta recién verificada
Cuando el usuario intenta retirar dentro de la ventana de enfriamiento
Entonces el retiro se rechaza indicando el tiempo restante

Dada una cuenta principal existente
Cuando se designa otra como principal
Entonces solo una queda con es_principal en true
```

## Ver también

[[CU-11 Retirar saldo]] · [[CU-22 Liquidar y entregar el fondo]] · [[CU-28 Emitir la orden de desembolso y ejecutar el intento]] · [[CU-99 Dar de alta un proveedor de pago y enrutar el cobro]]
