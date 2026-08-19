---
name: arquitectura-atomica
description: "Descomponer cualquier trabajo de AportaYa en átomos, moléculas y organismos, en frontend y en backend. Úsala antes de escribir el primer archivo de una funcionalidad, cuando un archivo creció demasiado, cuando no sabés dónde poner algo, o cuando aparece lógica repetida. Define los niveles, la dirección de dependencia, la prueba que corresponde a cada nivel y cuándo se abstrae."
---

# Descomponer en átomos, moléculas y organismos

Regla estructural del proyecto: **todo el código se divide siempre en tres niveles**,
en la app, en el backoffice y en el backend ([[ADR-009 Composición atómica]]).
Antes de escribir el primer archivo, se listan las piezas y su nivel.

## Los niveles

| Nivel | Definición operativa | Backend | Frontend |
| --- | --- | --- | --- |
| **Átomo** | Sin estado de dominio y **sin IO**. Entra dato, sale dato o píxeles | `Dinero`, `Periodo`, `calcularMora`, `siguienteTurno` | `Boton`, `Campo`, `Monto`, `ChipEstado` |
| **Molécula** | Hace **una** cosa contra **un** colaborador | `ObligacionRepositorio`, `PasarelaQrAdapter`, `PoliticaLimite` | `CampoMonto`, `FilaAporte`, `useAporte` |
| **Organismo** | Orquesta piezas para un objetivo completo | `CU21CobrarAporte` — única frontera transaccional | `FormularioDeAporte`, `TablaDeAportes` |
| **Página** | Compone organismos, sin lógica | `AportesController.java` | `PantallaDeAporte` |

## Cómo decidir el nivel de una pieza

```
¿Necesita base, red o reloj?
├── No  → ÁTOMO        (dominio/, atomos/)
└── Sí  → ¿Depende de UN colaborador y hace UNA cosa?
         ├── Sí → MOLÉCULA     (infraestructura/, moleculas/)
         └── No → ¿Orquesta varias piezas hacia un objetivo completo?
                  ├── Sí → ORGANISMO  (aplicacion/, organismos/)
                  └── No → está haciendo de más: partila
```

Si una pieza cabe en dos niveles, mezcla responsabilidades: se parte igual.

## El puerto no es un nivel nuevo

Un **puerto** es un átomo con una forma particular: una interfaz que declara qué
necesita el dominio del mundo de afuera. Vive en `dominio/puertos/`. Su
implementación —el **adaptador**— es una molécula, y vive en
`infraestructura/adaptadores/`. La dirección de dependencia no cambia: el dominio
declara, la infraestructura cumple ([[ADR-033 Puertos y adaptadores]]).

La pregunta que decide: **¿esto sale del proceso?** Red, disco, correo, plata, reloj
o azar → puerto. Un cálculo puro no es puerto aunque sea complicado.

## Reglas que no se negocian

| Regla | Cómo se ve al revisar |
| --- | --- |
| **Nadie salta de nivel** | Una página no llama a un repositorio; un átomo no importa infraestructura |
| **Una sola dirección** | página → organismo → molécula → átomo. Nunca al revés, nunca en círculo |
| **Un archivo, una pieza** | Y el archivo se llama como la pieza |
| **La molécula no orquesta** | No llama a otra molécula ni abre transacción |
| **El organismo no hace SQL** | Ni llama a proveedores externos: eso es outbox |
| **La página no tiene reglas** | Solo traduce HTTP ⇄ caso de uso, o ruta ⇄ organismo |

## Antes de escribir código

Declará la descomposición. Literalmente, en el mensaje o en el PR:

```
CU-21 Cobrar el aporte del período
  Organismo  CU21CobrarAporte.java             abre la transacción
  Moléculas  ObligacionRepositorio.java        lee y marca la obligación
             MovimientoRepositorio.java        inserta la contrapartida
             PasarelaQrAdapter.java            (borde, se invoca desde el worker)
  Átomos     CalculoDeAporte.java              monto + recargo, puro
             Dinero.java                       ya existe en plataforma/comun-dominio
```

Si no podés escribir esa lista, todavía no entendiste el caso de uso: volvé a
[[Método de arquitectura]], paso 2.

## Prueba por nivel

| Nivel | Prueba | Dónde |
| --- | --- | --- |
| Átomo | Unitaria pura, milisegundos; propiedad si hay aritmética | `<Atomo>Test.java` |
| Molécula | Contra Postgres real; verifica que la restricción **rechaza** | `<Molecula>Test.java` |
| Organismo | Criterios de aceptación del caso de uso, uno a uno | `CU<NN>Test.java` |

## Cuándo se abstrae

**Al tercer uso.** Dos piezas parecidas se dejan duplicadas y se espera: con dos
ejemplos no se ve el patrón, se adivina. Al tercero, el átomo común es evidente y se
extrae con nombre propio —nunca a un archivo `Utils.java`, que es un átomo sin dueño.

Nunca se abstrae por anticipado "por si mañana". Ese *mañana* llega con requisitos
distintos a los imaginados y la abstracción estorba.

## Señales de que hay que volver a descomponer

| Señal | Qué está pasando |
| --- | --- |
| Un archivo pasa de ~200 líneas (backend) o ~150 (componente) | Varios niveles mezclados |
| Hay que leer tres archivos para entender uno | Las dependencias no van en una dirección |
| La prueba necesita levantar media aplicación | El nivel probado no está aislado |
| Aparece `Utils.java`, `Helpers.java`, `Common.java` | Átomos sin nombre |
| El mismo cálculo aparece en la app y en la API | Falta un átomo en `plataforma/comun-dominio` |
| Un componente hace `fetch` | Falta la capa de dominio del cliente |

## Ver también

`glosario-dominio` · `errores-api` · `implementar-desde-boveda` · `codigo-limpio` · `back-spring` · `web-backoffice` ·
`docs/Arquitectura/ADR-009 Composición atómica.md` · `docs/Arquitectura/Método de arquitectura.md`
