-- Consultas de control: TODAS deben devolver cero filas.
-- GENERADO desde docs/Restricciones.md — no editar a mano.
-- Se ejecutan en cada despliegue y en el control diario,
-- no forman parte de sql/aplicar.sql.

-- 1) Transacciones descuadradas
SELECT t.id FROM transaccion_billetera t
  JOIN movimiento_billetera m ON m.transaccion_id = t.id
 WHERE t.estado = 'APLICADA'
 GROUP BY t.id
HAVING SUM(CASE WHEN m.sentido='DEBITO' THEN m.monto ELSE -m.monto END) <> 0;

-- 2) Saldo cacheado que no coincide con el libro
SELECT c.id, c.saldo_disponible, SUM(CASE WHEN m.sentido='CREDITO' THEN m.monto
                                          ELSE -m.monto END) AS calculado
  FROM cuenta_billetera c
  LEFT JOIN movimiento_billetera m ON m.cuenta_billetera_id = c.id
 GROUP BY c.id, c.saldo_disponible
HAVING c.saldo_disponible + c.saldo_retenido
     <> COALESCE(SUM(CASE WHEN m.sentido='CREDITO' THEN m.monto ELSE -m.monto END),0);

-- 3) Días con encaje incumplido
SELECT fecha, ratio_cobertura FROM conciliacion_custodia WHERE NOT cumple_encaje;

-- 4) Obligaciones de reporte vencidas
SELECT codigo, periodo, fecha_limite FROM reporte_regulatorio r
  JOIN catalogo_reporte_regulatorio c ON c.id = r.catalogo_reporte_id
 WHERE r.estado <> 'ENVIADO' AND r.fecha_limite < current_date;

-- 5) Entregas con comisión no trazable al tarifario
SELECT d.id FROM deduccion_entrega d
  LEFT JOIN cargo_comision cc ON cc.deduccion_entrega_id = d.id
 WHERE d.tipo = 'COMISION_PLATAFORMA' AND cc.id IS NULL;

-- 6) Reclamos cerrados favorables sin reparación
SELECT codigo FROM reclamo_cliente
 WHERE estado='CERRADO' AND resultado='FAVORABLE'
   AND monto_reclamado IS NOT NULL AND devolucion_comision_id IS NULL;

-- 7) R-AUD-10 · eslabones rotos en la cadena de transacciones
-- Cada fila debe apuntar al hash de su predecesora por secuencia. Detecta tanto
-- una alteración como una eliminación: si falta un eslabón, el siguiente queda
-- apuntando a un hash que ya no existe.
SELECT t.id, t.secuencia, t.hash_anterior, prev.hash_registro AS esperado
  FROM transaccion_billetera t
  LEFT JOIN LATERAL (
        SELECT p.hash_registro FROM transaccion_billetera p
         WHERE p.secuencia < t.secuencia
         ORDER BY p.secuencia DESC LIMIT 1) prev ON TRUE
 WHERE t.hash_anterior IS DISTINCT FROM prev.hash_registro;

-- 8) R-AUD-10 · eslabones rotos en la cadena de la bitácora
SELECT b.id, b.secuencia, b.hash_anterior, COALESCE(prev.hash_registro, repeat('0',64)) AS esperado
  FROM bitacora_evento b
  LEFT JOIN LATERAL (
        SELECT p.hash_registro FROM bitacora_evento p
         WHERE p.secuencia < b.secuencia
         ORDER BY p.secuencia DESC LIMIT 1) prev ON TRUE
 WHERE b.hash_anterior IS DISTINCT FROM COALESCE(prev.hash_registro, repeat('0',64));

-- 9) R-AUD-10 · patas huérfanas o transacciones aplicadas sin patas
-- Las patas no entran en el hash del encabezado (ver R-AUD-03): esta consulta es
-- la que cubre ese flanco.
SELECT m.id AS movimiento_huerfano, NULL::uuid AS transaccion_sin_patas
  FROM movimiento_billetera m
  LEFT JOIN transaccion_billetera t ON t.id = m.transaccion_id
 WHERE t.id IS NULL
UNION ALL
SELECT NULL, t.id FROM transaccion_billetera t
 WHERE t.estado = 'APLICADA'
   AND NOT EXISTS (SELECT 1 FROM movimiento_billetera m WHERE m.transaccion_id = t.id);

-- 10) R-BIL-20 · transacciones que mezclan monedas
SELECT t.id, t.moneda, c.moneda AS moneda_cuenta
  FROM transaccion_billetera t
  JOIN movimiento_billetera m ON m.transaccion_id = t.id
  JOIN cuenta_billetera c ON c.id = m.cuenta_billetera_id
 WHERE c.moneda <> t.moneda;

-- 11) R-SEG-03 · tablas con datos de titular sin RLS forzada
--     Recorre todos los esquemas de servicio. Filtraba por `public`, igual que la
--     funcion que aplica RLS, asi que devolvia cero filas SIEMPRE: la verificacion
--     que debia denunciar el agujero lo estaba tapando.
SELECT n.nspname || '.' || c.relname FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname NOT IN ('pg_catalog','information_schema','pg_toast')
   AND n.nspname NOT LIKE 'pg_temp%'
   AND c.relkind = 'r'
   AND EXISTS (SELECT 1 FROM pg_attribute a
                WHERE a.attrelid = c.oid AND a.attname = 'usuario_id'
                  AND NOT a.attisdropped)
   AND NOT c.relrowsecurity;

-- 12) R-CTB-09 · saldo contable en caché que no coincide con el mayor
--     El equivalente contable de la consulta 2: el saldo es caché, el libro es la
--     verdad, y si difieren gana el libro y hay que explicar por qué.
SELECT c.id, c.codigo, c.saldo AS cacheado, COALESCE(l.derivado, 0) AS derivado
  FROM cuenta_contable c
  LEFT JOIN LATERAL (
        SELECT SUM(CASE WHEN c.naturaleza = 'DEUDORA' THEN m.debe - m.haber
                        ELSE m.haber - m.debe END) AS derivado
          FROM movimiento_contable m WHERE m.cuenta_id = c.id) l ON TRUE
 WHERE c.saldo <> COALESCE(l.derivado, 0);

-- 13) R-AUD-11 · asientos donde el estado y el enlace de reversa se contradicen
--     La restricción lo impide al insertar; esta consulta detecta lo que hubiera
--     entrado antes de que existiera.
SELECT id, numero, estado, asiento_reversa_id
  FROM asiento_contable
 WHERE (estado = 'REVERSADO') <> (asiento_reversa_id IS NOT NULL);
