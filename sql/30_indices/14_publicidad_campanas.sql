-- Índices y restricciones de unicidad del módulo 14 — Publicidad y Campañas
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE UNIQUE INDEX IF NOT EXISTS uq_socio_comercial_numero_documento
  ON publicidad.socio_comercial (numero_documento);

CREATE INDEX IF NOT EXISTS ix_socio_comercial_estado
  ON publicidad.socio_comercial (estado);

CREATE INDEX IF NOT EXISTS ix_anunciante_tipo
  ON publicidad.anunciante (tipo);

CREATE INDEX IF NOT EXISTS ix_anunciante_organizador_id
  ON publicidad.anunciante (organizador_id);

CREATE INDEX IF NOT EXISTS ix_anunciante_socio_comercial_id
  ON publicidad.anunciante (socio_comercial_id);

CREATE INDEX IF NOT EXISTS ix_anunciante_estado
  ON publicidad.anunciante (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cuenta_publicitaria_anunciante_id
  ON publicidad.cuenta_publicitaria (anunciante_id);

CREATE INDEX IF NOT EXISTS ix_cuenta_publicitaria_estado
  ON publicidad.cuenta_publicitaria (estado);

CREATE INDEX IF NOT EXISTS ix_campana_publicitaria_cuenta_publicitaria_id
  ON publicidad.campana_publicitaria (cuenta_publicitaria_id);

CREATE INDEX IF NOT EXISTS ix_campana_publicitaria_estado
  ON publicidad.campana_publicitaria (estado);

CREATE UNIQUE INDEX IF NOT EXISTS uq_espacio_publicitario_codigo
  ON publicidad.espacio_publicitario (codigo);

CREATE INDEX IF NOT EXISTS ix_conjunto_anuncios_campana_publicitaria_id
  ON publicidad.conjunto_anuncios (campana_publicitaria_id);

CREATE INDEX IF NOT EXISTS ix_conjunto_anuncios_segmento_audiencia_id
  ON publicidad.conjunto_anuncios (segmento_audiencia_id);

CREATE INDEX IF NOT EXISTS ix_conjunto_anuncios_espacio_publicitario_id
  ON publicidad.conjunto_anuncios (espacio_publicitario_id);

CREATE INDEX IF NOT EXISTS ix_conjunto_anuncios_estado
  ON publicidad.conjunto_anuncios (estado);

CREATE INDEX IF NOT EXISTS ix_pieza_creativa_anunciante_id
  ON publicidad.pieza_creativa (anunciante_id);

CREATE INDEX IF NOT EXISTS ix_pieza_creativa_estado_moderacion
  ON publicidad.pieza_creativa (estado_moderacion);

CREATE INDEX IF NOT EXISTS ix_revision_creativa_pieza_creativa_id
  ON publicidad.revision_creativa (pieza_creativa_id);

CREATE INDEX IF NOT EXISTS ix_anuncio_conjunto_anuncios_id
  ON publicidad.anuncio (conjunto_anuncios_id);

CREATE INDEX IF NOT EXISTS ix_anuncio_pieza_creativa_id
  ON publicidad.anuncio (pieza_creativa_id);

CREATE INDEX IF NOT EXISTS ix_anuncio_estado
  ON publicidad.anuncio (estado);

CREATE INDEX IF NOT EXISTS ix_impresion_anuncio_anuncio_id
  ON publicidad.impresion_anuncio (anuncio_id);

CREATE INDEX IF NOT EXISTS ix_impresion_anuncio_usuario_id
  ON publicidad.impresion_anuncio (usuario_id);

CREATE INDEX IF NOT EXISTS ix_impresion_anuncio_mostrada_en
  ON publicidad.impresion_anuncio (mostrada_en);

CREATE INDEX IF NOT EXISTS ix_clic_anuncio_impresion_id
  ON publicidad.clic_anuncio (impresion_id);

CREATE INDEX IF NOT EXISTS ix_clic_anuncio_usuario_id
  ON publicidad.clic_anuncio (usuario_id);

CREATE INDEX IF NOT EXISTS ix_clic_anuncio_clic_en
  ON publicidad.clic_anuncio (clic_en);

CREATE INDEX IF NOT EXISTS ix_conversion_anuncio_clic_id
  ON publicidad.conversion_anuncio (clic_id);

CREATE INDEX IF NOT EXISTS ix_conversion_anuncio_impresion_id
  ON publicidad.conversion_anuncio (impresion_id);

CREATE INDEX IF NOT EXISTS ix_conversion_anuncio_referencia_id
  ON publicidad.conversion_anuncio (referencia_id);

CREATE INDEX IF NOT EXISTS ix_factura_publicidad_cuenta_publicitaria_id
  ON publicidad.factura_publicidad (cuenta_publicitaria_id);

CREATE INDEX IF NOT EXISTS ix_factura_publicidad_estado
  ON publicidad.factura_publicidad (estado);
