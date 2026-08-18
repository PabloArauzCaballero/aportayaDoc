-- socio_comercial · módulo 14 — Publicidad y Campañas
-- clase de dominio: SocioComercial
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS publicidad.socio_comercial (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  razon_social                       VARCHAR(150) NOT NULL,
  numero_documento                   VARCHAR(30) NOT NULL,
  rubro                              VARCHAR(60),
  email_contacto                     VARCHAR(120) NOT NULL,
  telefono_contacto                  VARCHAR(20),
  estado                             VARCHAR(15) NOT NULL,
  verificado_por                     UUID,
  creado_en                          TIMESTAMPTZ DEFAULT now() NOT NULL,
  CONSTRAINT pk_socio_comercial PRIMARY KEY (id),
  CONSTRAINT ck_socio_comercial_estado CHECK (estado IN ('ACTIVO', 'DADO_DE_BAJA', 'POSTULADO', 'SUSPENDIDO'))
);

COMMENT ON TABLE publicidad.socio_comercial IS 'Módulo 14 — Publicidad y Campañas. Que un partner se anuncie dentro de la app sin inventar un segundo cobro';
COMMENT ON COLUMN publicidad.socio_comercial.id IS 'PK';
COMMENT ON COLUMN publicidad.socio_comercial.numero_documento IS 'UQ';
COMMENT ON COLUMN publicidad.socio_comercial.rubro IS 'NULL';
COMMENT ON COLUMN publicidad.socio_comercial.telefono_contacto IS 'NULL';
COMMENT ON COLUMN publicidad.socio_comercial.estado IS 'CK, IDX';
COMMENT ON COLUMN publicidad.socio_comercial.verificado_por IS 'FK, NULL';
