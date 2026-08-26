---
titulo: AportaYa — Índice de vistas
tipo: indice
proyecto: AportaYa
version: 1
estado: vivo
fecha: 2026-08-25
tags: [indice, maqueta, sistema-de-diseno, marca]
---

# Vistas

> [!info] Índice navegable
> [index.html](index.html) — abrir en el navegador. Lista las dos maquetas, el sistema de
> diseño y la identidad, y contesta dónde se ve cada una de las siete preguntas del apunte.

Todo lo que en este proyecto se puede **abrir y mirar**. No hay build ni dependencias:
son archivos HTML sueltos que comparten `Sistema-Diseno/estilos.css`.

## Qué hay

| Vista | Qué muestra | Cuánto |
| --- | --- | --- |
| [[AportaYa-Maqueta]] | El recorrido del participante y del operador, con cada llamada al backend simulada | 43 pantallas de app · 28 de backoffice |
| [[Maqueta-Crecimiento/README\|Crecimiento y alianzas]] | Mercado de turnos, alianzas con vales e inversión, en tres superficies | 6 · 4 · 3 |
| [[Sistema-Diseno/README\|Sistema de Diseño]] | Tokens, átomos, moléculas, organismos y piezas móviles | 5 catálogos |
| [[AportaYa-Identidad]] | Nombre, promesa, tono de voz y usos del logo | 3 documentos |

## Los cinco recorridos de la demo

Se caminan enteros en [[AportaYa-Maqueta]], y cada paso tiene efecto real sobre el estado
simulado: el saldo se mueve, el turno cambia, el cupo se ocupa.

| Recorrido | Por dónde se entra |
| --- | --- |
| **Vender el turno** | Grupos → La Ramada → *Tu turno y el mercado* → *Ceder mi turno* |
| **Aceptar la permuta de otro** | La misma pantalla → *Ver las ofertas del grupo* |
| **Aceptar a quien quiere entrar** | Grupos → Compañeras del taller → *N esperan que las aceptes* |
| **Entrar siendo cuenta nueva** | *Reiniciar demo* → crear cuenta → escanear QR → *Simular que Rosa te acepta* |
| **El descuento** | Bono y vale con el alta · *Vales* en accesos rápidos · *Cobrar la bolsa* para el descuento de comisión |

> [!note] Las tres formas del descuento no son intercambiables
> El **bono en efectivo** obliga a depositar el respaldo en la cuenta de custodia. El
> **descuento de comisión** sale del margen de la empresa. El **vale** lo paga el comercio
> aliado y no toca la custodia en absoluto. Por eso el vale puede ser mucho más grande que
> cualquier bono que la plataforma pudiera regalar.

## Las siete preguntas del apunte y dónde se contestan

| Pregunta | Dónde se ve | De dónde sale el dato |
| --- | --- | --- |
| El organizador acepta a quien entra con el QR | App · *Tu pedido de cupo* · *Quién quiere entrar* | [[CU-68 Postular a un grupo y ser emparejado]] · `ck_solicitud_ingreso_resuelta` |
| Qué se pide para ser organizador, y quién lo acepta | App · *Organizar un grupo* · Backoffice · *Habilitaciones* | [[CU-90 Postular a organizador y habilitarse]] · `seeders/minimos/17` |
| El QR se lee con la cámara al añadir un grupo | App · *Canjear invitación*, modo escanear | [[CU-69 Invitar a un contacto y registrar sus referencias]] · `R-GRP-15` |
| Problemas legales y regulación con la ASFI | Backoffice · *Licencia ASFI* | [[Cumplimiento]] §1.1 · Res. ASFI/540/2025 |
| Qué se hace si alguien no puede o no quiere pagar | App · *No voy a poder pagar* | [[CU-23 Cubrir un incumplimiento con el fondo]] · [[CU-25 Declarar el incumplimiento con descargo y evidencia]] · `seeders/minimos/18` |
| Cuánto de bono al subir de nivel | App · *Tu nivel* | No existía · se apoya en `COM_ENTREGA` y en el encaje de `cuenta_custodia` |
| Cómo se gestiona el soporte | App · *Ayuda* · *Hacer un reclamo* · Backoffice · *Reclamos* | [[CU-52 Atender un reclamo en plazo]] · [[Cumplimiento]] §1.3 |

> [!warning] Ninguna cifra de las maquetas es un dato de negocio
> Los montos, nombres y grupos son de ejemplo. Lo único que sí sale del proyecto son los
> umbrales marcados con su archivo de origen: requisitos de habilitación, etapas de
> cobranza, plazos de descargo y de reclamo, y la comisión de la entrega.

## Por qué las vistas viven en `docs/` y no en `apps/`

`apps/` está en `.gitignore` a propósito, y un HTML suelto ahí haría parecer que el frontend
arrancó. Estas vistas son **documentación**: la especificación visual de lo que
[[Flujo de pantallas · app del participante]] y
[[Flujo de pantallas · backoffice administrador]] describen en prosa.

Relacionado: [[Index|Bóveda del modelo]] · [[Restricciones]] · [[Cumplimiento]]
