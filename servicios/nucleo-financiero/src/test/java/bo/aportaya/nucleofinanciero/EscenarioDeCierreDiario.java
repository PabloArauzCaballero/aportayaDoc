package bo.aportaya.nucleofinanciero;

import bo.aportaya.nucleofinanciero.aplicacion.CU51EjecutarCierreDiario.EntradaCierre;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;

/**
 * El escenario que comparten las pruebas de CU-51: fechas propias por prueba y los
 * asientos que bloquean o dejan pasar un cierre.
 *
 * <p>Cada prueba cierra un dia distinto a proposito: {@code saldo_diario_billetera} es
 * unico por cuenta y fecha (R-AUD-07) y append-only, asi que dos pruebas sobre el mismo
 * dia chocarian contra la foto de la otra y el fallo diria algo que no es.
 */
abstract class EscenarioDeCierreDiario extends BaseDeBilletera {

    protected static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        borrarCierres();
        fixtura.limpiarBilleteras();
    }

    /** La fecha del cierre, en UTC: {@code LocalDate.now()} usa la zona de la maquina. */
    protected LocalDate hoy() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Una fecha propia por prueba.
     *
     * <p>{@code saldo_diario_billetera} es unico por cuenta y fecha (R-AUD-07) y
     * append-only: si dos pruebas cerraran el mismo dia, la segunda chocaria contra la
     * foto de la primera y el fallo diria algo que no es.
     */
    protected LocalDate diaDe(int desplazamiento) {
        return hoy().minusDays(desplazamiento);
    }

    protected void borrarCierres() {
        dsl.execute("DELETE FROM nucleo_financiero.cierre_diario");
    }

    protected EntradaCierre entrada(LocalDate fecha, int excepciones, boolean custodiaCuadrada) {
        return new EntradaCierre(
                fecha,
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                4,
                excepciones,
                custodiaCuadrada);
    }

    /** Un asiento sin confirmar en la fecha: es lo que bloquea el cierre. */
    protected UUID asientoEnBorrador(LocalDate fecha) {
        UUID cuenta = contable.cuentaDeMovimiento(codigoCorto(), "ACTIVO", "DEUDORA");
        UUID contrapartida = contable.cuentaDeMovimiento(codigoCorto(), "INGRESO", "ACREEDORA");
        UUID asientoId = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.asiento_contable
                    (id, fecha, glosa, origen_tipo, origen_id, estado)
                VALUES (?, ?, 'sin confirmar', 'AJUSTE', gen_random_uuid(), 'BORRADOR')
                """,
                asientoId,
                fecha);
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.movimiento_contable
                    (id, asiento_id, cuenta_id, debe, haber, descripcion)
                VALUES (gen_random_uuid(), ?, ?, 10.00, 0.00, 'debe'),
                       (gen_random_uuid(), ?, ?, 0.00, 10.00, 'haber')
                """,
                asientoId,
                cuenta,
                asientoId,
                contrapartida);
        return asientoId;
    }

    protected String codigoCorto() {
        return String.valueOf(System.nanoTime()).substring(8, 14);
    }
}
