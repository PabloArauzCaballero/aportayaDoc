package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU33DevolverComision.EntradaDevolucion;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-33 · las pruebas de RECHAZO, una por restriccion citada. */
class CU33RechazosTest extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(UUID devengoId, UUID usuario, UUID facturaId, ContextoSesion ctx) {}

    private Caso caso() {
        UUID tarifario = fixtura.tarifarioVigente("TAR-" + corto());
        UUID hecho = fixtura.hechoGenerador("ENTREGA-" + corto());
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario, hecho, redondeo, fixtura.cuentaDeIngreso(), "COM-SERV", "0.0030", null, null, false, false);
        fixtura.activar(tarifario);
        UUID usuario = fixtura.usuario();
        UUID devengo = fixtura.devengoCobrado(concepto, tarifario, usuario, "18.00", "2026-08");
        UUID factura =
                fixtura.factura(devengo, usuario, fixtura.datosDeFacturacion(usuario), "18.00", "VALIDADA", null);
        return new Caso(devengo, usuario, factura, contextoDe(usuario));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El devengo no se edita para «dejarlo en cero»: la devolucion vive en su
        // propia fila. Borrar el ingreso perderia la prueba de que se cobro y se
        // devolvio, que es justo lo que hay que poder mostrar.
        Caso c = caso();
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "ERROR_DE_TARIFA",
                        "Devuelto todo",
                        bob("18.00"),
                        "ABONO_BILLETERA",
                        Optional.empty(),
                        soporte.usuarioId()),
                soporte));

        assertThat(rechazaLaBase("UPDATE tarifas.devengo_comision SET estado = 'DEVUELTO' WHERE id = '%s'"
                        .formatted(c.devengoId())))
                .contains("R-AUD-01");
        // Y el ingreso sigue registrado, con su devolucion al lado.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.devengo_comision WHERE id = ? AND monto_total = 18.00",
                        c.devengoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-06")
    void rechazaRAUD06() {
        // Un asiento confirmado solo se corrige por reversa. La devolucion pide el
        // asiento de reversa por evento; no reescribe el original.
        Caso c = caso();
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        var salida = transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "ERROR_DE_TARIFA",
                        "Reversa",
                        bob("18.00"),
                        "ABONO_BILLETERA",
                        Optional.empty(),
                        soporte.usuarioId()),
                soporte));

        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "tarifas.comision_devuelta",
                        salida.devolucionId()))
                .isEqualTo(1);
        // Y la fila de la devolucion tampoco se reescribe para cambiar el importe.
        assertThat(rechazaLaBase("UPDATE tarifas.devolucion_comision SET monto_devuelto = 25.00 WHERE id = '%s'"
                        .formatted(salida.devolucionId())))
                .contains("R-TAR-11");
    }

    @Test
    @DisplayName("rechaza por R-CON-04")
    void rechazaRCON04() {
        // Un reclamo favorable con monto EXIGE devolucion asociada. Lo que aporta este
        // servicio es el enlace: sin la fila con reclamo_id, el cierre del reclamo no
        // tiene con que cumplir.
        Caso c = caso();
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        UUID reclamo = fixtura.reclamo(c.usuario());

        assertThat(contar("SELECT count(*)::int FROM tarifas.devolucion_comision WHERE reclamo_id = ?", reclamo))
                .isZero();

        var salida = transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "RECLAMO_PROCEDENTE",
                        "Procedente",
                        bob("18.00"),
                        "ABONO_BILLETERA",
                        Optional.of(reclamo),
                        soporte.usuarioId()),
                soporte));

        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.devolucion_comision WHERE id = ? AND reclamo_id = ?",
                        salida.devolucionId(),
                        reclamo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Quien autoriza no ejecuta: la devolucion guarda quien la autorizo, y ese
        // dato es obligatorio. Sin nombre no hay a quien preguntarle por que.
        Caso c = caso();
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        UUID supervisor = fixtura.usuario();

        var salida = transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "ERROR_DE_TARIFA",
                        "Autorizada por supervisor",
                        bob("18.00"),
                        "ABONO_BILLETERA",
                        Optional.empty(),
                        supervisor),
                soporte));

        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.devolucion_comision WHERE id = ? AND autorizada_por = ?",
                        salida.devolucionId(),
                        supervisor))
                .isEqualTo(1);
        assertThat(rechazaLaBase("UPDATE tarifas.devolucion_comision SET autorizada_por = NULL WHERE id = '%s'"
                        .formatted(salida.devolucionId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-TAR-10")
    void rechazaRTAR10() {
        // La factura no se edita ni para reflejar la devolucion: se emite una nota de
        // credito con su propio CUF.
        Caso c = caso();
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        var salida = transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "ERROR_DE_TARIFA",
                        "Con nota",
                        bob("18.00"),
                        "ABONO_BILLETERA",
                        Optional.empty(),
                        soporte.usuarioId()),
                soporte));

        assertThat(salida.notaCreditoId()).isNotNull();
        assertThat(rechazaLaBase("UPDATE tarifas.factura_electronica SET monto_total = 0 WHERE id = '%s'"
                        .formatted(c.facturaId())))
                .contains("R-TAR-10");
    }

    @Test
    @DisplayName("rechaza por R-TAR-11")
    void rechazaRTAR11() {
        // No se devuelve mas de lo cobrado, y lo impide la BASE aunque la aplicacion
        // se equivoque.
        Caso c = caso();
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t -> devolucionCU.devolver(
                        new EntradaDevolucion(
                                c.devengoId(),
                                "ERROR_DE_TARIFA",
                                "De mas",
                                bob("18.01"),
                                "ABONO_BILLETERA",
                                Optional.empty(),
                                soporte.usuarioId()),
                        soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excede lo cobrado");
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.devolucion_comision
                            (id, devengo_id, autorizada_por, motivo, detalle, monto_devuelto, forma,
                             estado, solicitada_en, ejecutada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', 'ERROR_DE_TARIFA', 'saltando la app',
                                18.01, 'ABONO_BILLETERA', 'EJECUTADA', now(), now())
                        """
                                .formatted(c.devengoId(), c.usuario())))
                .contains("R-TAR-11");
    }
}
