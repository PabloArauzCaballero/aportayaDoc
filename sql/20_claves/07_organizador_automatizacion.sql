-- Claves foráneas del módulo 07 — Organizador y Automatización
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.
--
-- Cada una se borra si existe antes de crearse: PostgreSQL no tiene
-- ADD CONSTRAINT IF NOT EXISTS, y sql/aplicar.sql se aplica también
-- sobre una base que ya lo tiene. Borrar y volver a crear —en vez de
-- saltear si ya está— es lo que hace que un ON DELETE cambiado en el
-- modelo quede corregido al reaplicar.

ALTER TABLE organizador.apelacion_sancion_org DROP CONSTRAINT IF EXISTS fk_apelacion_sancion_org_resuelta_por;
ALTER TABLE organizador.apelacion_sancion_org
  ADD CONSTRAINT fk_apelacion_sancion_org_resuelta_por
  FOREIGN KEY (resuelta_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE organizador.apelacion_sancion_org DROP CONSTRAINT IF EXISTS fk_apelacion_sancion_org_sancion_organizador_id;
ALTER TABLE organizador.apelacion_sancion_org
  ADD CONSTRAINT fk_apelacion_sancion_org_sancion_organizador_id
  FOREIGN KEY (sancion_organizador_id) REFERENCES organizador.sancion_organizador (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.capacitacion_organizador DROP CONSTRAINT IF EXISTS fk_capacitacion_organizador_organizador_id;
ALTER TABLE organizador.capacitacion_organizador
  ADD CONSTRAINT fk_capacitacion_organizador_organizador_id
  FOREIGN KEY (organizador_id) REFERENCES organizador.organizador (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.contrato_organizador DROP CONSTRAINT IF EXISTS fk_contrato_organizador_organizador_id;
ALTER TABLE organizador.contrato_organizador
  ADD CONSTRAINT fk_contrato_organizador_organizador_id
  FOREIGN KEY (organizador_id) REFERENCES organizador.organizador (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.contrato_organizador DROP CONSTRAINT IF EXISTS fk_contrato_organizador_token_firma_id;
ALTER TABLE organizador.contrato_organizador
  ADD CONSTRAINT fk_contrato_organizador_token_firma_id
  FOREIGN KEY (token_firma_id) REFERENCES identidad.token_verificacion (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE organizador.ejecucion_tarea DROP CONSTRAINT IF EXISTS fk_ejecucion_tarea_tarea_id;
ALTER TABLE organizador.ejecucion_tarea
  ADD CONSTRAINT fk_ejecucion_tarea_tarea_id
  FOREIGN KEY (tarea_id) REFERENCES organizador.tarea_automatizada (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.evaluacion_desempeno DROP CONSTRAINT IF EXISTS fk_evaluacion_desempeno_organizador_id;
ALTER TABLE organizador.evaluacion_desempeno
  ADD CONSTRAINT fk_evaluacion_desempeno_organizador_id
  FOREIGN KEY (organizador_id) REFERENCES organizador.organizador (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.metrica_organizador DROP CONSTRAINT IF EXISTS fk_metrica_organizador_evaluacion_id;
ALTER TABLE organizador.metrica_organizador
  ADD CONSTRAINT fk_metrica_organizador_evaluacion_id
  FOREIGN KEY (evaluacion_id) REFERENCES organizador.evaluacion_desempeno (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.organizador DROP CONSTRAINT IF EXISTS fk_organizador_usuario_id;
ALTER TABLE organizador.organizador
  ADD CONSTRAINT fk_organizador_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.sancion_organizador DROP CONSTRAINT IF EXISTS fk_sancion_organizador_aplicada_por;
ALTER TABLE organizador.sancion_organizador
  ADD CONSTRAINT fk_sancion_organizador_aplicada_por
  FOREIGN KEY (aplicada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.sancion_organizador DROP CONSTRAINT IF EXISTS fk_sancion_organizador_evaluacion_id;
ALTER TABLE organizador.sancion_organizador
  ADD CONSTRAINT fk_sancion_organizador_evaluacion_id
  FOREIGN KEY (evaluacion_id) REFERENCES organizador.evaluacion_desempeno (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE organizador.sancion_organizador DROP CONSTRAINT IF EXISTS fk_sancion_organizador_organizador_id;
ALTER TABLE organizador.sancion_organizador
  ADD CONSTRAINT fk_sancion_organizador_organizador_id
  FOREIGN KEY (organizador_id) REFERENCES organizador.organizador (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.solicitud_organizador DROP CONSTRAINT IF EXISTS fk_solicitud_organizador_kyc_reforzado_id;
ALTER TABLE organizador.solicitud_organizador
  ADD CONSTRAINT fk_solicitud_organizador_kyc_reforzado_id
  FOREIGN KEY (kyc_reforzado_id) REFERENCES identidad.verificacion_kyc (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE organizador.solicitud_organizador DROP CONSTRAINT IF EXISTS fk_solicitud_organizador_revisada_por;
ALTER TABLE organizador.solicitud_organizador
  ADD CONSTRAINT fk_solicitud_organizador_revisada_por
  FOREIGN KEY (revisada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE organizador.solicitud_organizador DROP CONSTRAINT IF EXISTS fk_solicitud_organizador_usuario_id;
ALTER TABLE organizador.solicitud_organizador
  ADD CONSTRAINT fk_solicitud_organizador_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.tarea_automatizada DROP CONSTRAINT IF EXISTS fk_tarea_automatizada_grupo_id;
ALTER TABLE organizador.tarea_automatizada
  ADD CONSTRAINT fk_tarea_automatizada_grupo_id
  FOREIGN KEY (grupo_id) REFERENCES grupos.grupo (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organizador.tarea_automatizada DROP CONSTRAINT IF EXISTS fk_tarea_automatizada_regla_id;
ALTER TABLE organizador.tarea_automatizada
  ADD CONSTRAINT fk_tarea_automatizada_regla_id
  FOREIGN KEY (regla_id) REFERENCES organizador.regla_automatizacion (id) ON DELETE RESTRICT ON UPDATE CASCADE;
