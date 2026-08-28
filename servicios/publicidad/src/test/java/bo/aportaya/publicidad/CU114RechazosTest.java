package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.publicidad.aplicacion.CU113EntregarAnuncio.Entrada;
import bo.aportaya.publicidad.aplicacion.CU114LiquidarPublicidad;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-114 · Lo que la base y el caso de uso rechazan. */
class CU114RechazosTest extends EscenarioDeCampana {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private String mesVigente() {
        return OffsetDateTime.now().format(MES);
    }

    private UUID facturar() {
        campanaEnAire("500.00", "50.00", "40.00", "CPC");
        var impresion = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
        transaccion.execute(t -> entregaCU.registrarClic(impresion.impresionId(), operaciones));
        return transaccion
                .execute(t -> liquidacionCU.liquidar(
                        new CU114LiquidarPublicidad.Entrada(cuentaId, mesVigente(), null, null), operaciones))
                .facturaPublicidadId();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        UUID facturaId = facturar();

        // La factura del mes es append-only: ni su monto ni su estado se reescriben, y
        // no se borra. Una factura editable no prueba lo que se le cobro a nadie.
        assertThat(rechazaLaBase("UPDATE publicidad.factura_publicidad SET monto_total = 1 WHERE id = ?", facturaId))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("UPDATE publicidad.factura_publicidad SET estado = 'COBRADA' WHERE id = ?", facturaId))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM publicidad.factura_publicidad WHERE id = ?", facturaId))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-PUB-06")
    void rechazaRPUB06() {
        UUID facturaId = facturar();

        // Un periodo por cuenta: dos facturas del mismo mes le cobran dos veces lo
        // mismo al mismo anunciante.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.factura_publicidad
                            (cuenta_publicitaria_id, periodo, monto_total, moneda, estado, generada_en)
                        VALUES (?, ?, 40, 'BOB', 'GENERADA', now())
                        """,
                        cuentaId,
                        mesVigente()))
                .contains("uq_factura_publicidad_cuenta_periodo");

        // Y no se factura cero: no hay obligacion de emitir por un mes sin consumo, y
        // ck_factura_publicidad_monto tampoco lo admitiria.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.factura_publicidad
                            (cuenta_publicitaria_id, periodo, monto_total, moneda, estado, generada_en)
                        VALUES (?, '2019-01', 0, 'BOB', 'GENERADA', now())
                        """,
                        cuentaId))
                .contains("factura_publicidad");

        assertThat(contar("SELECT count(*)::int FROM publicidad.factura_publicidad WHERE id = ?", facturaId))
                .isEqualTo(1);
    }
}
