---
tags:
  - arquitectura
  - adr
titulo: "ADR-036 — Android primero, iOS por paridad"
estado: aceptada
fecha: 2026-08-19
---

# ADR-036 — Android primero, iOS por paridad

> Complementa a [[ADR-004 Frontend]], que eligió Expo / React Native. Aquel ADR no
> dijo **en qué orden** se construyen las dos plataformas, y sin ese orden escrito
> cada pantalla se termina "a medias en las dos".

## Contexto

[[ADR-004 Frontend]] ya fijó el contexto real de uso: *Android de gama baja, datos
móviles intermitentes, en la calle*. El público del pasanaku en Bolivia está
mayoritariamente en Android, y las cinco máquinas del equipo pueden correr un
emulador de Android sin nada más; iOS exige hardware Apple y cuenta de desarrollador.

React Native invita a creer que las dos plataformas salen gratis a la vez. No es
cierto donde importa: biometría, notificaciones push, permisos, cámara, teclado,
gestos de retroceso, área segura y revisión de tienda se comportan distinto. Lo que
sale gratis es el **diseño**; lo que no, es el **borde con el sistema operativo**.

## Decisión

**Se desarrolla, se prueba y se da por terminada cada pantalla en Android. iOS se
atiende después, como un pase de paridad por pantalla, no como desarrollo paralelo.**

### Qué significa "copiar el diseño a iOS"

No se reescribe nada. Lo que se repite es el **pase**, y toca cuatro cosas:

| Qué se comparte tal cual | Qué se revisa en el pase de iOS |
| --- | --- |
| Componentes (átomos, moléculas, organismos) | Área segura, notch y barra inferior |
| Tokens de marca, tipografía, espaciado | Tipografía del sistema y tamaños dinámicos |
| Capa de dominio, cliente generado, estados | Gesto de retroceso y navegación por deslizamiento |
| Textos y formatos de dinero y fecha | Diálogos de permiso: cámara, notificaciones, biometría |

### Lo que cambia por plataforma va detrás de un puerto del front

Misma regla que en el backend ([[ADR-033 Puertos y adaptadores]]), aplicada a la
app. Nada de `Platform.OS` repartido por las pantallas:

```
apps/movil/src/
├── dominio/puertos/          Biometria · AvisosPush · Camara · AlmacenSeguro · Haptica
└── infraestructura/
    ├── android/              implementación Android
    └── ios/                  implementación iOS  ← el pase de paridad vive acá
```

Una pantalla que necesita `Platform.OS` para decidir algo visible está mal
compuesta: eso se resuelve en el adaptador o en el token, no en la vista.

### La definición de terminado, por pantalla

1. **Terminada en Android**: los cuatro estados (cargando, vacío, error, éxito),
   humo de la pantalla en verde sobre emulador Android, y la evidencia adjunta.
2. **Ficha de paridad iOS**: qué se vio distinto y qué se decidió. Una pantalla sin
   ficha no está lista para el pase.
3. **Terminada en iOS**: corrida en simulador iOS, con su humo en verde. **Nadie
   declara una pantalla terminada en iOS sin haberla corrido en iOS**
   (`definicion-de-terminado`).

El pase de iOS **no es una fase al final del proyecto**: es una fase por bloque de
pantallas. Se acumulan las pantallas de un flujo completo (por ejemplo, todo el
ingreso: registro, verificación, PIN) y se pasan juntas. Acumular más que eso hace
que las diferencias se descubran cuando ya hay veinte pantallas escritas encima.

### Lo que no espera al pase

Tres cosas se resuelven en Android **pensando** en iOS, porque después salen caras:

- **Push**: el token y el permiso se piden con la misma abstracción para FCM y APNs.
  El registro del dispositivo ya está modelado (`dispositivo.token_push`) y no
  distingue plataforma.
- **Almacenamiento seguro**: `expo-secure-store` cubre las dos, pero el modelo de
  bloqueo por biometría no es idéntico. Se elige el más restrictivo.
- **Actualizaciones OTA**: EAS Update se configura para las dos desde el principio;
  lo que cambia es cuándo se publica cada canal.

## Motivo

**Ahí están los usuarios y ahí está el hardware.** Toda máquina del equipo corre un
emulador de Android hoy, sin comprar nada. La primera demostración usable llega
antes, y a más gente.

**Una plataforma terminada enseña más que dos a medias.** Con Android completo se
descubre lo que de verdad falta —red intermitente, gama baja, teclado numérico,
lectura de QR con cámara mala—, y esos hallazgos cambian el diseño. Descubrir lo
mismo dos veces en paralelo cuesta el doble.

**Concentra la atención, que es el recurso escaso.** El cuello de botella del
proyecto es la revisión humana, no las máquinas. Dos plataformas en paralelo
duplican el frente de revisión sin duplicar el revisor.

**El riesgo de iOS es conocido y acotado.** Lo que suele romper —área segura,
permisos, push, revisión de tienda— es una lista corta y enumerable. Se planifica
como pase, no se descubre como sorpresa.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Las dos plataformas a la vez, pantalla por pantalla** | Duplica el frente de revisión y obliga a tener hardware Apple desde el día uno. En un equipo cuyo cuello de botella es la atención, es el peor reparto. |
| **iOS primero** | Menos usuarios, más costo de entrada, tiendas más lentas y un ciclo de corrección más largo. |
| **Solo Android, iOS más adelante "si hace falta"** | Deja el borde de plataforma sin abstraer, y cuando iOS haga falta va a estar cableado en las pantallas. Por eso el pase se agenda por bloque, no "algún día". |
| **Dos bases de código nativas** | Ya lo descartó [[ADR-004 Frontend]]: duplica el trabajo del equipo más chico sin beneficio proporcional. |

## Consecuencias

**A favor**

- Se libera algo usable antes y se aprende del uso real más temprano.
- Un solo frente de revisión por pantalla.
- El borde con el sistema operativo queda abstraído desde el principio, porque el
  pase de iOS lo exige.

**En contra, y hay que asumirlo**

- **El pase de iOS va a encontrar cosas**, y algunas van a obligar a retocar
  componentes ya "terminados" en Android. Es el costo elegido, y por eso el pase se
  hace por bloque y no al final: cuanto más tarde, más caro.
- Mientras dure el desfase, **iOS no tiene versión de prueba** para nadie de negocio.
  Hay que decirlo antes, no cuando lo pidan.
- Sostener la disciplina de no meter `Platform.OS` en las vistas exige una
  verificación automática; sin ella, la regla se afloja sola.

## Cómo se verifica

- [ ] `grep -r "Platform.OS" apps/movil/src` no devuelve nada fuera de
      `infraestructura/android/` e `infraestructura/ios/`.
- [ ] Toda pantalla del bloque tiene humo de Android en verde, con evidencia.
- [ ] Toda pantalla que entra al pase tiene su ficha de paridad escrita.
- [ ] El pase de iOS termina con la misma suite de humo corriendo en simulador iOS.
- [ ] Un puerto del front no tiene una sola implementación: si Android e iOS no
      difieren en algo, ese algo no era un puerto.

## Ver también

[[ADR-004 Frontend]] · [[ADR-033 Puertos y adaptadores]] ·
[[ADR-035 Canales por defecto]] · [[Procedimiento de desarrollo]] ·
`movil-expo` · `disenar-frontend` · `definicion-de-terminado`
