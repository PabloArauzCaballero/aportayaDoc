package bo.aportaya.nucleofinanciero;

import java.math.BigDecimal;
import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Filas mínimas de {@code cuenta_contable} para probar CU-24 sin depender de
 * {@code sql/60_semillas} (que no está sembrado en la base de las pruebas de
 * integración — ver {@code BaseDePrueba}).
 *
 * <p>Se inserta como dueño de la base, igual que el resto de las fixturas del
 * proyecto: sembrar no es lo que se está probando.
 */
final class FixturaDeNucleoFinanciero {

    /** Un telefono E.164 distinto por usuario: uq_usuario_telefono_e164 no perdona. */
    private static final java.util.concurrent.atomic.AtomicInteger SECUENCIA =
            new java.util.concurrent.atomic.AtomicInteger(50_000_000);

    private final DSLContext dsl;

    FixturaDeNucleoFinanciero(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Una cuenta de movimiento del plan de cuentas, con saldo inicial en cero. */
    UUID cuentaDeMovimiento(String codigo, String tipo, String naturaleza) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_contable
                    (id, codigo, nombre, tipo, naturaleza, nivel, es_cuenta_de_movimiento, saldo)
                VALUES (?, ?, ?, ?, ?, 1, true, 0.00)
                """,
                id,
                codigo,
                "Cuenta de prueba " + codigo,
                tipo,
                naturaleza);
        return id;
    }

    /** {@code R-CTB-02}: una cuenta sumarizadora no recibe movimientos directos. */
    UUID cuentaSumarizadora(String codigo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_contable
                    (id, codigo, nombre, tipo, naturaleza, nivel, es_cuenta_de_movimiento, saldo)
                VALUES (?, ?, ?, 'ACTIVO', 'DEUDORA', 1, false, 0.00)
                """,
                id,
                codigo,
                "Cuenta sumarizadora " + codigo);
        return id;
    }

    BigDecimal saldoDe(UUID cuentaId) {
        return (BigDecimal) dsl.fetchOne("SELECT saldo FROM nucleo_financiero.cuenta_contable WHERE id = ?", cuentaId)
                .get(0);
    }

    /** Un usuario real en identidad: la cuenta lo referencia por clave foranea. */
    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Billetera', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "BIL-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /** La politica por omision: sin ella la cuenta no se puede abrir. */
    UUID politica() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.politica_billetera
                    (id, codigo, moneda, dias_inactividad_para_limitar, permite_transferencia_p2p,
                     requiere_mfa_desde, ventana_enfriamiento_retiro_horas, dias_vigencia_retencion,
                     permite_saldo_negativo, vigente_desde)
                VALUES (?, ?, 'BOB', 365, true, 500.00, 24, 30, false, now() - interval '1 day')
                """,
                id,
                "POL-" + id.toString().substring(0, 8));
        return id;
    }

    /**
     * Una billetera de usuario con el saldo pedido.
     *
     * <p>El saldo se pone directo porque estas pruebas verifican los limites y las
     * reglas, no como se llego a ese saldo: llegar «bien» exigiria correr media
     * docena de casos de uso que todavia no existen.
     */
    UUID billetera(UUID usuarioId, String nivel, BigDecimal disponible) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_billetera
                    (id, numero_cuenta, tipo, usuario_id, politica_billetera_id, moneda, estado,
                     nivel_debida_diligencia, saldo_disponible, saldo_retenido,
                     permite_saldo_negativo, fecha_apertura, version)
                VALUES (?, ?, 'USUARIO', ?, ?, 'BOB', 'ACTIVA', ?, ?, 0, false, now(), 0)
                """,
                id,
                "BOB-" + id.toString().substring(0, 12),
                usuarioId,
                politica(),
                nivel,
                disponible);
        return id;
    }

    /** Un limite del catalogo para ese concepto, nivel y ventana. */
    UUID limite(String concepto, String nivel, String ventana, BigDecimal montoMaximo, Integer cantidadMaxima) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO catalogo.limite_operativo_billetera
                    (id, concepto, nivel_debida_diligencia, ventana, monto_maximo, cantidad_maxima,
                     moneda, base_normativa, vigente_desde, activo)
                VALUES (?, ?, ?, ?, ?, ?, 'BOB', 'ASFI 540/2025', current_date - 30, true)
                """,
                id,
                concepto,
                nivel,
                ventana,
                montoMaximo,
                cantidadMaxima);
        return id;
    }

    void limpiarBilleteras() {
        dsl.execute("DELETE FROM nucleo_financiero.consumo_limite");
        dsl.execute("DELETE FROM nucleo_financiero.movimiento_billetera");
        dsl.execute("DELETE FROM nucleo_financiero.transaccion_billetera");
        dsl.execute("DELETE FROM nucleo_financiero.cuenta_billetera");
        dsl.execute("DELETE FROM nucleo_financiero.politica_billetera");
        dsl.execute("DELETE FROM catalogo.limite_operativo_billetera");
        dsl.execute("DELETE FROM nucleo_financiero.evento_dominio");
    }
}
