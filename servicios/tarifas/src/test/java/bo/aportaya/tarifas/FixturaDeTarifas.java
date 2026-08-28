package bo.aportaya.tarifas;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/**
 * Las filas que las pruebas de tarifas necesitan.
 *
 * <p>Cada fixtura **inserta lo suyo**: {@code sql/aplicar.sql} crea el esquema pero no
 * siembra, asi que un UPDATE sobre una fila de catalogo que no existe no falla — no
 * hace nada, y la prueba pasa por la razon equivocada.
 */
class FixturaDeTarifas {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(80_000_000);

    private final DSLContext dsl;

    FixturaDeTarifas(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Tarifa', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "TAR-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /**
     * Un tarifario vigente. El codigo se hace unico para que el EXCLUDE no choque.
     *
     * <p><b>Nace en BORRADOR.</b> {@code tg_concepto_tarifa_inmutable} rechaza insertar
     * conceptos en un tarifario ya VIGENTE, que es exactamente R-TAR-02: se publica una
     * version nueva, no se edita la que rige. Quien arma el escenario carga los
     * conceptos y despues llama a {@link #activar}.
     */
    UUID tarifarioVigente(String codigo) {
        return tarifarioBorrador(codigo);
    }

    /** Pasa el tarifario a VIGENTE, con lo que ck_tarifario_publicado exige. */
    void activar(UUID tarifarioId) {
        dsl.execute(
                """
                UPDATE catalogo.tarifario
                   SET estado = 'VIGENTE',
                       vigente_desde = now() - interval '1 day',
                       publicado_en = now() - interval '1 day',
                       url_publicacion = 'https://aportaya.test/tarifario',
                       hash_documento = repeat('a', 64)
                 WHERE id = ?
                """,
                tarifarioId);
    }

    /** Un tarifario en borrador: el que se puede editar. */
    UUID tarifarioBorrador(String codigo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO catalogo.tarifario
                    (id, codigo, version, nombre, estado, moneda_base, vigente_desde, dias_preaviso)
                VALUES (?, ?, 1, 'Borrador de prueba', 'BORRADOR', 'BOB', now(), 30)
                """,
                id,
                codigo);
        return id;
    }

    /** El codigo del hecho es unico global: cada prueba tiene que traer el suyo. */
    /** Un grupo minimo: lo que la tarifa congelada exige por clave foranea. */
    UUID grupo() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.grupo
                    (id, codigo_publico, nombre, monto_aporte, moneda, periodicidad, dia_cobro,
                     num_periodos, cupos_totales, cupos_ocupados, fecha_inicio, fecha_fin_estimada,
                     estado, tipo_conformacion, modalidad_turnos, visibilidad, es_autogestionado,
                     requiere_kyc_minimo, reputacion_minima, dias_gracia, aplica_recargo_mora,
                     usa_fondo_garantia, porcentaje_fondo_garantia, quorum_decisiones)
                VALUES (?, ?, 'Grupo de prueba', 500.00, 'BOB', 'MENSUAL', 5, 3, 3, 0,
                        current_date, current_date + 90, 'ACTIVO', 'MANUAL_POR_INVITACION', 'SORTEO_ALEATORIO', 'PRIVADO',
                        true, 'BASICO', 0, 3, true, false, 0.00, 0.600)
                """,
                id,
                "GRP-" + id.toString().substring(0, 8));
        return id;
    }

    /**
     * Una deduccion de entrega, con toda la cadena que su clave foranea exige.
     *
     * <p>Es larga a proposito: se podria «probar» R-TAR-06 con un UUID inventado y un
     * assert sobre el nombre del indice, pero eso no verifica que la regla funcione —
     * verifica que el indice existe. La cadena real es lo que hace que la prueba
     * pruebe algo.
     */
    UUID deduccionDeEntrega(UUID grupoId) {
        UUID usuario = usuario();
        UUID participante = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.participante
                    (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                     reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                VALUES (?, ?, ?, 'ACTIVO', false, now(), 50, 0, 0)
                """,
                participante,
                grupoId,
                usuario);

        UUID cupo = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.cupo (id, grupo_id, numero, participante_id, estado, fraccion, asignado_en)
                VALUES (?, ?, 1, ?, 'OCUPADO', 1.0, now())
                """,
                cupo,
                grupoId,
                participante);

        UUID periodo = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.periodo
                    (id, grupo_id, numero, fecha_inicio, fecha_limite_pago, fecha_fin_gracia,
                     fecha_entrega_prevista, estado, monto_objetivo, monto_recaudado, cupos_morosos)
                VALUES (?, ?, 1, current_date - 30, current_date + 10, current_date + 13,
                        current_date + 40, 'ABIERTO', 1500.00, 0, 0)
                """,
                periodo,
                grupoId);

        UUID turno = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.turno
                    (id, grupo_id, periodo_id, cupo_id, orden_asignado, estado, criterio_asignacion,
                     monto_estimado_cobro)
                VALUES (?, ?, ?, ?, 1, 'PROGRAMADO', 'SORTEO', 1500.00)
                """,
                turno,
                grupoId,
                periodo,
                cupo);

        UUID entrega = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO entregas.entrega_fondo
                    (id, grupo_id, periodo_id, turno_id, cupo_id, beneficiario_participante_id,
                     monto_bolsa_bruto, total_deducciones, monto_neto_a_entregar,
                     monto_efectivamente_entregado, moneda, estado, metodo_desembolso,
                     fecha_programada, version)
                VALUES (?, ?, ?, ?, ?, ?, 1500.00, 18.00, 1482.00, 0, 'BOB', 'PROGRAMADA',
                        'BILLETERA_MOVIL', current_date, 0)
                """,
                entrega,
                grupoId,
                periodo,
                turno,
                cupo,
                participante);

        UUID deduccion = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO entregas.deduccion_entrega
                    (id, entrega_id, tipo, descripcion, monto, es_obligatoria, aplicada_en)
                VALUES (?, ?, 'COMISION_PLATAFORMA', 'Comision de la plataforma', 18.00, true, now())
                """,
                deduccion,
                entrega);
        return deduccion;
    }

    /** El snapshot congelado del grupo: el precio pactado que no se mueve. */
    void congelarTarifa(UUID grupoId, UUID tarifarioId) {
        dsl.execute(
                """
                INSERT INTO tarifas.tarifa_congelada_grupo
                    (id, grupo_id, tarifario_id, snapshot_conceptos, hash_snapshot, congelada_en)
                VALUES (gen_random_uuid(), ?, ?, '[]'::jsonb, repeat('c', 64), now())
                """,
                grupoId,
                tarifarioId);
    }

    /** El codigo del hecho es unico global: cada prueba tiene que traer el suyo. */
    UUID hechoGenerador(String codigo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO tarifas.catalogo_hecho_generador
                    (id, codigo, descripcion, entidad_evento, campo_monto_base, unidad_conteo,
                     modulo_origen, activo)
                VALUES (?, ?, 'Hecho de prueba', 'entrega_fondo', 'monto_bruto', 'OPERACION', '11', true)
                """,
                id,
                codigo);
        return id;
    }

    UUID politicaDeRedondeo(String codigo, String unidadMinima, String modo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO tarifas.politica_redondeo (id, codigo, moneda, unidad_minima, modo, aplica_a)
                VALUES (?, ?, 'BOB', ?, ?, 'COMISION')
                """,
                id,
                codigo,
                new BigDecimal(unidadMinima),
                modo);
        return id;
    }

    /**
     * Un concepto porcentual con piso y techo: el caso del CU-30.
     *
     * <p>El sujeto obligado importa mas de lo que parece:
     * {@code ck_concepto_precio_final} prohibe que un concepto gravado se le muestre al
     * BENEFICIARIO o al PAGADOR sin el impuesto adentro. Es R-TAR-12 en la DDL —
     * mostrar un precio al que despues se le suma 13% es exactamente lo que impide.
     */
    UUID conceptoPorcentual(
            UUID tarifarioId,
            UUID hechoId,
            UUID redondeoId,
            UUID cuentaIngresoId,
            String codigo,
            String porcentaje,
            String piso,
            String techo,
            boolean gravadoIva,
            boolean precioIncluyeImpuesto) {

        return conceptoPorcentual(
                tarifarioId,
                hechoId,
                redondeoId,
                cuentaIngresoId,
                codigo,
                porcentaje,
                piso,
                techo,
                gravadoIva,
                precioIncluyeImpuesto,
                "BENEFICIARIO_DEL_TURNO");
    }

    UUID conceptoPorcentual(
            UUID tarifarioId,
            UUID hechoId,
            UUID redondeoId,
            UUID cuentaIngresoId,
            String codigo,
            String porcentaje,
            String piso,
            String techo,
            boolean gravadoIva,
            boolean precioIncluyeImpuesto,
            String sujetoObligado) {

        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO tarifas.concepto_tarifa
                    (id, tarifario_id, hecho_generador_id, politica_redondeo_id, cuenta_ingreso_id,
                     codigo, nombre_comercial, descripcion_usuario, metodo_calculo, base_calculo,
                     valor_porcentual, monto_minimo, monto_maximo, sujeto_obligado, forma_cobro,
                     momento_cobro, gravado_iva, gravado_it, precio_incluye_impuesto,
                     orden_aplicacion, activo)
                VALUES (?, ?, ?, ?, ?, ?, 'Comision de servicio', 'Lo que cobra la plataforma',
                        'PORCENTUAL', 'MONTO_BOLSA_BRUTO', ?, ?, ?, ?,
                        'DEDUCCION_DE_ENTREGA', 'AL_LIQUIDAR_ENTREGA', ?, false, ?, 1, true)
                """,
                id,
                tarifarioId,
                hechoId,
                redondeoId,
                cuentaIngresoId,
                codigo,
                new BigDecimal(porcentaje),
                piso == null ? null : new BigDecimal(piso),
                techo == null ? null : new BigDecimal(techo),
                sujetoObligado,
                gravadoIva,
                precioIncluyeImpuesto);
        return id;
    }

    /** Una cuenta contable de ingreso: sin ella el devengo no puede armar su asiento. */
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

    OffsetDateTime ahora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Borra lo que se puede borrar.
     *
     * <p>{@code devengo_comision} <b>no se borra</b>: es append-only por trigger, para
     * DELETE tambien (R-AUD-01). Tampoco hace falta — cada prueba trae su tarifario y
     * sus conceptos, asi que lo que quedo de la anterior no le suma ni le resta. Lo que
     * si hay que limpiar es lo unico por clave: los codigos de tarifario, hecho y
     * segmento chocarian entre pruebas.
     *
     * <p>Y por eso mismo tampoco se pueden borrar sus hijos: {@code cargo_comision},
     * {@code calculo_impuesto}, {@code cuenta_por_cobrar_comision}, las devoluciones y
     * las facturas cuelgan de un devengo que sigue ahi.
     */
    void limpiar() {
        // El nombre de la tabla va como IDENTIFICADOR de jOOQ, no concatenado: una
        // consulta armada con `+` es la forma en que se cuela una inyeccion, y la
        // prohibicion no distingue entre codigo de prueba y de produccion — porque el
        // habito si se traslada.
        for (String[] tabla : new String[][] {
            {"tarifas", "liquidacion_ingresos"},
            {"tarifas", "costo_proveedor_operacion"},
            {"tarifas", "tarifa_congelada_grupo"},
            {"entregas", "deduccion_entrega"},
            {"entregas", "entrega_fondo"},
            {"grupos", "turno"},
            {"grupos", "periodo"},
            {"grupos", "cupo"},
            {"grupos", "participante"},
            {"grupos", "grupo"},
            {"cumplimiento", "reclamo_cliente"},
            {"cumplimiento", "punto_reclamo"},
            {"tarifas", "simulacion_tarifa"},
            {"tarifas", "cambio_tarifario"},
            {"tarifas", "segmento_comercial"},
            {"tarifas", "evento_dominio"},
            {"tarifas", "evento_consumido"},
            {"catalogo", "impuesto"}
        }) {
            dsl.deleteFrom(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name(tabla[0], tabla[1])))
                    .execute();
        }
    }
}
