---
tags:
  - caso-uso
  - modulo/01-identidad-usuarios-y-seguridad
  - modulo/10-billetera-custodia-y-dinero-electronico
  - modulo/12-cumplimiento-regulatorio-y-consumidor-financiero
codigo: CU-01
criticidad: alta
actores: [Usuario, Sistema]
normas: [ASFI Res. 540/2025, UIF EBR, ASFI Consumidor Financiero]
---

# CU-01 — Registro y apertura de billetera

> **Objetivo.** Que una persona pueda abrir cuenta en minutos, con la debida
> diligencia mínima, y quede habilitada a operar **solo dentro de los límites que
> le corresponden por ese nivel de conocimiento**.

## Actores y disparador

- **Actor principal:** persona natural que descarga la app.
- **Disparador:** solicitud de registro con teléfono y documento.
- **Actores secundarios:** proveedor de verificación de identidad, motor de listas.

## Precondiciones

1. Existe una [[licencia_regulatoria]] con `estado='OTORGADA'` cuyo
   `alcance_autorizado` incluya el servicio de billetera → ver [[CU-46 Verificar el alcance de la licencia]].
2. Existe [[contrato_adhesion]] vigente de tipo `BILLETERA` y un [[tarifario]] en
   estado `VIGENTE` publicado.
3. Hay filas activas de [[limite_operativo_billetera]] para el nivel
   `SIMPLIFICADA`.

## Flujo principal

1. El usuario ingresa teléfono; el sistema emite un [[token_verificacion]] de
   propósito `VERIFICACION_TELEFONO` (nunca se guarda el valor plano, solo su hash
   con *pepper*).
2. Validado el token, se crea [[usuario]] con `estado='PENDIENTE_VERIFICACION'` y
   se registran [[dispositivo]] y [[sesion]].
3. El usuario captura su documento; se crea [[documento_identidad]] y se dispara
   [[verificacion_kyc]] contra el proveedor.
4. **En la misma transacción** que aprueba el KYC:
   - se crea [[debida_diligencia]] con `tipo='SIMPLIFICADA'` y `estado='COMPLETA'`;
   - se evalúan los factores de [[matriz_riesgo_lft]] y se escriben
     [[factor_riesgo_evaluado]];
   - se crea [[calificacion_riesgo_cliente]] vigente con `nivel` y
     `nivel_dd_requerido`, y `proxima_revision` según la periodicidad del nivel;
   - se crea [[expediente_cliente]] con `retencion_hasta` calculada.
5. Se cotejan nombre y documento contra [[lista_restrictiva_externa]]; toda
   coincidencia genera [[coincidencia_lista]] en estado `PENDIENTE_REVISION` y
   **bloquea la apertura** hasta su descarte.
6. El usuario declara su condición PEP → [[CU-03 Declaración PEP y beneficiario final]].
7. El usuario declara su [[perfil_transaccional]] (`tipo='DECLARADO'`: monto
   mensual estimado, actividad económica, origen de fondos).
8. El usuario acepta contrato y tarifario → [[CU-05 Aceptar contrato de adhesión y tarifario]].
9. Se crea [[cuenta_billetera]] con `tipo='USUARIO'`, `estado='ACTIVA'`,
   `nivel_debida_diligencia='SIMPLIFICADA'`, saldos en cero, y su
   [[cuenta_contable]] espejo.
10. Se escribe `evento_dominio` `USUARIO_REGISTRADO` y `BILLETERA_ABIERTA` en la
    misma transacción.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 4a | El proveedor de KYC rechaza el documento | `verificacion_kyc.estado='RECHAZADA'`; la cuenta no se crea; el usuario puede reintentar con límite de intentos ([[intento_validacion_token]]) |
| 5a | Coincidencia en lista restrictiva confirmada | No se abre cuenta. Se abre [[caso_investigacion_lft]] con `origen='REVISION_PERIODICA'` y se evalúa reporte |
| 6a | El usuario declara ser PEP | La apertura exige debida diligencia reforzada y aprobación de nivel superior antes de activar la cuenta |
| 8a | El usuario no acepta el contrato | Queda en `PENDIENTE_VERIFICACION`; sin aceptación no hay cuenta |
| 9a | Ya existe cuenta activa del mismo titular y moneda | Se rechaza por `R-BIL-04`; se ofrece recuperar acceso |

## Postcondiciones

- Existe exactamente una [[cuenta_billetera]] activa por (usuario, moneda, tipo).
- El usuario tiene calificación de riesgo vigente y límites aplicables resueltos.
- El expediente del cliente queda abierto con su fecha de conservación.

## Contrato · `openapi/identidad.yaml`

```ts
export const EntradaCU01 = z.object({
  claveIdempotencia: z.string().uuid(),
  telefonoE164:      z.string().regex(/^\+591\d{8}$/),
  nombres:           z.string().min(2).max(60),
  apellidos:         z.string().min(2).max(60),
  fechaNacimiento:   z.string().date(),
  documento:         z.object({ tipo: z.enum(['CI','CEX','PASAPORTE']), numero: z.string() }),
  aceptaContratos:   z.array(z.string().uuid()).min(1),
}).strict()

export const SalidaCU01 = z.object({
  usuarioId:         z.string().uuid(),
  cuentaBilleteraId: z.string().uuid(),
  nivelDiligencia:   z.enum(['SIMPLIFICADA','ESTANDAR','AMPLIADA','REFORZADA']),
  limites:           z.array(z.object({ concepto: z.string(), ventana: z.string(), monto: MontoSchema })),
}).strict()

export const ErroresCU01 = {
  KYC_RECHAZADO: 'AP-CU01-01',
  COINCIDENCIA_EN_LISTA: 'AP-CU01-02',
  CUENTA_YA_EXISTE: 'AP-CU01-03',
  CONTRATO_NO_ACEPTADO: 'AP-CU01-04',
  SERVICIO_NO_AUTORIZADO: 'AP-CU01-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `KYC_RECHAZADO` | El proveedor no pudo validar el documento |
| `COINCIDENCIA_EN_LISTA` | Coincidencia confirmada en lista restrictiva: no se abre cuenta |
| `CUENTA_YA_EXISTE` | Ya hay cuenta activa para ese titular y moneda (R-BIL-04) |
| `CONTRATO_NO_ACEPTADO` | Falta aceptar el contrato de adhesión vigente (R-CON-06) |
| `SERVICIO_NO_AUTORIZADO` | La licencia no habilita billetera (R-LIC-01) |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `evaluarMatrizRiesgo` | Puntúa los factores y devuelve nivel y diligencia exigida; puro |
| Átomo | `calcularRetencionLegal` | Fecha hasta la que hay que conservar el expediente |
| Molécula | `VerificacionKycAdaptador` | Proveedor externo de identidad, detrás de una interfaz |
| Molécula | `ListaRestrictivaRepositorio` | Cotejo contra listas vigentes |
| Molécula | `CuentaBilleteraRepositorio` | Alta de la cuenta y su espejo contable |
| Organismo | `CU01RegistrarUsuario` | Una transacción: usuario, KYC, diligencia, calificación, expediente y cuenta |
| Página | `POST /usuarios` | Traduce y delega, sin lógica |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `usuario.registrado` | Notificación de bienvenida y alta en bandeja | Ninguno: ruta pública |
| `billetera.abierta` | Cálculo inicial de límites por nivel | — |

## Interfaz

- **App:** Alta guiada en cuatro pasos con la cámara para el documento; al terminar muestra los topes que le corresponden.
- **Backoffice:** Cola de altas con KYC observado, para revisión manual.

## Restricciones aplicables

`R-BIL-04` · `R-BIL-05` · `R-UIF-09` · `R-UIF-10` · `R-UIF-11` · `R-CON-06` ·
`R-SEG-01` · `R-LIC-01` · `R-AUD-04`

## Evidencia que deja

[[usuario]] · [[documento_identidad]] · [[verificacion_kyc]] ·
[[debida_diligencia]] · [[calificacion_riesgo_cliente]] · [[expediente_cliente]] ·
[[perfil_transaccional]] · [[aceptacion_contrato]] · [[cuenta_billetera]] ·
[[bitacora_evento]]

## Criterios de aceptación

```gherkin
Dado un usuario con documento válido y sin coincidencias en listas
Cuando completa el registro
Entonces existe una cuenta_billetera ACTIVA con nivel SIMPLIFICADA
Y existe una calificacion_riesgo_cliente vigente
Y existe una aceptacion_contrato con hash_evidencia no nulo

Dado un usuario con coincidencia confirmada en lista restrictiva
Cuando intenta completar el registro
Entonces no se crea cuenta_billetera
Y queda una coincidencia_lista en estado CONFIRMADA

Dado un usuario que ya tiene cuenta ACTIVA en BOB
Cuando intenta abrir otra cuenta USUARIO en BOB
Entonces la operación falla por violación de unicidad (R-BIL-04)
```

## Ver también

[[CU-02 Elevar nivel de debida diligencia]] · [[CU-03 Declaración PEP y beneficiario final]] · [[CU-05 Aceptar contrato de adhesión y tarifario]] · [[CU-46 Verificar el alcance de la licencia]] · [[CU-69 Invitar a un contacto y registrar sus referencias]] · [[_CasosDeUso]] · [[Restricciones]] · [[Cumplimiento]]
