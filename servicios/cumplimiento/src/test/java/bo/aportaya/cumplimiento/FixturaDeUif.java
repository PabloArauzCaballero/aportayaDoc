package bo.aportaya.cumplimiento;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * Las filas que los casos de uso de UIF, reportes y gobierno necesitan.
 *
 * <p>Los umbrales se siembran desde {@code sql/60_semillas/06-umbrales-uif.sql}:
 * {@code sql/aplicar.sql} monta el esquema pero no las semillas, y un umbral es
 * catalogo con base normativa (R-UIF-01). Se lee el archivo de la boveda en vez de
 * reescribir sus cifras aca, para que ninguna prueba pase contra numeros que la boveda
 * ya no tiene.
 */
class FixturaDeUif {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(90_100_000);

    private final DSLContext dsl;

    FixturaDeUif(DSLContext dsl) {
        this.dsl = dsl;
    }

    void catalogoDeUmbrales() {
        if (dsl.fetchCount(DSL.table(DSL.name("catalogo", "umbral_reporte_uif"))) > 0) {
            return;
        }
        dsl.execute(leerSemilla("sql/60_semillas/06-umbrales-uif.sql"));
    }

    private String leerSemilla(String ruta) {
        try {
            var raiz = java.nio.file.Path.of("").toAbsolutePath();
            while (raiz != null && !java.nio.file.Files.isDirectory(raiz.resolve("sql/60_semillas"))) {
                raiz = raiz.getParent();
            }
            return java.nio.file.Files.readString(raiz.resolve(ruta));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer " + ruta, e);
        }
    }

    /**
     * Un umbral propio de la prueba, para no depender del catalogo real al medir.
     *
     * <p>Nace **inactivo** y se enciende con {@link #activarUmbral}. No es capricho: el
     * disparador {@code tg_movimiento_umbrales_uif} de la boveda corre al insertar un
     * movimiento de billetera y, si hay un PCC-01 activo, intenta escribir una fila que
     * {@code ck_operelev_declaracion} rechaza — y **aborta la transaccion de billetera
     * entera**. Es el hueco H-1 del carril, y la prueba
     * {@code CU41RechazosTest.rechazaRUIF02} lo demuestra. Mientras exista, la fixtura
     * arma las transacciones antes de encender el umbral.
     */
    UUID umbral(String formulario, String concepto, boolean acumulado, String umbralUsd, Integer ventanaDias) {
        // `ex_umbral_vigencia` impide dos vigencias solapadas del mismo formulario y
        // concepto: si la prueba anterior ya lo sembro, se reusa apagado en vez de
        // duplicarlo. Es la misma regla que impide dos umbrales contradictorios en
        // produccion (R-UIF-01), y respetarla aca es parte de probar contra ella.
        var existente = dsl.fetchOne(
                """
                SELECT id FROM catalogo.umbral_reporte_uif
                 WHERE formulario = ? AND concepto_operacion = ? AND es_acumulado = ?
                """,
                formulario,
                concepto,
                acumulado);
        if (existente != null) {
            UUID previo = existente.get(0, UUID.class);
            dsl.execute(
                    "UPDATE catalogo.umbral_reporte_uif SET activo = false, vigente_desde = current_date - 400 WHERE id = ?",
                    previo);
            return previo;
        }
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO catalogo.umbral_reporte_uif
                    (id, formulario, inciso, concepto_operacion, es_acumulado, umbral_usd,
                     ventana_dias_calendario, exige_declaracion_origen_destino, reinicia_tras_superar,
                     base_normativa, vigente_desde, activo)
                VALUES (?, ?, ?, ?, ?, ?::numeric, ?, ?, true, ?, current_date - 400, false)
                """,
                id,
                formulario,
                "z" + (SECUENCIA.incrementAndGet() % 9),
                concepto,
                acumulado,
                umbralUsd,
                ventanaDias == null ? null : ventanaDias.shortValue(),
                formulario.startsWith("PCC"),
                "Prueba de carril — " + formulario + " " + concepto);
        return id;
    }

    /** Enciende el umbral, ya con las transacciones de la prueba escritas. */
    void activarUmbral(UUID umbralId) {
        dsl.execute("UPDATE catalogo.umbral_reporte_uif SET activo = true WHERE id = ?", umbralId);
    }

    /**
     * Escribe una transaccion aplicada con el umbral apagado, y lo vuelve a encender.
     *
     * <p>**No es una comodidad: es la unica forma de armar el escenario hoy.** Con un
     * PCC-01 activo, {@code tg_movimiento_umbrales_uif} intenta escribir una fila que
     * {@code ck_operelev_declaracion} rechaza y aborta la recarga entera (hueco H-1).
     * Apagar y encender el catalogo alrededor de la escritura NO relaja ninguna
     * restriccion: la fila que despues escribe el caso de uso pasa por todas.
     */
    UUID transaccionConUmbralApagado(
            UUID umbralId, UUID cuentaId, String monto, String moneda, String tipo, OffsetDateTime cuando) {
        dsl.execute("UPDATE catalogo.umbral_reporte_uif SET activo = false WHERE id = ?", umbralId);
        try {
            return transaccionAplicada(cuentaId, monto, moneda, tipo, cuando);
        } finally {
            activarUmbral(umbralId);
        }
    }

    /**
     * La cotizacion del dia.
     *
     * <p>Sin ella {@code fn_fx_a_usd} corta cualquier operacion en moneda distinta de
     * dolares, y con razon: un umbral que no se puede convertir no se puede reportar
     * (R-UIF-04).
     */
    void tipoDeCambio(String origen, String cotizacion) {
        dsl.execute(
                """
                INSERT INTO catalogo.tipo_cambio (id, moneda_origen, moneda_destino, fecha, tipo_cambio, fuente, cargado_en)
                VALUES (gen_random_uuid(), ?, 'USD', current_date, ?::numeric, 'BCB', now())
                ON CONFLICT DO NOTHING
                """,
                origen,
                cotizacion);
    }

    UUID catalogoDeReporte(String codigo, String organismo, int plazoDias) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.catalogo_reporte_regulatorio
                    (id, codigo, organismo, nombre, periodicidad, formato, plazo_dias, base_normativa,
                     obligatorio, activo)
                VALUES (?, ?, ?, ?, 'MENSUAL', 'TXT', ?, 'Instructivo EIF art. 52', true, true)
                """,
                id,
                codigo,
                organismo,
                "Reporte " + codigo,
                (short) plazoDias);
        return id;
    }

    /**
     * Una transaccion de billetera aplicada, con su movimiento.
     *
     * <p>Hace falta porque {@code registro_operacion_relevante.transaccion_id} tiene
     * clave foranea contra ella. La escribe la fixtura y no el caso de uso: el esquema
     * es de {@code nucleo_financiero} y este servicio no lo toca (invariante 11).
     */
    UUID transaccionAplicada(UUID cuentaId, String monto, String moneda, String tipo, OffsetDateTime cuando) {
        UUID id = UUID.randomUUID();
        UUID puente = cuentaPuente(moneda);
        // Todo en UNA transaccion: fn_bil_transaccion_cuadrada es un disparador de
        // restriccion diferido, y con cada sentencia en su propia transaccion se
        // dispararia antes de que existan los movimientos.
        dsl.transaction(config -> {
            var t = DSL.using(config);
            t.execute(
                    """
                    INSERT INTO nucleo_financiero.transaccion_billetera
                        (id, tipo, estado, moneda, monto_total, origen_tipo, origen_id,
                         clave_idempotencia, canal, ocurrida_en, registrada_en)
                    VALUES (?, ?, 'APLICADA', ?, ?::numeric, 'ORDEN_RECARGA', ?, ?, 'APP', ?::timestamptz, ?::timestamptz)
                    """,
                    id,
                    tipo,
                    moneda,
                    monto,
                    UUID.randomUUID(),
                    "uif-" + id,
                    cuando.toString(),
                    cuando.toString());
            // Una recarga ACREDITA al titular y debita el puente de custodia, que es
            // la unica cuenta que admite saldo negativo. Al reves, el saldo del titular
            // quedaria negativo y ck_cuenta_saldo_no_negativo lo rechazaria — con razon.
            movimiento(t, id, puente, "DEBITO", monto, 1);
            movimiento(t, id, cuentaId, "CREDITO", monto, 2);
        });
        return id;
    }

    private void movimiento(DSLContext t, UUID transaccionId, UUID cuentaId, String sentido, String monto, int orden) {
        t.execute(
                """
                INSERT INTO nucleo_financiero.movimiento_billetera
                    (id, transaccion_id, cuenta_billetera_id, orden, sentido, monto,
                     saldo_disponible_posterior, saldo_retenido_posterior, glosa, registrado_en)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?::numeric, 0, 0, 'Prueba de carril', now())
                """,
                transaccionId,
                cuentaId,
                (short) orden,
                sentido,
                monto);
    }

    /** La cuenta puente de custodia: la contraparte que hace cuadrar la partida doble. */
    private UUID cuentaPuente(String moneda) {
        var existente = dsl.fetchOne(
                "SELECT id FROM nucleo_financiero.cuenta_billetera WHERE tipo = 'PUENTE_CUSTODIA' AND moneda = ? LIMIT 1",
                moneda);
        if (existente != null) {
            return existente.get(0, UUID.class);
        }
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_billetera
                    (id, numero_cuenta, tipo, moneda, estado, nivel_debida_diligencia,
                     saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
                VALUES (?, ?, 'PUENTE_CUSTODIA', ?, 'ACTIVA', 'ESTANDAR', 0, 0, true, now(), 1)
                """,
                id,
                "PUENTE-" + SECUENCIA.incrementAndGet(),
                moneda);
        return id;
    }

    UUID cuentaBilletera(UUID usuarioId, String moneda) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_billetera
                    (id, numero_cuenta, tipo, usuario_id, moneda, estado, nivel_debida_diligencia,
                     saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
                VALUES (?, ?, 'USUARIO', ?, ?, 'ACTIVA', 'ESTANDAR', 0, 0, false, now(), 1)
                """,
                id,
                "CB-" + SECUENCIA.incrementAndGet(),
                usuarioId,
                moneda);
        return id;
    }

    /** Deja el esquema como estaba. Sin SQL concatenado: lo prohibe el contrato. */
    void limpiar() {
        for (String[] tabla : new String[][] {
            {"cumplimiento", "instancia_reclamo"},
            {"cumplimiento", "reclamo_cliente"},
            {"cumplimiento", "punto_reclamo"},
            {"cumplimiento", "prueba_continuidad"},
            {"cumplimiento", "plan_continuidad"},
            {"cumplimiento", "acta_comite"},
            {"cumplimiento", "comite_gobierno"}
        }) {
            dsl.deleteFrom(DSL.table(DSL.name(tabla[0], tabla[1]))).execute();
        }
    }
}
