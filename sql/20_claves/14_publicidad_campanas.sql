-- Claves foráneas del módulo 14 — Publicidad y Campañas
-- Generado por scripts/generar_ddl.py — no editar a mano.
-- Se aplican después de crear todas las tablas: el modelo tiene
-- referencias circulares entre módulos.

ALTER TABLE publicidad.anunciante
  ADD CONSTRAINT fk_anunciante_organizador_id
  FOREIGN KEY (organizador_id) REFERENCES organizador.organizador (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.anunciante
  ADD CONSTRAINT fk_anunciante_socio_comercial_id
  FOREIGN KEY (socio_comercial_id) REFERENCES publicidad.socio_comercial (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.anuncio
  ADD CONSTRAINT fk_anuncio_conjunto_anuncios_id
  FOREIGN KEY (conjunto_anuncios_id) REFERENCES publicidad.conjunto_anuncios (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.anuncio
  ADD CONSTRAINT fk_anuncio_pieza_creativa_id
  FOREIGN KEY (pieza_creativa_id) REFERENCES publicidad.pieza_creativa (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.campana_publicitaria
  ADD CONSTRAINT fk_campana_publicitaria_aprobada_por
  FOREIGN KEY (aprobada_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.campana_publicitaria
  ADD CONSTRAINT fk_campana_publicitaria_cuenta_publicitaria_id
  FOREIGN KEY (cuenta_publicitaria_id) REFERENCES publicidad.cuenta_publicitaria (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.clic_anuncio
  ADD CONSTRAINT fk_clic_anuncio_impresion_id
  FOREIGN KEY (impresion_id) REFERENCES publicidad.impresion_anuncio (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.clic_anuncio
  ADD CONSTRAINT fk_clic_anuncio_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.conjunto_anuncios
  ADD CONSTRAINT fk_conjunto_anuncios_campana_publicitaria_id
  FOREIGN KEY (campana_publicitaria_id) REFERENCES publicidad.campana_publicitaria (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.conjunto_anuncios
  ADD CONSTRAINT fk_conjunto_anuncios_espacio_publicitario_id
  FOREIGN KEY (espacio_publicitario_id) REFERENCES publicidad.espacio_publicitario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.conjunto_anuncios
  ADD CONSTRAINT fk_conjunto_anuncios_segmento_audiencia_id
  FOREIGN KEY (segmento_audiencia_id) REFERENCES publicidad.segmento_audiencia (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.conversion_anuncio
  ADD CONSTRAINT fk_conversion_anuncio_clic_id
  FOREIGN KEY (clic_id) REFERENCES publicidad.clic_anuncio (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.conversion_anuncio
  ADD CONSTRAINT fk_conversion_anuncio_impresion_id
  FOREIGN KEY (impresion_id) REFERENCES publicidad.impresion_anuncio (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.cuenta_publicitaria
  ADD CONSTRAINT fk_cuenta_publicitaria_anunciante_id
  FOREIGN KEY (anunciante_id) REFERENCES publicidad.anunciante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.factura_publicidad
  ADD CONSTRAINT fk_factura_publicidad_cuenta_por_cobrar_id
  FOREIGN KEY (cuenta_por_cobrar_id) REFERENCES erp.cuenta_por_cobrar (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.factura_publicidad
  ADD CONSTRAINT fk_factura_publicidad_cuenta_publicitaria_id
  FOREIGN KEY (cuenta_publicitaria_id) REFERENCES publicidad.cuenta_publicitaria (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.factura_publicidad
  ADD CONSTRAINT fk_factura_publicidad_factura_electronica_id
  FOREIGN KEY (factura_electronica_id) REFERENCES tarifas.factura_electronica (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.impresion_anuncio
  ADD CONSTRAINT fk_impresion_anuncio_anuncio_id
  FOREIGN KEY (anuncio_id) REFERENCES publicidad.anuncio (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.impresion_anuncio
  ADD CONSTRAINT fk_impresion_anuncio_usuario_id
  FOREIGN KEY (usuario_id) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE publicidad.pieza_creativa
  ADD CONSTRAINT fk_pieza_creativa_anunciante_id
  FOREIGN KEY (anunciante_id) REFERENCES publicidad.anunciante (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.revision_creativa
  ADD CONSTRAINT fk_revision_creativa_pieza_creativa_id
  FOREIGN KEY (pieza_creativa_id) REFERENCES publicidad.pieza_creativa (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.revision_creativa
  ADD CONSTRAINT fk_revision_creativa_revisada_por
  FOREIGN KEY (revisada_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.segmento_audiencia
  ADD CONSTRAINT fk_segmento_audiencia_creado_por
  FOREIGN KEY (creado_por) REFERENCES identidad.usuario (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE publicidad.socio_comercial
  ADD CONSTRAINT fk_socio_comercial_verificado_por
  FOREIGN KEY (verificado_por) REFERENCES identidad.usuario (id) ON DELETE SET NULL ON UPDATE CASCADE;
