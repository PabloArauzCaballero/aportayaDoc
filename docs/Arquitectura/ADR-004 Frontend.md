---
tags:
  - arquitectura
  - adr
titulo: "ADR-004 — Frontend: app del participante y backoffice"
estado: aceptada
fecha: 2026-08-12
---

# ADR-004 — Frontend: app del participante y backoffice

## Contexto

Son **dos productos**, no uno:

| Producto | Usuario | Qué hace | Contexto real de uso |
| --- | --- | --- | --- |
| **App AportaYa** | Participante y organizador | Aportar, pagar por QR, ver turno, cobrar la bolsa, ver saldo | Android de gama baja, datos móviles intermitentes, en la calle |
| **Backoffice** | Oficial de cumplimiento, soporte, contabilidad | Revisar alertas, resolver reclamos en plazo, conciliar, emitir reportes ASFI/UIF | Escritorio, pantallas densas, jornada completa |

Los casos de uso les exigen cosas distintas: la app necesita cámara para QR (CU-21),
biometría y **registro de dispositivo de confianza** (CU-04), notificaciones push
para cobro; el backoffice necesita tablas grandes, filtros, exportación y trazas.

## Decisión

**Expo / React Native** para la app y **React + Vite** para el backoffice, ambos en
TypeScript, consumiendo el **cliente generado** desde la especificación OpenAPI de
cada servicio ([[ADR-020 Contratos OpenAPI primero]]).

- App: `expo-camera` (QR), `expo-secure-store` (credenciales), `expo-local-authentication`
  (biometría), notificaciones vía FCM/APNs, actualizaciones OTA con EAS Update.
- Backoffice: TanStack Router + TanStack Query, tablas virtualizadas, exportación.
- Ambos: **composición atómica obligatoria** ([[ADR-023 Composición atómica en Java]], en su versión de frontend) y el
  sistema de diseño de la skill `disenar-frontend`.

## Motivo

**Una billetera necesita ser app.** El modelo registra dispositivo de confianza,
exige MFA y guarda credenciales: una PWA no da biometría confiable, el push en iOS
es frágil y el lector de QR en Android de gama baja es notoriamente peor. No es
preferencia estética; son requisitos de casos de uso ya escritos.

**Correcciones sin pasar por tienda.** Cuando cambia un tarifario, un umbral o un
texto obligatorio del contrato de adhesión (CU-05), la corrección tiene que llegar
en horas. EAS Update entrega JavaScript sin revisión de tienda; una app nativa pura
espera días.

**Un solo lenguaje entre los dos productos de frontend.** Un límite operativo, el
estado de una obligación o el formato de un importe se definen una vez en la
especificación del servicio, y la app y el backoffice consumen el **mismo cliente
generado** ([[ADR-020 Contratos OpenAPI primero]]).

> **Esto cambió el 2026-08-16.** Cuando se tomó esta decisión, el desempate era
> compartir lenguaje **con el backend**. Con el backend en Java
> ([[ADR-015 Lenguaje, runtime y framework]]) ese argumento ya no aplica, y la
> decisión se sostiene igual por los otros tres: la app tiene que ser app, las
> correcciones tienen que llegar sin pasar por tienda, y el backoffice no es la app
> estirada. El tipo compartido a mano se reemplaza por el generado, que es menos
> cómodo y más fiable.

**El backoffice no debe ser la app estirada.** Sus pantallas son densas y su usuario
es experto; compartir componentes de dominio sí, compartir layout no.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Flutter** | Excelente rendimiento en gama baja y buena opción si el equipo domina Dart, pero mete un tercer lenguaje y duplica los contratos. El backoffice igual quedaría en React. |
| **PWA única (Next.js)** | Más barata, pero pierde biometría, dispositivo de confianza, push confiable en iOS y presencia en tienda. Sirve para el backoffice, no para una billetera. |
| **Nativo iOS + Android separados** | Duplica el trabajo del equipo más chico del proyecto sin beneficio proporcional. |
| **React Native sin Expo** | Se pierde EAS Update y se gana mantenimiento de toolchain nativo. |

## Consecuencias

**A favor**

- Un solo lenguaje, contratos compartidos, un solo CI.
- Ciclo de corrección en horas para la capa JS.

**En contra**

- Cualquier cambio que toque código nativo (nuevo módulo de cámara, SDK de KYC) sí
  requiere build y revisión de tienda: se planifica por versión, no por parche.
- React Native rinde peor que Flutter en listas muy largas: las listas de
  movimientos se virtualizan y se paginan desde el primer día.

## Reglas de uso

| Regla | Por qué |
| --- | --- |
| Ningún importe se formatea a mano en la vista | Un solo formateador, desde el contrato ([[ADR-019 Dinero con BigDecimal]]) |
| Ninguna regla de negocio vive solo en el cliente | El cliente da buen mensaje; la base garantiza |
| Toda operación de dinero envía clave de idempotencia | El reintento del usuario no puede duplicar un aporte |
| La app asume red intermitente | Estados de carga, error y reintento explícitos en cada pantalla de dinero |
| Los colores salen de los tokens de la marca | Skill `disenar-frontend`; nunca hex sueltos |

## Cómo se verifica

- [ ] Cada pantalla de dinero tiene sus cuatro estados probados: vacío, cargando,
      error, éxito.
- [ ] Ningún componente importa `fetch` directo: todo pasa por la capa de dominio.
- [ ] Ningún hex literal fuera del archivo de tokens.
- [ ] La app funciona con red apagada mostrando el último estado y sin permitir
      operaciones que requieran confirmación del servidor.

## Ver también

[[ADR-023 Composición atómica en Java]] · [[ADR-020 Contratos OpenAPI primero]] · [[Stack]] · [[_CasosDeUso]]
