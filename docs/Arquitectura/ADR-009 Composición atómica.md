---
tags:
  - arquitectura
  - adr
titulo: "ADR-009 — Composición atómica en frontend y backend"
estado: superada por ADR-023
fecha: 2026-08-12
---

# ADR-009 — Composición atómica en frontend y backend

> [!warning] Decisión superada el 2026-08-16
> La reemplaza [[ADR-023 Composición atómica en Java]], al pasar el backend a Spring Boot con
> arquitectura de microservicios ([[ADR-014 Arquitectura de servicios]]).
> Se conserva porque el motivo por el que se decidió lo que se decidió es
> parte del expediente: quien lea el ADR nuevo tiene que poder ver qué
> cambió y por qué.

## Contexto

Doce módulos, 274 tablas y 87 casos de uso escritos por un equipo chico durante
años. Sin una regla de descomposición, el resultado conocido es siempre el mismo:
pantallas de 800 líneas, servicios que hacen ocho cosas, lógica de negocio copiada
en tres lugares y ninguna pieza que se pueda probar sola.

El sistema de diseño de AportaYa ya está definido en átomos, moléculas y organismos
(skill `disenar-frontend`). Falta decidir que **esa misma disciplina se
aplica al backend**, con el vocabulario traducido.

## Decisión

**Todo el código —frontend y backend— se divide siempre en átomos, moléculas y
organismos.** No es una convención del frontend: es la regla de composición del
proyecto.

| Nivel | Frontend | Backend | Regla que lo define |
| --- | --- | --- | --- |
| **Átomo** | `Boton`, `Campo`, `Monto`, `Etiqueta`, `Chip` | Objeto de valor y función pura: `Dinero`, `Periodo`, `calcularMora`, `siguienteTurno` | **Sin estado de dominio y sin IO.** Recibe datos, devuelve datos o píxeles |
| **Molécula** | `CampoMonto`, `FilaAporte`, `SelectorDeGrupo`, hook `useAporte` | Una pieza con **una** dependencia externa: repositorio, adaptador, política, esquema de contrato | Hace **una** cosa y la hace contra un solo colaborador. No abre transacciones |
| **Organismo** | `TablaDeAportes`, `FormularioDeAporte`, `ResumenDeBilletera` | El caso de uso: `CU21CobrarAporte` | **Orquesta** moléculas para cumplir un objetivo completo. En el backend es el único que abre transacción |
| **Plantilla / Página** | Pantalla o ruta: compone organismos, no contiene lógica | Módulo NestJS + controlador: traduce HTTP ⇄ caso de uso | Sin reglas de negocio, sin cálculos, sin SQL |

Reglas transversales:

1. **Nadie salta de nivel.** Una página no llama a un repositorio; un átomo no
   importa un servicio; una molécula no orquesta a otra molécula.
2. **Un archivo, una pieza**, con el nombre de la pieza.
3. **La dirección de dependencia es una sola**: página → organismo → molécula →
   átomo. Nunca al revés, nunca en círculo.
4. **La prueba corresponde al nivel** ([[ADR-008 Pruebas]]): átomo unitario puro,
   molécula contra Postgres real, organismo contra los criterios de aceptación.
5. **Ascender es explícito.** Cuando una pieza se usa en dos lugares, se sube de
   nivel y se documenta; no se copia.

## Motivo

**Porque la especificación ya viene descompuesta.** Un caso de uso es un organismo,
sus validaciones son moléculas y sus cálculos son átomos. Usar el mismo eje evita
tener que reinventar la estructura en cada módulo y hace que un archivo nuevo tenga
un lugar obvio.

**Porque el nivel define qué se puede probar.** Un átomo puro se prueba en
milisegundos y admite pruebas de propiedad —justo lo que necesita la aritmética de
dinero—. Un organismo se prueba contra criterios de aceptación. Sin la separación,
todo termina probándose de extremo a extremo, lento y sin localizar la causa.

**Porque el mismo vocabulario en los tres artefactos baja el costo de cambiar de
contexto.** Quien viene del backoffice entiende la estructura del backend en el
primer día: son los mismos tres sustantivos.

**Porque impone KISS por construcción.** Si una pieza no cabe en ningún nivel, está
haciendo de más. La pregunta "¿esto es átomo, molécula u organismo?" es un detector
de responsabilidades mezcladas más barato que cualquier revisión.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Atómico solo en el frontend** | Deja el backend sin regla de descomposición, que es donde está el riesgo del dinero. |
| **Capas clásicas (controller/service/repository) sin más** | No dicen nada sobre el tamaño ni sobre qué puede depender de qué; el "service" termina siendo el basurero. |
| **Arquitectura hexagonal completa con puertos y adaptadores para todo** | La dirección de dependencia se conserva igual con menos ceremonia; se usa hexagonal solo en el borde externo (proveedores). |
| **Sin regla, criterio por revisión** | Es lo que produce archivos de 800 líneas. |

## Consecuencias

**A favor**

- Piezas chicas, con nombre y prueba propia.
- La duplicación se vuelve visible: dos moléculas parecidas piden un átomo común.
- La revisión de código tiene un criterio objetivo que no depende del gusto.

**En contra**

- Más archivos. Se acepta: un archivo de más se navega; una función de 300 líneas
  no se revisa.
- Riesgo de atomizar de más. Se contiene con la regla del ADR: **se abstrae al
  tercer uso**, no al segundo, y jamás por anticipado.

## Cómo se verifica

- [ ] Ningún átomo importa algo de infraestructura (regla de dependencias en lint).
- [ ] Ninguna molécula abre transacción; ningún organismo hace SQL directo.
- [ ] Ninguna página o controlador contiene una regla de negocio.
- [ ] Todo archivo del dominio tiene su prueba al nivel que le corresponde.
- [ ] Ningún archivo de organismo supera las ~200 líneas sin justificación escrita.

## Ver también

[[Método de arquitectura]] · [[ADR-008 Pruebas]] · [[Estructura del repositorio]] · [[ADR-004 Frontend]]
