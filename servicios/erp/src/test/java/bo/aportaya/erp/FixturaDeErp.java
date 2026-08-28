package bo.aportaya.erp;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** Las filas que las pruebas de erp necesitan. */
class FixturaDeErp {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(70_700_000);

    private final DSLContext dsl;

    FixturaDeErp(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Erp', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'COMPLETO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "ERP-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /**
     * Una cuenta contable de movimiento.
     *
     * <p>De movimiento a proposito: {@code fn_ctb_cuenta_de_movimiento} rechaza un
     * asiento contra una sumarizadora, y con razon — una sumarizadora es un total, no un
     * destino.
     */
    UUID cuenta(String codigo, String tipo, String naturaleza) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_contable
                    (id, codigo, nombre, tipo, naturaleza, nivel, es_cuenta_de_movimiento, saldo)
                VALUES (?, ?, ?, ?, ?, 4, true, 0)
                """,
                id,
                codigo,
                "Cuenta " + codigo,
                tipo,
                naturaleza);
        return id;
    }

    /**
     * Un asiento con sus dos patas, dentro del periodo.
     *
     * <p>Lo escribe {@code nucleo-financiero} en produccion (invariante 12); la fixtura
     * lo arma porque los estados financieros de CU-106 y el cuadre de CU-100 se calculan
     * desde el mayor y sin asientos no hay nada que sumar.
     */
    void asiento(UUID periodoId, UUID cuentaDebe, UUID cuentaHaber, String monto, UUID registradoPor) {
        UUID id = UUID.randomUUID();
        dsl.transaction(config -> {
            var t = DSL.using(config);
            t.execute(
                    """
                    INSERT INTO nucleo_financiero.asiento_contable
                        (id, numero, fecha, glosa, origen_tipo, origen_id, periodo_contable_id,
                         estado, registrado_por)
                    VALUES (?, ?, current_date, 'Asiento del carril', 'AJUSTE', gen_random_uuid(), ?,
                            'CONFIRMADO', ?)
                    """,
                    id,
                    (long) SECUENCIA.incrementAndGet(),
                    periodoId,
                    registradoPor);
            movimiento(t, id, cuentaDebe, monto, "0");
            movimiento(t, id, cuentaHaber, "0", monto);
        });
    }

    private void movimiento(DSLContext t, UUID asientoId, UUID cuentaId, String debe, String haber) {
        t.execute(
                """
                INSERT INTO nucleo_financiero.movimiento_contable (id, asiento_id, cuenta_id, debe, haber, descripcion)
                VALUES (gen_random_uuid(), ?, ?, ?::numeric, ?::numeric, 'Movimiento del carril')
                """,
                asientoId,
                cuentaId,
                debe,
                haber);
    }

    UUID centroDeCosto(String codigo, String tipo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO erp.centro_costo (id, codigo, nombre, tipo, activo)
                VALUES (?, ?, ?, ?, true)
                """,
                id,
                codigo,
                "Centro " + codigo,
                tipo);
        return id;
    }

    UUID tercero(String tipo, String numeroDocumento) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO erp.tercero_comercial (id, tipo, razon_social, numero_documento, estado)
                VALUES (?, ?, ?, ?, 'ACTIVO')
                """,
                id,
                tipo,
                "Tercero " + numeroDocumento,
                numeroDocumento);
        return id;
    }

    /**
     * Una cuenta por cobrar ya nacida INCOBRABLE.
     *
     * <p>Nace asi porque **no puede llegar a serlo**: {@code cuenta_por_cobrar} es
     * append-only, y marcarla incobrable seria un UPDATE. Queda declarado como hueco del
     * carril; mientras tanto, el estado se decide al abrirla.
     */
    UUID cuentaIncobrable(UUID terceroId, String monto) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO erp.cuenta_por_cobrar
                    (id, origen_tipo, origen_id, tercero_comercial_id, monto, moneda,
                     monto_cobrado, fecha_vencimiento, estado)
                VALUES (?, 'OTRO', gen_random_uuid(), ?, ?::numeric, 'BOB', 0,
                        current_date - 120, 'INCOBRABLE')
                """,
                id,
                terceroId,
                monto);
        return id;
    }

    /** Una cuenta sumarizadora: un total, no un destino de asiento (R-CTB-02). */
    UUID cuentaSumarizadora(String codigo, String tipo, String naturaleza) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_contable
                    (id, codigo, nombre, tipo, naturaleza, nivel, es_cuenta_de_movimiento, saldo)
                VALUES (?, ?, ?, ?, ?, 1, false, 0)
                """,
                id,
                codigo,
                "Sumarizadora " + codigo,
                tipo,
                naturaleza);
        return id;
    }

    /** Una plantilla de asiento con dos lineas balanceadas: un debe y un haber. */
    UUID plantillaDeAsiento(String codigo, UUID cuentaDebe, UUID cuentaHaber, UUID creadaPor) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO erp.asiento_plantilla (id, codigo, nombre, glosa, periodicidad, activa, creada_por)
                VALUES (?, ?, ?, 'Plantilla del carril', 'MANUAL', true, ?)
                """,
                id,
                codigo,
                "Plantilla " + codigo,
                creadaPor);
        linea(id, cuentaDebe, "DEBE", 1);
        linea(id, cuentaHaber, "HABER", 2);
        return id;
    }

    private void linea(UUID plantillaId, UUID cuentaId, String tipo, int orden) {
        dsl.execute(
                """
                INSERT INTO erp.linea_plantilla_asiento
                    (id, plantilla_id, cuenta_contable_id, tipo_movimiento, orden)
                VALUES (gen_random_uuid(), ?, ?, ?, ?)
                """,
                plantillaId,
                cuentaId,
                tipo,
                (short) orden);
    }

    UUID categoriaDeActivo(String codigo, int vidaUtilMeses, UUID cuentaActivo, UUID cuentaDep, UUID cuentaGasto) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO erp.categoria_activo_fijo
                    (id, codigo, nombre, vida_util_meses, metodo_depreciacion,
                     cuenta_activo_id, cuenta_depreciacion_id, cuenta_gasto_depreciacion_id)
                VALUES (?, ?, ?, ?, 'LINEA_RECTA', ?, ?, ?)
                """,
                id,
                codigo,
                "Categoria " + codigo,
                (short) vidaUtilMeses,
                cuentaActivo,
                cuentaDep,
                cuentaGasto);
        return id;
    }

    UUID activo(UUID categoriaId, String costo, String residual, String acumulada) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO erp.activo_fijo
                    (id, categoria_activo_fijo_id, codigo_inventario, descripcion, fecha_adquisicion,
                     costo_adquisicion, moneda, valor_residual, depreciacion_acumulada, estado)
                VALUES (?, ?, ?, 'Activo del carril', current_date - 400, ?::numeric, 'BOB',
                        ?::numeric, ?::numeric, 'ACTIVO')
                """,
                id,
                categoriaId,
                "INV-" + SECUENCIA.incrementAndGet(),
                costo,
                residual,
                acumulada);
        return id;
    }

    /** Deja el esquema como estaba. Sin SQL concatenado: lo prohibe el contrato. */
    void limpiar() {
        for (String[] tabla : new String[][] {
            {"erp", "depreciacion_activo"},
            {"erp", "activo_fijo"},
            {"erp", "categoria_activo_fijo"},
            {"erp", "estado_financiero_generado"},
            {"erp", "cierre_periodo_contable"}
        }) {
            dsl.deleteFrom(DSL.table(DSL.name(tabla[0], tabla[1]))).execute();
        }
    }
}
