---
tags:
  - caso-uso
  - modulo/06-transparencia-y-reputacion
codigo: CU-75
criticidad: media
actores: [Usuario, Tercero verificador, Sistema]
normas: [Protección de datos, transparencia, no discriminación arbitraria]
---

# CU-75 — Emitir un certificado de reputación verificable

> **Objetivo.** Que el historial que alguien construyó dentro de AportaYa le sirva
> afuera —para entrar a otro grupo, para respaldar un aval— sin que eso obligue a
> mostrar su vida financiera entera a quien se lo pida.

## Actores y disparador

- **Actor principal:** usuario titular, que es **el único** que puede pedirlo.
- **Disparadores:** postulación a un grupo externo; pedido de un organizador;
  necesidad de acreditar antigüedad; renovación de un certificado vencido.

## Precondiciones

1. Existe [[puntaje_reputacion]] vigente y un [[snapshot_reputacion]] congelado
   ([[CU-71 Recalcular el puntaje de reputación]]).
2. El usuario tiene identidad verificada: un certificado sobre una identidad sin
   verificar no acredita nada.

## Flujo principal

1. El titular pide el certificado y **elige qué incluye**: puntaje y nivel,
   antigüedad, grupos completados, insignias, índice de puntualidad. Lo que no
   elige, no aparece.
2. Se toma el [[snapshot_reputacion]] vigente —nunca un cálculo al vuelo— para que
   el certificado sea reproducible: dos emisiones sobre el mismo snapshot dicen lo
   mismo.
3. Se crea [[certificado_reputacion]] con `snapshot_id`, `codigo_verificacion`
   único, `hash_contenido`, `firma_digital`, `url_publica`, `emitido_en` y
   **`expira_en`**. Un certificado de reputación sin vencimiento miente al mes
   siguiente.
4. La verificación pública en `url_publica` devuelve **solo**: válido o no, la fecha
   de emisión, la de vencimiento y el contenido que el titular eligió publicar. No
   revela nada más, ni siquiera si el usuario existe cuando el código es inválido.
5. El verificador puede recomputar el `hash_contenido` desde el contenido publicado y
   validar la `firma_digital` con la clave pública de la plataforma. Cada consulta se
   registra en [[verificacion_publica]].
6. **Revocación.** El titular puede revocarlo cuando quiera (`revocado_en`); la
   plataforma solo cuando el snapshot que lo sustenta resultó erróneo. Revocado, la
   URL responde "revocado", no un error genérico.
7. Nunca se emite un certificado **negativo** ni hay endpoint para consultar la
   reputación de un tercero sin su certificado: la información es del usuario y sale
   por su decisión.

## Flujos alternativos

| # | Situación | Resultado |
| :-: | --- | --- |
| 2a | El usuario no tiene historial suficiente | Se emite igual, marcando `SIN_HISTORIAL`: no es cero, es "todavía no sabemos", y esa distinción importa |
| 3a | Ya hay un certificado vigente equivalente | Se devuelve el existente en vez de emitir otro; renovar antes de vencer sí crea uno nuevo y revoca el anterior |
| 4a | Código de verificación inexistente | Respuesta genérica de "no válido", **sin distinguir** entre inexistente y revocado: la diferencia sería un canal de filtración |
| 6a | El snapshot resulta erróneo | Se revoca el certificado, se recalcula y se avisa al titular; los verificadores que ya lo consultaron ven el cambio de estado |
| — | El usuario ejerce derecho de supresión | Los certificados se revocan y la URL deja de resolver ([[CU-07 Ejercer derechos sobre datos personales]]) |
| — | Un tercero pide reputación sin certificado | No existe forma de responderle. Es una decisión de diseño, no una limitación |
| — | Certificado vencido presentado como vigente | La verificación lo marca vencido con su fecha; queda en evidencia sin acusar a nadie |

## Postcondiciones

- Todo certificado es verificable por cualquiera y reproducible desde su snapshot.
- El titular controla qué se publica, por cuánto tiempo, y puede cortarlo.

## Contrato · `openapi/transparencia.yaml`

```ts
export const EntradaCU75 = z.object({
  incluir: z.object({
    puntajeYNivel:     z.boolean().default(true),
    antiguedad:        z.boolean().default(true),
    gruposCompletados: z.boolean().default(true),
    insignias:         z.boolean().default(false),
    indicePuntualidad: z.boolean().default(false),
  }),
  vigenciaDias: z.number().int().min(7).max(180).default(90),
}).strict()

export const SalidaCU75 = z.object({
  certificadoId: z.string().uuid(),
  codigoVerificacion: z.string().max(40),
  urlPublica: z.string().url(),
  hashContenido: z.string().length(64),
  emitidoEn: z.string().datetime(),
  expiraEn:  z.string().datetime(),
  contenido: z.record(z.unknown()),
}).strict()

export const SalidaVerificarCU75 = z.object({
  valido: z.boolean(),
  estado: z.enum(['VIGENTE','VENCIDO','REVOCADO','NO_VALIDO']),
  emitidoEn: z.string().datetime().nullable(),
  expiraEn:  z.string().datetime().nullable(),
  contenido: z.record(z.unknown()).nullable(),
}).strict()

export const ErroresCU75 = {
  SIN_SNAPSHOT:        'AP-CU75-01',
  IDENTIDAD_NO_VERIFICADA:'AP-CU75-02',
  CERTIFICADO_VIGENTE: 'AP-CU75-03',
  NO_ES_TITULAR:       'AP-CU75-04',
  YA_REVOCADO:         'AP-CU75-05',
} as const
```

| Error | Cuándo se devuelve |
| --- | --- |
| `SIN_SNAPSHOT` | No hay snapshot congelado del que emitir |
| `IDENTIDAD_NO_VERIFICADA` | El titular no completó la verificación de identidad |
| `CERTIFICADO_VIGENTE` | Ya existe uno equivalente vigente; se devuelve ese |
| `NO_ES_TITULAR` | Alguien distinto del titular intenta emitirlo (`R-SEG-03`) |
| `YA_REVOCADO` | Se intenta revocar uno ya revocado |

## Descomposición atómica

| Nivel | Pieza | Responsabilidad |
| --- | --- | --- |
| Átomo | `contenidoCanonico(snapshot, seleccion)` | Estructura de orden fijo para que el hash sea reproducible; puro |
| Átomo | `hashYFirma(contenido, clave)` | Hash y firma; puro dado el material criptográfico |
| Molécula | `CertificadoRepositorio` | Persistencia, unicidad del código y vigencia |
| Molécula | `VerificadorPublico` | Resuelve el código sin filtrar existencia |
| Organismo | `CU75EmitirCertificado` | Transacción: emisión, revocación del anterior y evento |
| Página | `POST /reputacion/certificados` · `GET /verificar/:codigo` (público, sin sesión) | Traduce y delega |

## Eventos, trabajos y permisos

| Emite | Dispara | Exige |
| --- | --- | --- |
| `certificado.emitido` | Aviso al titular con la URL y el vencimiento | Sesión del titular |
| `certificado.revocado` | Invalidación inmediata de la URL | Titular o plataforma con motivo |
| `certificado.verificado` | Registro en [[verificacion_publica]] | Ninguno: es público |
| — | Trabajo que marca vencidos y avisa al titular antes de expirar | — |

## Interfaz

- **App:** *Mi reputación → Certificado*: casillas de qué incluir, una vista previa
  exacta de lo que verá el tercero, y el enlace para compartir con su vencimiento.
- **Backoffice:** consultas de verificación por certificado, para detectar códigos
  probados a fuerza bruta.

## Restricciones aplicables

`R-REP-02` · `R-REP-03` · `R-SEG-02` · `R-SEG-03` · `R-AUD-01` · `R-AUD-04`

## Evidencia que deja

[[certificado_reputacion]] · [[snapshot_reputacion]] · [[puntaje_reputacion]] ·
[[reputacion_usuario]] · [[verificacion_publica]] · `evento_dominio`

## Criterios de aceptación

```gherkin
Dado un usuario con snapshot vigente
Cuando emite un certificado incluyendo puntaje y antigüedad
Entonces la URL pública devuelve exactamente esos campos y ninguno más

Dado un código de verificación inexistente
Cuando un tercero lo consulta
Entonces la respuesta es NO_VALIDO sin distinguirla de un revocado

Dado un certificado vencido
Cuando se lo verifica
Entonces el estado es VENCIDO con su fecha de expiración

Dado un usuario que ejerce derecho de supresión
Cuando se procesa
Entonces sus certificados quedan revocados y la URL deja de resolver
```

## Ver también

[[CU-07 Ejercer derechos sobre datos personales]] · [[CU-71 Recalcular el puntaje de reputación]] · [[CU-73 Verificar la cadena de transparencia]] · [[CU-74 Otorgar y revocar una insignia]]
