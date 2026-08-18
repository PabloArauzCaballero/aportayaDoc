-- limite_operativo_billetera · módulo 10 — Billetera, Custodia y Dinero Electrónico
-- clase de dominio: LimiteOperativoBilletera
-- Generado por scripts/generar_ddl.py — no editar a mano.

CREATE TABLE IF NOT EXISTS catalogo.limite_operativo_billetera (
  id                                 UUID DEFAULT gen_random_uuid() NOT NULL,
  concepto                           VARCHAR(25) NOT NULL,
  nivel_debida_diligencia            VARCHAR(15) NOT NULL,
  ventana                            VARCHAR(10) NOT NULL,
  monto_maximo                       NUMERIC(16,2),
  cantidad_maxima                    INTEGER,
  moneda                             CHAR(3) NOT NULL,
  base_normativa                     VARCHAR(120) NOT NULL,
  vigente_desde                      DATE NOT NULL,
  vigente_hasta                      DATE,
  activo                             BOOLEAN DEFAULT FALSE NOT NULL,
  CONSTRAINT pk_limite_operativo_billetera PRIMARY KEY (id),
  CONSTRAINT ck_limite_operativo_billetera_concepto CHECK (concepto IN ('APORTE', 'RECARGA', 'RETIRO', 'SALDO_MAXIMO', 'TRANSFERENCIA')),
  CONSTRAINT ck_limite_operativo_billetera_nivel_debida_diligencia CHECK (nivel_debida_diligencia IN ('AMPLIADA', 'ESTANDAR', 'REFORZADA', 'SIMPLIFICADA')),
  CONSTRAINT ck_limite_operativo_billetera_ventana CHECK (ventana IN ('ANIO', 'DIA', 'MES', 'OPERACION', 'SEMANA'))
);

COMMENT ON TABLE catalogo.limite_operativo_billetera IS 'Módulo 10 — Billetera, Custodia y Dinero Electrónico. El saldo no se guarda: se deriva, y todos los días cuadra contra el banco';
COMMENT ON COLUMN catalogo.limite_operativo_billetera.id IS 'PK';
COMMENT ON COLUMN catalogo.limite_operativo_billetera.concepto IS 'CK, UQ+nivel_debida_diligencia+ventana';
COMMENT ON COLUMN catalogo.limite_operativo_billetera.nivel_debida_diligencia IS 'CK';
COMMENT ON COLUMN catalogo.limite_operativo_billetera.ventana IS 'CK';
COMMENT ON COLUMN catalogo.limite_operativo_billetera.monto_maximo IS 'NULL';
COMMENT ON COLUMN catalogo.limite_operativo_billetera.cantidad_maxima IS 'NULL';
COMMENT ON COLUMN catalogo.limite_operativo_billetera.vigente_hasta IS 'NULL';
