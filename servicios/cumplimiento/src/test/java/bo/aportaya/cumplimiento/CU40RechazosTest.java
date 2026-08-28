package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU40EvaluarLimites.EntradaLimite;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-40 · Lo que la base y el caso de uso rechazan. */
class CU40RechazosTest extends BaseDeCumplimiento {

    private UUID usuario;
    private UUID cuenta;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        cuenta = uif.cuentaBilletera(usuario, "BOB");
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        dsl.execute("DELETE FROM nucleo_financiero.consumo_limite");
        dsl.execute("DELETE FROM catalogo.limite_operativo_billetera WHERE concepto = 'RETIRO'");
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        // El saldo disponible nunca es negativo, y el limite es la otra mitad de lo
        // mismo: evaluar antes de aplicar es lo que impide que la operacion llegue a
        // dejar la cuenta en rojo.
        UUID limite = gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        gobiernoFixtura.consumo(cuenta, limite, "5000.00", 10);

        var veredicto = transaccion.execute(
                t -> limiteCU.evaluar(new EntradaLimite(cuenta, "RETIRO", new BigDecimal("1.00")), ctx));

        assertThat(veredicto.permitido()).isFalse();
        assertThat(veredicto.limitesEvaluados().get(0).disponible()).isEqualByComparingTo("0.00");
        // Y la base tampoco admite un saldo negativo en la cuenta que no lo permite.
        assertThat(rechazaLaBase(
                        "UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -1 WHERE id = ?", cuenta))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        // Denegar por omision: sin limite configurado para ese concepto y nivel, se
        // rechaza. Permitir lo no configurado convierte un olvido del catalogo en una
        // puerta abierta.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> limiteCU.evaluar(new EntradaLimite(cuenta, "RETIRO", new BigDecimal("10.00")), ctx)))
                .hasMessageContaining("deniega por omision");

        // Y la funcion de la boveda dice lo mismo desde la base.
        assertThat(rechazaLaBase("SELECT fn_lim_evaluar(?, 'RETIRO', 10)", cuenta))
                .contains("R-LIM-01");
    }

    @Test
    @DisplayName("rechaza por R-LIM-02")
    void rechazaRLIM02() {
        // Un consumo por cuenta, limite y ventana: dos filas de la misma ventana harian
        // que el acumulado dependiera de cual se lea.
        UUID limite = gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);
        gobiernoFixtura.consumo(cuenta, limite, "1000.00", 2);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.consumo_limite
                            (cuenta_billetera_id, limite_id, ventana_inicio, ventana_fin,
                             monto_acumulado, cantidad_acumulada, actualizado_en)
                        SELECT cuenta_billetera_id, limite_id, ventana_inicio, ventana_fin,
                               500, 1, now()
                          FROM nucleo_financiero.consumo_limite
                         WHERE cuenta_billetera_id = ? AND limite_id = ?
                        """,
                        cuenta,
                        limite))
                .contains("uq_consumo_ventana");
    }

    @Test
    @DisplayName("rechaza por R-LIM-03")
    void rechazaRLIM03() {
        // Los limites se versionan por vigencia y no se solapan: dos vigentes a la vez
        // para el mismo concepto, nivel y ventana harian imposible decir cual rigio.
        gobiernoFixtura.limite("RETIRO", "ESTANDAR", "MES", "5000.00", null);

        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.limite_operativo_billetera
                            (concepto, nivel_debida_diligencia, ventana, monto_maximo, moneda,
                             base_normativa, vigente_desde, activo)
                        VALUES ('RETIRO', 'ESTANDAR', 'MES', 9000, 'BOB', 'Duplicado', current_date, true)
                        """))
                .contains("ex_limite_vigencia");
    }
}
