-- Claves foráneas del módulo 04 — Entregas de Fondo
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.

ALTER TABLE entregas.confirmacion_recepcion
  ADD CONSTRAINT fk_confirmacion_recepcion_entrega_id
  FOREIGN KEY (entrega_id) REFERENCES entregas.entrega_fondo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.confirmacion_recepcion
  ADD CONSTRAINT fk_confirmacion_recepcion_token_confirmacion_id
  FOREIGN KEY (token_confirmacion_id) REFERENCES identidad.token_verificacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE entregas.cuenta_bancaria_beneficiario
  ADD CONSTRAINT fk_cuenta_bancaria_beneficiario_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.deduccion_entrega
  ADD CONSTRAINT fk_deduccion_entrega_entrega_id
  FOREIGN KEY (entrega_id) REFERENCES entregas.entrega_fondo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.entrega_fondo
  ADD CONSTRAINT fk_entrega_fondo_autorizada_por
  FOREIGN KEY (autorizada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE entregas.entrega_fondo
  ADD CONSTRAINT fk_entrega_fondo_beneficiario_participante_id
  FOREIGN KEY (beneficiario_participante_id) REFERENCES grupos.participante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.entrega_fondo
  ADD CONSTRAINT fk_entrega_fondo_cuenta_destino_id
  FOREIGN KEY (cuenta_destino_id) REFERENCES entregas.cuenta_bancaria_beneficiario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE entregas.entrega_fondo
  ADD CONSTRAINT fk_entrega_fondo_cupo_id
  FOREIGN KEY (cupo_id) REFERENCES grupos.cupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.entrega_fondo
  ADD CONSTRAINT fk_entrega_fondo_ejecutada_por
  FOREIGN KEY (ejecutada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE entregas.entrega_fondo
  ADD CONSTRAINT fk_entrega_fondo_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.entrega_fondo
  ADD CONSTRAINT fk_entrega_fondo_periodo_id
  FOREIGN KEY (periodo_id) REFERENCES grupos.periodo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.entrega_fondo
  ADD CONSTRAINT fk_entrega_fondo_turno_id
  FOREIGN KEY (turno_id) REFERENCES grupos.turno (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.historial_estado_entrega
  ADD CONSTRAINT fk_historial_estado_entrega_ejecutado_por
  FOREIGN KEY (ejecutado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE entregas.historial_estado_entrega
  ADD CONSTRAINT fk_historial_estado_entrega_entrega_id
  FOREIGN KEY (entrega_id) REFERENCES entregas.entrega_fondo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.incidencia_entrega
  ADD CONSTRAINT fk_incidencia_entrega_asignada_a
  FOREIGN KEY (asignada_a) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE entregas.incidencia_entrega
  ADD CONSTRAINT fk_incidencia_entrega_entrega_id
  FOREIGN KEY (entrega_id) REFERENCES entregas.entrega_fondo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.incidencia_entrega
  ADD CONSTRAINT fk_incidencia_entrega_reportada_por
  FOREIGN KEY (reportada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.intento_desembolso
  ADD CONSTRAINT fk_intento_desembolso_orden_desembolso_id
  FOREIGN KEY (orden_desembolso_id) REFERENCES entregas.orden_desembolso (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.orden_desembolso
  ADD CONSTRAINT fk_orden_desembolso_cuenta_destino_id
  FOREIGN KEY (cuenta_destino_id) REFERENCES entregas.cuenta_bancaria_beneficiario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.orden_desembolso
  ADD CONSTRAINT fk_orden_desembolso_entrega_id
  FOREIGN KEY (entrega_id) REFERENCES entregas.entrega_fondo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.orden_desembolso
  ADD CONSTRAINT fk_orden_desembolso_proveedor_id
  FOREIGN KEY (proveedor_id) REFERENCES aportes.proveedor_pago (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.validacion_pre_entrega
  ADD CONSTRAINT fk_validacion_pre_entrega_entrega_id
  FOREIGN KEY (entrega_id) REFERENCES entregas.entrega_fondo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE entregas.validacion_pre_entrega
  ADD CONSTRAINT fk_validacion_pre_entrega_omitida_por
  FOREIGN KEY (omitida_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE entregas.validacion_pre_entrega
  ADD CONSTRAINT fk_validacion_pre_entrega_regla_id
  FOREIGN KEY (regla_id) REFERENCES entregas.regla_entrega (id) ON DELETE RESTRICT ON UPDATE CASCADE;
