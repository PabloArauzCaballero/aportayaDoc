---
tags:
  - moc
  - plan
  - frontend
  - estandar
titulo: "Estándar de ejecución del frontend"
fecha: 2026-08-14
aplica_a: las 13 fases del frontend, sin excepción
---

# Estándar de ejecución del frontend

> **Anexo de [[00b Estándar de ejecución · código limpio, pruebas y calidad]].**
> Todo lo que dice el estándar general —regla cero, KISS, nombres, funciones,
> errores, revisión por riesgo, definición de terminado— **también rige acá**. Este
> documento agrega solo lo que es propio de la interfaz.

Fuentes que hace operativas: [[Prompt de frontend]] · skills `disenar-frontend`,
`movil-expo`, `web-backoffice`, `arquitectura-atomica`, `glosario-dominio`,
`errores-api`, `dinero-decimal`, `contratos-api`.

---

## 1 · Regla cero aplicada a la interfaz

**No se inventa una pantalla.** Cada caso de uso trae su sección **Interfaz** con lo
que la app y el backoffice deben mostrar. Se lee y se implementa eso.

| Situación | Qué se hace |
| --- | --- |
| El CU dice qué pantalla es | Se implementa **eso**, con esas palabras |
| El CU dice «sin pantalla» | **No se hace pantalla.** Que exista una tabla no significa que tenga UI |
| Falta un texto, un estado o un campo | Se busca en el CU, en `glosario-dominio` y en la identidad de marca. Si no está y es crítico, **se pregunta** |
| Falta un ícono, una animación, un matiz visual | Se decide y se **declara el supuesto** |

**No se inventan datos de ejemplo con apariencia real.** Nada de nombres de personas
verosímiles, cédulas, montos que parezcan reales o capturas que simulen una cuenta
existente. Los fixtures salen de `seeders/dev/` o son obviamente ficticios.

---

## 2 · Los cuatro estados, sin excepción

Toda pantalla o sección que dependa de datos remotos implementa **los cuatro**, y en
flujos con efecto suma dos más:

| Estado | Qué debe verse | Componente |
| --- | --- | --- |
| **Cargando** | Indicación clara, **sin salto de layout** | `Skeleton`, `Spinner` |
| **Vacío** | Por qué no hay nada **y qué hacer** | `EstadoVacio` |
| **Error** | Qué pasó en lenguaje humano **y un botón de reintento** | `EstadoError` |
| **Éxito** | El dato, con formato y unidad explícita | — |
| **Enviando** | Botón deshabilitado con progreso | `Boton` estado `cargando` |
| **Sin conexión** | Último estado conocido, operaciones bloqueadas | `EstadoError` variante `sinConexion` |

> **En el backoffice el estado vacío importa el doble:** «no hay alertas» y «el filtro
> no devolvió nada» son mensajes **distintos** y se distinguen. Y una pantalla vacía
> **por permiso** lo dice explícitamente —«no tenés acceso a esto»—, nunca una tabla
> vacía que parece un error de datos (`web-backoffice`).

> **Un `EstadoVacio` que dice «No hay datos» no cumple.** Tiene que decir por qué está
> vacío y qué puede hacer la persona: *«Todavía no tenés movimientos. Cuando recargues
> saldo van a aparecer acá.»*

### El `202` no es éxito

Una operación aceptada pero no confirmada se muestra como **pendiente**, con su estado
actualizándose. Mostrarla como exitosa es decirle a la persona que su plata se movió
cuando todavía no se sabe (`movil-expo`, `errores-api`).

### Red intermitente — las cuatro reglas

| Regla | Cómo se ve |
| --- | --- |
| **Nada de reintentos automáticos silenciosos** sobre operaciones con efecto | Reintento **manual y visible** en cada pantalla de dinero |
| Caché de lectura con **marca de cuándo se actualizó** | La app abre mostrando el último estado conocido, fechado |
| Timeouts cortos, **mensajes concretos** | ✅ «No pudimos confirmar tu aporte, revisá el estado antes de volver a pagar» · ❌ «Error de red» |
| Compatibilidad de contrato **verificada al iniciar** | Si es incompatible, pide actualizar **en vez de fallar a mitad de un pago** |

### Permiso nativo denegado

Si la persona deniega la cámara, la app **explica qué se pierde y ofrece una
alternativa** —ingresar el código del QR a mano—, en vez de quedarse en una pantalla
muerta (`movil-expo`).

### Notificaciones

**El contenido de una notificación no revela montos ni datos personales.** Aparece en
la pantalla de bloqueo de un teléfono que puede estar sobre una mesa.

---

## 3 · Diseño: los tokens son la única fuente

De la skill `disenar-frontend`, que manda sobre cualquier criterio estético:

> **Verde = estructura. Naranja = acción.** El naranja `#E5852B` se reserva para el
> **único** llamado a la acción principal de cada pantalla. Nunca dos naranjas
> compitiendo.

| Regla | Valor |
| --- | --- |
| Espaciado | Múltiplos de 4: `--s1 4` … `--s7 48` |
| Radio | `--r-sm 8` · `--r-md 12` · `--r-lg 16` · `--r-xl 24` · `--r-pill 999` |
| Tipografía | `Poppins` display/cifras · `Inter` cuerpo/UI |
| **Dinero** | `tabular-nums`, prefijo `Bs`, coma decimal → **`Bs 1.240,00`** |
| Táctil móvil | Área mínima **44×44 px** |
| Tema | `usarTema()`; claro y oscuro **redefinen solo tokens** |

**Cero literales.** Un `#fff`, un `padding: 13px` o un `font-family` dentro de un
componente es un fallo de lint, no una preferencia.

---

## 4 · La voz de la interfaz

De la identidad de marca: **cercana al hablar, impecable con el dinero.**

| Sí | No |
| --- | --- |
| «Listo, tu aporte de Bs 250 quedó guardado.» | «Transacción procesada exitosamente.» |
| «Te toca en 2 turnos. Te avisamos.» | «Posición en cola: 3.» |
| «Confirmar aporte de Bs 250» | «Aceptar» |
| «No pudimos cobrar. Revisá tu saldo e intentá de nuevo.» | «Error 409.» |
| «Tu grupo», «tu turno», «tu aporte» | «El usuario», «la entidad» |

**El vocabulario es el de `glosario-dominio`**: grupo, cupo, turno, período, aporte,
entrega. Nunca `member`, `payment`, `round`. Si el modelo dice `obligacion_aporte`, la
pantalla dice «aporte», no «cuota pendiente de pago».

**Los errores se traducen.** El usuario ve el mensaje del código `AP-CU<NN>-<nn>` en
lenguaje llano; nunca el código, nunca el texto de PostgreSQL, nunca «error
inesperado» a secas.

---

## 5 · Dinero en el cliente

| Regla | Por qué |
| --- | --- |
| **El cliente no calcula importes.** Muestra lo que el servidor devolvió | Invariante 5 del backend: la exactitud es cumplimiento |
| Todo importe pasa por el átomo **`Monto`** | Un solo formateador, un solo redondeo |
| Los importes llegan y viajan como ***string*** | `numeric` se lee como string de punta a punta |
| Prohibido `toFixed`, `Number()`, `parseFloat` sobre importes | Regla de lint |
| Una comisión se muestra como **línea con nombre**, nunca como descuento anónimo | CU-31, transparencia ASFI |
| El costo total **con impuestos** se ve **antes** de confirmar | CU-30, `R-CON-07` |

---

## 5b · Tablas del backoffice — el organismo central

Casi todo el backoffice es una tabla con filtros. Reglas fijas de `web-backoffice`:

| Aspecto | Regla |
| --- | --- |
| Paginación | **Del servidor, siempre.** Nada de traer todo y filtrar en el cliente |
| Orden y filtros | **Por lista blanca** acordada con la API |
| **Estado en la URL** | Un oficial tiene que poder **pegar el enlace de lo que está mirando en un expediente** |
| Virtualización | Para listas largas; el DOM no crece sin límite |
| Columnas de dinero | **Alineadas a la derecha**, con el átomo `Monto`, **moneda visible** |
| Fechas | **Con zona horaria explícita**; nada ambiguo en un expediente |
| Exportación | Del servidor, con los mismos filtros, y **queda registrada: quién exportó qué y cuándo** |
| Selección múltiple | **Solo si existe una acción masiva real**, con confirmación que **enumera lo afectado** |

### Lo que distingue al backoffice de un CRUD

- **El plazo se muestra siempre**, y el vencimiento se destaca **antes** de vencer.
- **La evidencia se ve**: toda pantalla de expediente muestra la bitácora —quién hizo
  qué, cuándo, con qué resultado.
- **Nada se edita.** Corregir es registrar un movimiento o una decisión nueva, con
  motivo obligatorio. **Si la interfaz sugiere «editar», está mintiendo sobre el
  modelo.**
- **Motivo obligatorio** en toda acción con consecuencia: bloquear saldo, rechazar,
  elevar, devolver comisión.
- **Doble confirmación** en lo irreversible, enumerando exactamente qué va a pasar.
- La **segregación de funciones se respeta en la interfaz**: quien registra no aprueba.
  Si la interfaz permite ambas cosas al mismo usuario, **es un defecto**.
- Las pantallas de conciliación y reportes **leen de la réplica**: son consultas pesadas.

---

## 6 · Formularios y efectos

- Validación con **los tipos generados desde el OpenAPI del servicio**. No se reescribe una regla.
- Error **por campo, en el campo**, diciendo cómo corregir.
- **Clave de idempotencia**: se genera al abrir el formulario y se reenvía igual en el
  reintento. Nunca se regenera al reintentar.
- El botón que envía **se deshabilita y muestra progreso**. El doble clic no puede
  duplicar un efecto.
- Deshabilitar el botón **no es** la validación: se explica qué falta.
- Confirmación explícita para lo irreversible, diciendo **qué** se va a hacer y **por
  cuánto**.
- **Los formularios largos guardan borrador local** (reportes, expedientes): una
  sesión caída no puede costar media hora de trabajo.

---

## 7 · Accesibilidad — bloqueante, no deseable

| Control | Umbral |
| --- | --- |
| Contraste | **AA ≥ 4.5:1** en texto; 3:1 en componentes |
| Foco | Visible siempre (`outline 3px --g300`); nunca `outline: none` sin reemplazo |
| Teclado | Todo el flujo, sin trampas de foco |
| Semántica | Un botón es un `<button>`. Nada de `div` con `onClick` |
| Etiquetas | Todo control con etiqueta asociada; error anunciado (`aria-live`) |
| Tipografía ampliada | La interfaz soporta 200 % sin romperse |
| Movimiento | `prefers-reduced-motion` respetado |
| Móvil | Área táctil ≥ 44 px; sin desplazamiento horizontal del cuerpo |

`jest-axe` en las pruebas de pantalla y `@axe-core/playwright` en E2E, **como error**.

---

## 8 · Rendimiento

- **Listas largas: virtualización y paginación desde el primer día.** Los movimientos
  de billetera y las tablas del backoffice no se renderizan enteros nunca.
- Caché de servidor con invalidación explícita. Nada de recargar todo tras cada acción.
- Imágenes dimensionadas y en formato moderno; nada de descargar lo que no se ve.
- **Se optimiza con medición**, no por intuición.
- La app asume **red intermitente**: reintento manual claro y estado visible.

---

## 9 · Seguridad en el cliente

- Credenciales en **almacenamiento seguro de la plataforma** (`expo-secure-store`),
  jamás en `AsyncStorage` plano ni en `localStorage`.
- Ningún dato sensible en la URL, en el estado global ni en un log.
- Los permisos se muestran u ocultan **por comodidad**; se verifican en el servidor.
- El número de cuenta bancaria se muestra **enmascarado** (últimos cuatro dígitos).
- Ningún token, PIN ni documento aparece en una traza de cliente.
- **Sin capturas de pantalla** en las vistas con saldo y datos personales, en móvil.

---

## 10 · Antes de abrir el PR

- [ ] Componentes declarados **por nivel** en la descripción del PR
- [ ] Los cuatro estados existen en cada pantalla con datos
- [ ] Cero literales de diseño fuera de tokens
- [ ] Ninguna llamada de red dentro de un componente
- [ ] Los tipos vienen del contrato; los mocks validan contra su esquema OpenAPI
- [ ] Un solo botón naranja por pantalla
- [ ] Dinero con `tabular-nums` y formato `Bs 1.240,00`
- [ ] Teclado, foco y contraste verificados
- [ ] Claro y oscuro probados
- [ ] Prueba de doble envío en todo flujo con efecto
- [ ] Copy en voz de marca y vocabulario del `glosario-dominio`
- [ ] Ningún componente pasa de ~150 líneas (una pantalla de 400 son varios organismos
      sin extraer)
- [ ] `yarn lint && yarn typecheck && yarn test:front && yarn test:a11y` en verde

---

## 11 · Revisión de frontend — orden por riesgo

```
1. ¿Qué caso de uso implementa? ¿Coincide con su sección Interfaz?
2. Dinero: formato, cálculo en el cliente, comisión visible
3. Efectos: idempotencia, doble envío, confirmación
4. Los cuatro estados
5. Accesibilidad: teclado, foco, contraste, semántica
6. Composición: niveles y dirección de dependencia
7. Tokens y voz de marca
8. Estilo → lo resolvió la herramienta
```

### Se rechaza sin discusión

| Hallazgo | Por qué |
| --- | --- |
| `fetch` dentro de un componente | Invariante 1 |
| Un importe calculado o formateado a mano | La exactitud es cumplimiento |
| Un hex o un `px` suelto | El sistema de diseño deja de existir al segundo |
| Una pantalla de datos sin sus cuatro estados | La mitad de la interfaz no existe |
| Un flujo con efecto sin bloqueo de doble envío | Duplica dinero |
| Un tipo reescrito a mano que ya está en el contrato | Diverge en la cuarta semana |
| `div` con `onClick` | Inaccesible |
| Un mock que no valida contra su esquema OpenAPI | La pantalla ya está rota y no lo sabe |
| Traer diez mil filas para filtrar en el navegador | La tabla se pagina en el servidor |
| Un botón «editar» sobre algo append-only | La interfaz miente sobre el modelo |
| Acción masiva sin confirmación que enumere lo afectado | Irreversible a ciegas |
| Exportar sin registrar quién exportó | `R-SEG-02`, CU-58 |
| Una notificación que muestra el monto | Aparece en la pantalla de bloqueo |
| Un `202` mostrado como éxito | Dice que la plata se movió cuando no se sabe |
| Reutilizar el layout de la app para pantallas densas | ADR-004: el backoffice no es la app estirada |
| Una regla de negocio que solo vive en el cliente | La garantía está en el lugar equivocado |
| Datos de ejemplo con apariencia de reales | Regla cero |

## Ver también

[[00b Estándar de ejecución · código limpio, pruebas y calidad]] · [[00c Recetario · implementar un caso de uso]] · [[10 Plan maestro del frontend]] · [[16 Carriles de frontend]] · [[Prompt de frontend]]
