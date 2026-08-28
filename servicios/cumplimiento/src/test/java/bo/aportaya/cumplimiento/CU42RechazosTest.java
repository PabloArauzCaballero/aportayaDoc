package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU41RegistrarPcc01.EntradaUmbral;
import bo.aportaya.cumplimiento.dominio.ConceptoRog;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-42 · Lo que la base y el caso de uso rechazan. */
class CU42RechazosTest extends BaseDeCumplimiento {

    private UUID usuario;
    private UUID cuenta;
    private UUID umbralId;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        cuenta = uif.cuentaBilletera(usuario, "USD");
        umbralId = uif.umbral("ROG-01", "EFECTIVO", false, "0.00", null);
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaUmbral retiro(String monto, BigDecimal tipoDeCambio, String moneda) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID tx = uif.transaccionConUmbralApagado(umbralId, cuenta, monto, "USD", "RETIRO", ahora);
        return new EntradaUmbral(
                usuario,
                tx,
                "EFECTIVO",
                new BigDecimal(monto),
                moneda,
                tipoDeCambio,
                BigDecimal.ZERO,
                null,
                LocalDate.now(ZoneOffset.UTC),
                LocalDate.now(ZoneOffset.UTC),
                ahora,
                false,
                null,
                null,
                null);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        var salida = transaccion.execute(t -> rogCU.registrar(retiro("500.00", BigDecimal.ONE, "USD"), ctx));
        UUID registroId = salida.registros().get(0).registroId();

        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.registro_operacion_relevante SET monto = 1 WHERE id = ?", registroId))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM cumplimiento.registro_operacion_relevante WHERE id = ?", registroId))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-UIF-01")
    void rechazaRUIF01() {
        // La exclusion solo rige sobre los ACTIVOS: dos umbrales apagados pueden
        // convivir, dos vigentes no. Es la diferencia entre archivo y norma en curso.
        uif.activarUmbral(umbralId);

        // Vigencias sin solape por formulario, concepto y acumulacion: dos umbrales
        // vigentes a la vez para el mismo hecho harian imposible decir cual se aplico.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.umbral_reporte_uif
                            (formulario, inciso, concepto_operacion, es_acumulado, umbral_usd,
                             ventana_dias_calendario, exige_declaracion_origen_destino, reinicia_tras_superar,
                             base_normativa, vigente_desde, activo)
                        VALUES ('ROG-01', 'z', 'EFECTIVO', false, 5000, NULL, false, true,
                                'Art. 53 duplicado', current_date, true)
                        """))
                .contains("ex_umbral_vigencia");
    }

    @Test
    @DisplayName("rechaza por R-UIF-02")
    void rechazaRUIF02() {
        // La clasificacion del concepto es la de la boveda, no una interpretacion
        // propia: si difirieran, el mismo hecho iria a dos formularios distintos.
        var esperado = dsl.fetchOne("SELECT fn_uif_concepto('RETIRO')").get(0, String.class);
        assertThat(ConceptoRog.de("RETIRO")).isEqualTo(esperado);
        assertThat(ConceptoRog.de("RECARGA"))
                .isEqualTo(dsl.fetchOne("SELECT fn_uif_concepto('RECARGA')").get(0, String.class));
        assertThat(ConceptoRog.de("TRANSFERENCIA_P2P"))
                .isEqualTo(dsl.fetchOne("SELECT fn_uif_concepto('TRANSFERENCIA_P2P')")
                        .get(0, String.class));
    }

    @Test
    @DisplayName("rechaza por R-UIF-03")
    void rechazaRUIF03() {
        // Un ROG individual NO lleva ventana: marcarlo como acumulado sin ella lo
        // rechaza la base.
        var salida = transaccion.execute(t -> rogCU.registrar(retiro("500.00", BigDecimal.ONE, "USD"), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE id = ? AND es_acumulada = false AND ventana_desde IS NULL
                        """,
                        salida.registros().get(0).registroId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-UIF-04")
    void rechazaRUIF04() {
        // Sin cotizacion no se convierte, y sin conversion el umbral en dolares no se
        // puede medir.
        assertThatThrownBy(() -> transaccion.execute(t -> rogCU.registrar(retiro("500.00", null, "BOB"), ctx)))
                .hasMessageContaining("tipo de cambio");
    }

    @Test
    @DisplayName("rechaza por R-UIF-05")
    void rechazaRUIF05() {
        var salida = transaccion.execute(t -> rogCU.registrar(retiro("500.00", BigDecimal.ONE, "USD"), ctx));

        // Cada registro con su periodo: el envio mensual se arma por ahi.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE id = ? AND periodo_remision = to_char(fecha_operacion, 'YYYY-MM')
                        """,
                        salida.registros().get(0).registroId()))
                .isEqualTo(1);
    }
}
