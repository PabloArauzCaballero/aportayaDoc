package bo.aportaya.tarifas;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/**
 * La mitad de facturacion de la fixtura de tarifas: cuentas contables, impuestos, datos
 * del emisor, devengos ya cobrados, facturas, contingencias y reclamos.
 *
 * <p>Esta separada de {@link FixturaDeTarifas} porque son dos cosas distintas: una arma
 * el catalogo con el que se calcula, y esta arma la evidencia de lo que ya se cobro.
 * Juntas pasaban las 300 lineas, que es la senal de que nadie las habia separado.
 */
class FixturaDeFacturacion {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(80_100_000);

    private final DSLContext dsl;

    FixturaDeFacturacion(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID cuentaDeIngreso() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_contable
                    (id, codigo, nombre, tipo, naturaleza, nivel, es_cuenta_de_movimiento, saldo)
                VALUES (?, ?, 'Ingresos por comision', 'INGRESO', 'ACREEDORA', 4, true, 0)
                """,
                id,
                "4" + SECUENCIA.incrementAndGet());
        return id;
    }

    /** Un impuesto vigente del catalogo. Nunca una constante en el codigo. */
    UUID impuesto(String codigo, String alicuota) {
        return impuesto(codigo, alicuota, "SOBRE_PRECIO");
    }

    UUID impuesto(String codigo, String alicuota, String tipoCalculo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO catalogo.impuesto
                    (id, codigo, nombre, alicuota, tipo_calculo, base_legal, vigente_desde)
                VALUES (?, ?, ?, ?, ?, 'Ley 843', current_date - 30)
                """,
                id,
                codigo,
                codigo,
                new BigDecimal(alicuota),
                tipoCalculo);
        return id;
    }

    UUID datosDeFacturacion(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO tarifas.datos_facturacion
                    (id, usuario_id, tipo_documento, numero_documento, razon_social, email_envio,
                     es_predeterminado, verificado, actualizado_en)
                VALUES (?, ?, 'NIT', ?, 'Razon Social de Prueba', 'facturas@aportaya.test',
                        true, true, now())
                """,
                id,
                usuarioId,
                Integer.toString(SECUENCIA.incrementAndGet()));
        return id;
    }

    /** Un devengo cobrado: el punto de partida de la factura y de la devolucion. */
    UUID devengoCobrado(UUID conceptoId, UUID tarifarioId, UUID usuarioId, String monto, String periodo) {
        UUID devengoId = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO tarifas.devengo_comision
                    (id, concepto_tarifa_id, tarifario_id, usuario_obligado_id, referencia_tipo,
                     referencia_id, monto_base, monto_comision, monto_descuento, monto_impuesto,
                     monto_total, moneda, estado, fecha_devengo, periodo_contable, clave_idempotencia)
                VALUES (?, ?, ?, ?, 'ENTREGA_FONDO', gen_random_uuid(), 1000.00, ?, 0, 0,
                        ?, 'BOB', 'COBRADO', now(), ?, ?)
                """,
                devengoId,
                conceptoId,
                tarifarioId,
                usuarioId,
                new BigDecimal(monto),
                new BigDecimal(monto),
                periodo,
                "dev-" + devengoId);
        dsl.execute(
                """
                INSERT INTO tarifas.cargo_comision
                    (id, devengo_id, forma_cobro, monto_cobrado, moneda, estado, intentos, cobrado_en)
                VALUES (gen_random_uuid(), ?, 'DEDUCCION_DE_ENTREGA', ?, 'BOB', 'COBRADO', 1, now())
                """,
                devengoId,
                new BigDecimal(monto));
        return devengoId;
    }

    /**
     * Una factura ya emitida.
     *
     * <p>La escribe la fixtura y no el caso de uso porque el CU-33 parte de una
     * factura existente: montar todo el CU-32 para probar una devolucion mezclaria dos
     * cosas, y el fallo de una haria fallar la otra sin decir por que.
     */
    UUID factura(
            UUID devengoId,
            UUID usuarioId,
            UUID datosFacturacionId,
            String monto,
            String estadoFiscal,
            UUID eventoSignificativoId) {

        UUID id = UUID.randomUUID();
        int correlativo = SECUENCIA.incrementAndGet();
        dsl.execute(
                """
                INSERT INTO tarifas.factura_electronica
                    (id, devengo_id, usuario_id, datos_facturacion_id, evento_significativo_id,
                     nit_emisor, sucursal, punto_venta, numero_factura, cuf, cufd,
                     fecha_emision, monto_total, monto_iva, monto_no_sujeto, moneda,
                     estado_fiscal, hash_documento)
                VALUES (?, ?, ?, ?, ?, '1234567890', 0, 1, ?, ?, 'CUFD-PRUEBA',
                        now(), ?, 0, 0, 'BOB', ?, repeat('f', 64))
                """,
                id,
                devengoId,
                usuarioId,
                datosFacturacionId,
                eventoSignificativoId,
                (long) correlativo,
                "CUF" + correlativo,
                new BigDecimal(monto),
                estadoFiscal);
        return id;
    }

    /**
     * Una contingencia abierta del punto de venta, con su plazo GUARDADO.
     *
     * <p>Devuelve su id en vez de dejar que la prueba lo busque: las contingencias no
     * se borran entre pruebas —cuelgan de facturas que son append-only por conservacion
     * (R-AUD-08)— y una consulta por «la abierta del punto de venta» encontraria varias.
     */
    UUID contingencia(int sucursal, int puntoVenta) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO tarifas.evento_significativo_sin
                    (id, codigo_evento, descripcion, sucursal, punto_venta, cufd_evento,
                     fecha_inicio, cantidad_documentos_offline, plazo_registro, estado)
                VALUES (?, '2', 'El servicio no responde', ?, ?, 'CUFD-CONT',
                        now(), 0, now() + interval '48 hours', 'ABIERTO')
                """,
                id,
                (short) sucursal,
                (short) puntoVenta);
        return id;
    }

    /**
     * Un reclamo del cliente.
     *
     * <p>Vive en {@code cumplimiento} y este servicio no lee ese esquema (invariante
     * 11). La fixtura si lo escribe: lo que la prueba verifica es el ENLACE, que un
     * reclamo favorable con monto tenga su devolucion asociada.
     */
    UUID reclamo(UUID usuarioId) {
        UUID punto = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.punto_reclamo (id, codigo, tipo, descripcion, horario, activo)
                VALUES (?, ?, 'APP', 'Punto de prueba', '24/7', true)
                """,
                punto,
                "PR-" + SECUENCIA.incrementAndGet());
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.reclamo_cliente
                    (id, codigo, usuario_id, punto_reclamo_id, categoria, producto, monto_reclamado,
                     descripcion, canal_ingreso, estado, dias_habiles_plazo, plazo_respuesta,
                     conservar_hasta)
                VALUES (?, ?, ?, ?, 'COMISION', 'BILLETERA', 18.00, 'Me cobraron de mas', 'APP',
                        'EN_ANALISIS', 5, now() + interval '5 days',
                        (now() + interval '10 years')::date)
                """,
                id,
                "REC-" + SECUENCIA.incrementAndGet(),
                usuarioId,
                punto);
        return id;
    }
}
