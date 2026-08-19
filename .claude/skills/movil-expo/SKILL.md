---
name: movil-expo
description: "Construir la app móvil de AportaYa con Expo y React Native: composición atómica, capa de dominio, cámara para QR, biometría y dispositivo de confianza, almacenamiento seguro, estados obligatorios, red intermitente y actualizaciones OTA. Úsala al crear o modificar cualquier pantalla o componente de apps/movil."
---

# App móvil con Expo

La app del participante: aportar, pagar por QR, ver turno, cobrar la bolsa, ver
saldo. Contexto real: Android de gama baja, datos móviles intermitentes, en la calle.

El **diseño visual** (tokens, átomos, componentes móviles) lo manda la skill
`disenar-frontend`; esta skill manda la **estructura y el comportamiento**.

## Estructura

```
apps/movil/src/
├── atomos/       Boton, Campo, Monto, ChipEstado, TecladoNumerico
├── moleculas/    CampoMonto, FilaAporte, SelectorDeGrupo, useAporte
├── organismos/   FormularioDeAporte, ResumenDeBilletera, ListaDeMovimientos
├── pantallas/    composición de organismos + ruta, sin lógica
├── dominio/      un cliente por caso de uso, tipado desde openapi/ del servicio
│   └── puertos/  Biometria · AvisosPush · Camara · AlmacenSeguro · Haptica
├── infraestructura/
│   ├── android/  implementación Android — se escribe primero
│   └── ios/      implementación iOS — el pase de paridad vive acá
└── tokens/       único lugar con valores de color, espacio y tipografía
```

**Android primero, iOS por pase** ([[ADR-036 Android primero]]). Una pantalla se
termina en Android —cuatro estados, humo en verde sobre emulador— y recién ahí se
escribe su ficha de paridad. `Platform.OS` **no aparece en una vista**: lo que difiere
por plataforma se resuelve en el adaptador o en el token.

Ningún componente hace `fetch`: la red vive en `dominio/`, y las pantallas la
consumen por hooks ([[ADR-009 Composición atómica]]).

## Los estados no son opcionales

Toda pantalla con datos implementa **cargando, vacío, error y éxito**; toda pantalla
que produce un efecto suma **enviando** y **sin conexión**.

| Estado | Qué se ve |
| --- | --- |
| Cargando | Indicación clara, sin saltos de layout |
| Vacío | Por qué no hay nada y qué hacer |
| Error | Qué pasó en lenguaje humano + reintento |
| Enviando | Botón deshabilitado con progreso |
| Sin conexión | Aviso explícito; se bloquean las operaciones que requieren confirmación |

## Dinero y doble envío

- Toda operación con efecto envía **clave de idempotencia** generada en el cliente y
  **reutilizada** en el reintento. El usuario en mala señal va a tocar dos veces: eso
  no puede duplicar un aporte.
- Los importes se muestran con el átomo `Monto`; **el cliente nunca recalcula** una
  comisión ni un total: los pide cotizados (CU-30) o los recibe en la respuesta
  (`dinero-decimal`).
- Confirmación explícita antes de cualquier operación irreversible, con el monto y el
  destinatario a la vista.

## Capacidades nativas

| Necesidad | Cómo | Caso de uso |
| --- | --- | --- |
| Escanear QR | `expo-camera`, con permiso explicado antes de pedirlo | CU-21 |
| Guardar credenciales | `expo-secure-store`; nunca almacenamiento plano | CU-04 |
| Biometría y MFA | `expo-local-authentication` | CU-04 |
| Dispositivo de confianza | Identificador estable + registro en el servidor | CU-04 |
| Notificaciones | FCM/APNs; el contenido no revela montos ni datos personales | Módulo 05 |
| Bandeja de avisos | Lee `bandeja_entrada`, que es la fuente; el push es solo el aviso ([[ADR-035 Canales por defecto]]) | Módulo 05 |
| Subir un archivo | `multipart` al endpoint del servicio dueño; vuelve una **clave de objeto**, no una URL ([[ADR-034 Almacenamiento de archivos]]) | CU-02, CU-22 |

Si se deniega un permiso, la app explica qué se pierde y ofrece una alternativa
(ingresar el código del QR a mano), en vez de quedarse en una pantalla muerta.

## Red intermitente

- Reintento manual visible en cada pantalla de dinero; nada de reintentos automáticos
  silenciosos sobre operaciones con efecto.
- Caché de lectura para que la app abra mostrando el último estado conocido, con
  marca de cuándo se actualizó.
- Timeouts cortos y mensajes concretos: "no pudimos confirmar tu aporte, revisá el
  estado antes de volver a pagar" es correcto; "error de red" no.
- Una operación aceptada pero no confirmada (`202`) se muestra como **pendiente**, no
  como exitosa.

## Rendimiento en gama baja

- Listas de movimientos **virtualizadas y paginadas** desde el primer día.
- Imágenes dimensionadas; nada que no se vea se descarga.
- Sin animaciones que bloqueen el hilo de JS en pantallas de dinero.
- Se mide en un dispositivo real de gama baja, no en el simulador.

## Actualizaciones

- **EAS Update** para la capa JavaScript: un texto obligatorio, un tarifario o un
  mensaje de cumplimiento se corrige en horas.
- Cualquier cambio que toque código nativo (SDK de KYC, módulo de cámara) requiere
  build y revisión de tienda: se planifica por versión.
- La app verifica compatibilidad de contrato con la API al iniciar; si es
  incompatible, pide actualizar en vez de fallar a mitad de un pago.

## Accesibilidad

Etiquetas asociadas, foco y estados anunciados, áreas táctiles cómodas, contraste
suficiente y soporte de tipografía ampliada. Una billetera la usa gente de todas las
edades; esto no es opcional.

## Antipatrones

- `fetch` dentro de un componente.
- Hex o espaciados literales fuera de `tokens/`.
- Reglas de negocio que solo existen en el cliente.
- Recalcular importes para mostrarlos.
- Guardar el token en almacenamiento plano.
- Pantalla de 400 líneas: son varios organismos sin extraer.

## Ver también

`errores-api` · `glosario-dominio` · `disenar-frontend` · `arquitectura-atomica` · `contratos-api` · `dinero-decimal` ·
`docs/Arquitectura/ADR-004 Frontend.md` · `docs/Arquitectura/Prompts/Prompt de frontend.md`
