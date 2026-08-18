-- entrega_fondo · módulo 04 — Entregas de Fondo
-- clase de dominio: EntregaFondo
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS entregas.entrega_fondo (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  grupo_id                           UUID NOT NULL,
  periodo_id                         UUID NOT NULL,
  turno_id                           UUID NOT NULL,
  cupo_id                            UUID NOT NULL,
  beneficiario_participante_id       UUID NOT NULL,
  cuenta_destino_id                  UUID,
  monto_bolsa_bruto                  NUMERIC(14,2) DEFAULT 0 NOT NULL,
  total_deducciones                  NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_neto_a_entregar              NUMERIC(14,2) DEFAULT 0 NOT NULL,
  monto_efectivamente_entregado      NUMERIC(14,2) DEFAULT 0 NOT NULL,
  moneda                             CHAR(3) NOT NULL,
  estado                             VARCHAR(35) NOT NULL,
  metodo_desembolso                  VARCHAR(30) NOT NULL,
  fecha_programada                   DATE NOT NULL,
  fecha_autorizacion                 TIMESTAMPTZ,
  fecha_entrega                      TIMESTAMPTZ,
  autorizada_por                     UUID,
  ejecutada_por                      UUID,
  comprobante_url                    VARCHAR(255),
  hash_comprobante                   VARCHAR(64),
  observaciones                      VARCHAR(400),
  version                            INTEGER DEFAULT 0 NOT NULL,
  CONSTRAINT pk_entrega_fondo PRIMARY KEY (id),
  CONSTRAINT ck_entrega_fondo_monto_neto_a_entregar CHECK (monto_neto_a_entregar >= 0),
  CONSTRAINT ck_entrega_fondo_estado CHECK (estado IN ('ANULADA', 'AUTORIZADA', 'BLOQUEADA_POR_FONDO_INCOMPLETO', 'BLOQUEADA_POR_VALIDACION', 'CONFIRMADA', 'ENTREGADA', 'EN_PROCESO_DESEMBOLSO', 'LISTA_PARA_ENTREGA', 'PROGRAMADA', 'RECHAZADA_POR_BENEFICIARIO', 'REVERSADA')),
  CONSTRAINT ck_entrega_fondo_metodo_desembolso CHECK (metodo_desembolso IN ('BILLETERA_MOVIL', 'COMPENSACION_INTERNA', 'EFECTIVO_ORGANIZADOR', 'QR_ENVIO', 'TRANSFERENCIA_BANCARIA'))
);

COMMENT ON TABLE entregas.entrega_fondo IS 'Módulo 04 — Entregas de Fondo. Que la bolsa llegue completa, a la persona correcta, una sola vez';
COMMENT ON COLUMN entregas.entrega_fondo.id IS 'PK';
COMMENT ON COLUMN entregas.entrega_fondo.grupo_id IS 'FK, IDX';
COMMENT ON COLUMN entregas.entrega_fondo.periodo_id IS 'FK, UQ';
COMMENT ON COLUMN entregas.entrega_fondo.turno_id IS 'FK, UQ';
COMMENT ON COLUMN entregas.entrega_fondo.cupo_id IS 'FK';
COMMENT ON COLUMN entregas.entrega_fondo.beneficiario_participante_id IS 'FK, IDX';
COMMENT ON COLUMN entregas.entrega_fondo.cuenta_destino_id IS 'FK, NULL';
COMMENT ON COLUMN entregas.entrega_fondo.monto_neto_a_entregar IS 'CK: >= 0';
COMMENT ON COLUMN entregas.entrega_fondo.estado IS 'CK, IDX';
COMMENT ON COLUMN entregas.entrega_fondo.metodo_desembolso IS 'CK';
COMMENT ON COLUMN entregas.entrega_fondo.fecha_programada IS 'IDX';
COMMENT ON COLUMN entregas.entrega_fondo.fecha_autorizacion IS 'NULL';
COMMENT ON COLUMN entregas.entrega_fondo.fecha_entrega IS 'NULL';
COMMENT ON COLUMN entregas.entrega_fondo.autorizada_por IS 'FK, NULL';
COMMENT ON COLUMN entregas.entrega_fondo.ejecutada_por IS 'FK, NULL';
COMMENT ON COLUMN entregas.entrega_fondo.comprobante_url IS 'NULL';
COMMENT ON COLUMN entregas.entrega_fondo.hash_comprobante IS 'NULL';
COMMENT ON COLUMN entregas.entrega_fondo.observaciones IS 'NULL';
