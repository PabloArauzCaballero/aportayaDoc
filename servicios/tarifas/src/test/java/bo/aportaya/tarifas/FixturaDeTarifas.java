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
