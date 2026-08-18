---
tags:
  - arquitectura
  - adr
titulo: "ADR-013 — Respaldo y continuidad"
estado: aceptada
fecha: 2026-08-13
---

# ADR-013 — Respaldo y continuidad

## Contexto

AportaYa custodia dinero de terceros y guarda la evidencia con la que se responde un
reclamo o una inspección. Perder datos no es perder trabajo: es perder saldo de
personas y prueba regulatoria. CU-56 obliga además a **ejercitar la continuidad con
evidencia**, y [[Cumplimiento]] fija plazos de conservación que no se negocian por
costo de almacenamiento.

## Decisión

**Respaldo continuo con recuperación a un punto en el tiempo, y ensayo de restauración
programado como requisito, no como buena intención.**

- **pgBackRest** (respaldo completo + WAL) para operación real; `pg_dump` solo en
  etapa temprana o como export lógico complementario.
- Destino fuera del servidor de base, cifrado, con clave gestionada aparte y
  **bloqueo de objeto** donde el proveedor lo permita.
- Retención según los plazos legales de [[Cumplimiento]], no según el espacio libre.
- Credencial propia para respaldo, separada del runtime.
- **Ensayo de restauración programado**, en entorno aislado, midiendo RPO y RTO
  reales; su resultado se archiva como evidencia.
- Un ensayo fallido es un **incidente** con evento de riesgo operativo, no una tarea
  pendiente.

## Motivo

**Porque una copia de anoche no alcanza cuando lo que se pierde son transacciones.**
Con dinero en custodia, el objetivo de pérdida se mide en minutos, y eso solo lo da
el archivado continuo de WAL.

**Porque el respaldo que nadie restauró es una suposición.** Los modos de fallo
—clave perdida, archivo truncado, versión de esquema incompatible, permisos— aparecen
únicamente al restaurar. Sin ensayo, el primer intento real ocurre en la peor noche
posible.

**Porque un respaldo que el atacante puede borrar no protege del escenario que más
importa.** El cifrado y la inmutabilidad son parte del respaldo, no un extra.

**Porque el número declarado tiene que ser el número medido.** Un RPO en un documento
que el ensayo no alcanza es peor que no tenerlo: da falsa tranquilidad.

## Alternativas descartadas

| Alternativa | Por qué no |
| --- | --- |
| **Solo instantáneas del proveedor** | Cómodas y útiles como complemento, pero atan la recuperación a su granularidad y a su consola. No dan punto en el tiempo fino ni portabilidad. |
| **`pg_dump` diario como única estrategia** | RPO de hasta 24 horas de transacciones. Inaceptable con saldos de terceros. |
| **Réplica en lugar de respaldo** | Una réplica copia fielmente el `DELETE` equivocado. Es alta disponibilidad, no respaldo. |
| **Ensayo "cuando haya tiempo"** | Nunca hay tiempo. Si no está programado, no existe. |
| **Restaurar en producción para probar** | Convierte la prueba en el incidente. |

## Consecuencias

**A favor**

- Recuperación a un punto anterior a un error humano o a un incidente de seguridad.
- Evidencia de continuidad lista para una inspección, sin armar carpetas.

**En contra**

- Más infraestructura y costo de almacenamiento que una copia simple.
- Los ensayos consumen tiempo del equipo de forma recurrente. Es el precio.
- La gestión de claves de cifrado se vuelve crítica: perderlas equivale a perder los
  respaldos.

## Cómo se verifica

- [ ] El trabajo de respaldo registra inicio, fin, tamaño, resultado y suma de
      verificación, y alerta si no corrió.
- [ ] El ensayo más reciente está dentro de la periodicidad definida.
- [ ] RPO y RTO medidos en el ensayo cumplen los declarados; si no, se corrige uno de
      los dos y se deja escrito.
- [ ] Los respaldos están cifrados y la clave no vive junto a ellos.
- [ ] La retención respeta los plazos de [[Cumplimiento]].

## Ver también

[[ADR-025 Empaquetado y despliegue de los servicios]] · `respaldos-restauracion` · `observabilidad` ·
[[Entornos y despliegue]] · [[Cumplimiento]]
