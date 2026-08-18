-- activo_informacion · módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero
-- clase de dominio: ActivoInformacion
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS cumplimiento.activo_informacion (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  propietario_id                     UUID,
  custodio_id                        UUID,
  contrato_tercero_id                UUID,
  codigo                             VARCHAR(30) NOT NULL,
  nombre                             VARCHAR(120) NOT NULL,
  tipo                               VARCHAR(25) NOT NULL,
  clasificacion                      VARCHAR(15) NOT NULL,
  contiene_datos_personales          BOOLEAN DEFAULT FALSE NOT NULL,
  contiene_datos_sensibles           BOOLEAN DEFAULT FALSE NOT NULL,
  criticidad                         VARCHAR(10) NOT NULL,
  ubicacion                          VARCHAR(120) NOT NULL,
  exige_cifrado                      BOOLEAN DEFAULT FALSE NOT NULL,
  ultima_revision                    DATE NOT NULL,
  CONSTRAINT pk_activo_informacion PRIMARY KEY (id),
  CONSTRAINT ck_activo_informacion_tipo CHECK (tipo IN ('APLICACION', 'BASE_DATOS', 'DOCUMENTO', 'INFRAESTRUCTURA', 'SERVICIO')),
  CONSTRAINT ck_activo_informacion_clasificacion CHECK (clasificacion IN ('CONFIDENCIAL', 'INTERNA', 'PUBLICA', 'RESERVADA')),
  CONSTRAINT ck_activo_informacion_criticidad CHECK (criticidad IN ('ALTA', 'BAJA', 'CRITICA', 'MEDIA'))
);

COMMENT ON TABLE cumplimiento.activo_informacion IS 'Módulo 12 — Cumplimiento Regulatorio y Consumidor Financiero. Que una inspección se responda con consultas, no armando carpetas';
COMMENT ON COLUMN cumplimiento.activo_informacion.id IS 'PK';
COMMENT ON COLUMN cumplimiento.activo_informacion.propietario_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.activo_informacion.custodio_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.activo_informacion.contrato_tercero_id IS 'FK, NULL';
COMMENT ON COLUMN cumplimiento.activo_informacion.codigo IS 'UQ';
COMMENT ON COLUMN cumplimiento.activo_informacion.tipo IS 'CK';
COMMENT ON COLUMN cumplimiento.activo_informacion.clasificacion IS 'CK, IDX';
COMMENT ON COLUMN cumplimiento.activo_informacion.contiene_datos_personales IS 'IDX';
COMMENT ON COLUMN cumplimiento.activo_informacion.criticidad IS 'CK';
