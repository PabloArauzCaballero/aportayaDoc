---
tags:
  - moc
  - modulo/10-billetera-custodia-y-dinero-electronico
modulo: "10 — Billetera, Custodia y Dinero Electrónico"
relaciones_fk: 61
---

# 10 — Billetera, Custodia y Dinero Electrónico · relaciones

Las **61 claves foráneas** que salen de las tablas de este módulo.

[[_Relaciones|← Todas las relaciones]] · [[Index]]

| Relación | Destino | Cruza | Opcional |
| --- | --- | :-: | :-: |
| [[bloqueo_saldo.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[bloqueo_saldo.levantada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[bloqueo_saldo.retencion_id → retencion_saldo]] | [[retencion_saldo]] | — | sí |
| [[certificado_saldo.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[certificado_saldo.solicitado_por → usuario]] | [[usuario]] | ↗ 01 | no |
| [[conciliacion_custodia.cierre_diario_id → cierre_diario]] | [[cierre_diario]] | ↗ 03 | sí |
| [[conciliacion_custodia.cuenta_custodia_id → cuenta_custodia]] | [[cuenta_custodia]] | — | no |
| [[conciliacion_custodia.ejecutada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[consumo_limite.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[consumo_limite.limite_id → limite_operativo_billetera]] | [[limite_operativo_billetera]] | — | no |
| [[cuenta_billetera.cuenta_contable_id → cuenta_contable]] | [[cuenta_contable]] | ↗ 03 | sí |
| [[cuenta_billetera.grupo_id → grupo]] | [[grupo]] | ↗ 02 | sí |
| [[cuenta_billetera.politica_billetera_id → politica_billetera]] | [[politica_billetera]] | — | sí |
| [[cuenta_billetera.usuario_id → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[descuadre_custodia.conciliacion_custodia_id → conciliacion_custodia]] | [[conciliacion_custodia]] | — | no |
| [[descuadre_custodia.incidente_operativo_id → incidente_operativo]] | [[incidente_operativo]] | ↗ 09 | sí |
| [[descuadre_custodia.resuelto_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[estado_cuenta_billetera.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[evaluacion_antifraude.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[evaluacion_antifraude.revisada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[evaluacion_antifraude.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[instrumento_fondeo.usuario_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[movimiento_billetera.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[movimiento_billetera.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | no |
| [[movimiento_custodia.cuenta_custodia_id → cuenta_custodia]] | [[cuenta_custodia]] | — | no |
| [[movimiento_custodia.movimiento_bancario_id → movimiento_bancario]] | [[movimiento_bancario]] | ↗ 03 | sí |
| [[orden_recarga.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[orden_recarga.instrumento_fondeo_id → instrumento_fondeo]] | [[instrumento_fondeo]] | — | sí |
| [[orden_recarga.pago_id → pago]] | [[pago]] | ↗ 03 | sí |
| [[orden_recarga.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | ↗ 03 | sí |
| [[orden_recarga.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[orden_retiro.aprobada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[orden_retiro.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[orden_retiro.instrumento_destino_id → instrumento_fondeo]] | [[instrumento_fondeo]] | — | no |
| [[orden_retiro.proveedor_id → proveedor_pago]] | [[proveedor_pago]] | ↗ 03 | sí |
| [[orden_retiro.retencion_id → retencion_saldo]] | [[retencion_saldo]] | — | sí |
| [[orden_retiro.solicitada_por → usuario]] | [[usuario]] | ↗ 01 | no |
| [[orden_retiro.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[politica_billetera.aprobada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[regla_antifraude.aprobada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[respuesta_idempotente.usuario_id → usuario]] | [[usuario]] | ↗ 01 | no |
| [[retencion_saldo.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[retencion_saldo.liberada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[retencion_saldo.transaccion_origen_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[reverso_transaccion.autorizada_por → usuario]] | [[usuario]] | ↗ 01 | no |
| [[reverso_transaccion.transaccion_original_id → transaccion_billetera]] | [[transaccion_billetera]] | — | no |
| [[reverso_transaccion.transaccion_reverso_id → transaccion_billetera]] | [[transaccion_billetera]] | — | sí |
| [[saldo_diario_billetera.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[solicitud_cierre_billetera.aprobada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[solicitud_cierre_billetera.cuenta_billetera_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[solicitud_cierre_billetera.orden_retiro_id → orden_retiro]] | [[orden_retiro]] | — | sí |
| [[transaccion_billetera.asiento_contable_id → asiento_contable]] | [[asiento_contable]] | ↗ 03 | sí |
| [[transaccion_billetera.dispositivo_id → dispositivo]] | [[dispositivo]] | ↗ 01 | sí |
| [[transaccion_billetera.grupo_id → grupo]] | [[grupo]] | ↗ 02 | sí |
| [[transaccion_billetera.iniciada_por → usuario]] | [[usuario]] | ↗ 01 | sí |
| [[transaccion_billetera.sesion_id → sesion]] | [[sesion]] | ↗ 01 | sí |
| [[transferencia_p2p.cuenta_billetera_destino_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[transferencia_p2p.cuenta_billetera_origen_id → cuenta_billetera]] | [[cuenta_billetera]] | — | no |
| [[transferencia_p2p.grupo_id → grupo]] | [[grupo]] | ↗ 02 | sí |
| [[transferencia_p2p.obligacion_id → obligacion_aporte]] | [[obligacion_aporte]] | ↗ 03 | sí |
| [[transferencia_p2p.transaccion_id → transaccion_billetera]] | [[transaccion_billetera]] | — | no |
