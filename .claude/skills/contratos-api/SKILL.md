---
name: contratos-api
description: "Escribir el contrato de un caso de uso de AportaYa como operación OpenAPI 3.1 en el servicio que lo expone, con sus códigos de error e idempotencia, y generar de ahí el servidor y los clientes. Úsala antes de implementar cualquier endpoint, al cambiar una entrada o salida de la API, o cuando el cliente y el servidor no coinciden. El contrato se escribe antes que la implementación."
---

# Escribir el contrato de un caso de uso

Una operación por caso de uso, en `servicios/<servicio>/src/main/resources/openapi/<servicio>.yaml`,
consumida por el propio servicio, por los otros trece, por la app y por el backoffice
([[ADR-020 Contratos OpenAPI primero]]).

> **Se escribe antes que la implementación, y no es formalismo.** Es lo que permite
> que otro carril genere el cliente y programe contra vos sin esperar a que termines.
> Con catorce servicios y cinco máquinas, el contrato es lo que sostiene la
> concurrencia.

## La especificación es la fuente; todo lo demás se genera

```
openapi/<servicio>.yaml          ← lo escribe una persona
        ↓ openapi-generator
        ├── interfaz de controlador (Spring)  → el servicio la IMPLEMENTA
        ├── cliente Java                       → lo usan los otros servicios
        └── cliente TypeScript                 → lo usan la app y el backoffice
```

**El controlador implementa la interfaz generada.** Si el contrato cambia y el
controlador no, **no compila**: la divergencia deja de ser posible en lugar de ser
detectada.

## Anatomía

```yaml
# openapi/aportes.yaml
paths:
  /aportes/{obligacionId}/cobro:
    post:
      operationId: cobrarAporte            # ← CU-21
      parameters:
        - name: Idempotency-Key
          in: header
          required: true                    # toda operación con efecto
          schema: { type: string, format: uuid }
      requestBody:
        content:
          application/json:
            schema: { $ref: '#/components/schemas/SolicitudCobro' }
      responses:
        '200': { $ref: '#/components/responses/Cobro' }
        '422': { $ref: '#/components/responses/ReglaDeNegocio' }   # AP-CU21-01..04

components:
  schemas:
    SolicitudCobro:
      type: object
      additionalProperties: false           # ← un campo de más es error, no algo que se ignora
      required: [monto, moneda, medio]
      properties:
        monto:  { type: string, pattern: '^-?\d+\.\d{2}$' }   # CADENA, nunca number
        moneda: { type: string, enum: [BOB, USD] }
        medio:  { type: string, enum: [qr, saldo] }
```

## Reglas

| Regla | Por qué |
| --- | --- |
| **Una operación por caso de uso**, con `operationId` que cita el CU | Trazabilidad especificación → contrato → código sin herramientas |
| **`additionalProperties: false` siempre** | Lo desconocido se rechaza; un campo de más suele ser un cliente desactualizado |
| **Un archivo por servicio, propiedad de su carril** | Un archivo único generaba un conflicto de merge por PR, y se «resolvía» regenerando |
| **Toda operación con efecto exige `Idempotency-Key`** | Se valida antes de escribir; el reintento devuelve la misma respuesta |
| **Los importes son cadena** con patrón de dos decimales | `dinero-decimal`; jamás `type: number` para dinero |
| **Errores con código, no solo texto** | `AP-CU<NN>-<nn>`, uno por criterio de aceptación del caso de uso |
| **Toda regla que proteja dinero cita su restricción** | Si no existe `R-XXX-nn`, la garantía está en el lugar equivocado |
| **Fechas en ISO-8601 con zona** | El modelo usa `TIMESTAMPTZ`; nada de fechas sin zona |
| **Los ejemplos son los del `CU-NN`** | El CI valida que la especificación acepte los ejemplos de la bóveda |

## La validación

**Bean Validation** (`jakarta.validation`) sobre los tipos generados. No se escriben
DTO a mano que dupliquen el esquema: si aparece una clase que repite lo que el
generador ya produjo, sobra.

## Qué NO va en el contrato

- Reglas que solo el servidor puede evaluar con datos que el cliente no tiene
  (límites acumulados, estado de KYC, encaje). El cliente pregunta; no adivina.
- Umbrales regulatorios como constantes: vienen del catálogo, con vigencia.
- Lógica de presentación: el contrato define datos, no cómo se muestran.

## Errores: cómo se responden

| Situación | HTTP | Cuerpo |
| --- | :-: | --- |
| Entrada inválida por el contrato | `400` | Lista de campos con mensaje |
| Regla de negocio de la aplicación | `422` | `{ codigo: 'AP-CU21-02', mensaje }` |
| Sin autenticar | `401` | `AP-SEG-01` |
| Sin permiso o fuera de política de fila | `403` o resultado vacío | Sin detalles internos |
| Restricción de la base rechaza | `409` | `{ codigo: 'R-LIM-02', mensaje }` traducido |
| Clave de idempotencia repetida | `200` | La respuesta original, íntegra |
| Proveedor externo indisponible | `202` | Aceptado, se completa por la cola |
| **Otro servicio indisponible** | `503` o `202` según si la respuesta era imprescindible | Sin detalles internos |

El mensaje al usuario **nunca** contiene SQL, nombres de tabla ni trazas.

## Versionado — y qué se puede romper

- Ruta versionada: `/api/v1/...`.
- **Aditivo es libre:** un campo opcional nuevo o una operación nueva no rompe a nadie.
- **Incompatible es coordinado:** cambiar un tipo o quitar un campo rompe a quien ya
  generó el cliente. Se hace en dos pasos —la API acepta ambas formas, los clientes
  migran, después se retira la vieja— y nunca en un solo despliegue.
- **La prueba de contrato falla en el CI del que rompe**, no en el del que sufre. Es
  la diferencia entre enterarse hoy y enterarse en integración dentro de tres semanas.

## Cómo lo consume cada lado

| Artefacto | Uso |
| --- | --- |
| El servicio dueño | **Implementa la interfaz generada.** Sin `@GetMapping` sueltos |
| Los otros servicios | Generan el cliente Java y programan contra él, con timeout y cortacircuitos |
| `apps/movil` / `apps/backoffice` | Generan el cliente TypeScript. **No se edita a mano**: el CI falla si hay diff |
| Pruebas | Spring Cloud Contract genera el doble del productor desde el mismo contrato |

Recordá el límite: la validación del contrato **da buen mensaje**; la garantía real
está en [[Restricciones]]. Un contrato nunca reemplaza una restricción.

## Antes de dar por terminado

- [ ] La operación existe en `openapi/<servicio>.yaml` con entrada, salida y errores
- [ ] El controlador **implementa la interfaz generada**; ningún mapeo suelto
- [ ] Todo esquema de entrada declara `additionalProperties: false`
- [ ] Todos los criterios de aceptación del caso de uso tienen su código de error
- [ ] Toda regla del contrato que proteja dinero cita su `R-XXX-nn`
- [ ] La operación con efecto exige `Idempotency-Key`
- [ ] Los ejemplos son los de la bóveda y validan
- [ ] El cliente TypeScript regenerado no produce diff

## Ver también

`errores-api` · `idempotencia-reintentos` · `caso-de-uso` · `back-spring` ·
`servicios-y-sagas` · `dinero-decimal` · `pruebas-cu` ·
`docs/Arquitectura/ADR-020 Contratos OpenAPI primero.md`
