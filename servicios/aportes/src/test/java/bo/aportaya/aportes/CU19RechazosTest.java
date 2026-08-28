package bo.aportaya.aportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.aportes.aplicacion.CU19ReembolsarPago.EntradaDisputa;
import bo.aportaya.aportes.aplicacion.CU19ReembolsarPago.EntradaReembolso;
import bo.aportaya.aportes.aplicacion.CU19ReembolsarPago.SalidaDisputa;
import bo.aportaya.aportes.aplicacion.CU19ReembolsarPago.SalidaSolicitud;
import bo.aportaya.aportes.aplicacion.CU21CobrarAporte.EntradaCobro;
import bo.aportaya.aportes.aplicacion.CU21CobrarAporte.SalidaCobro;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-19 · las pruebas de RECHAZO, una por restriccion citada.
 *
 * <p>Otra pregunta que las de {@link CU19Test}: aquellas verifican que el caso de uso
 * hace lo que promete; estas, que lo que no debe pasar no pasa — y dicen quien lo
 * impide, la base o la aplicacion, porque no es lo mismo.
 */
class CU19RechazosTest extends BaseDeAportes {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Caso(UUID usuario, UUID obligacion, UUID pago, ContextoSesion ctx) {}

    private Caso caso(String monto) {
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, monto, 10);
        ContextoSesion ctx = contextoDe(usuario);
        SalidaCobro cobro = transaccion.execute(t -> cobroCU.acreditar(
                new EntradaCobro(
                        "cob-" + UUID.randomUUID(),
                        obligacion.id(),
                        bob(monto),
                        bob("0.00"),
                        "BILLETERA_MOVIL",
                        "ref-" + UUID.randomUUID(),
                        Optional.empty(),
                        false,
                        true),
                ctx));
        return new Caso(usuario, obligacion.id(), cobro.pagoId(), ctx);
    }

    private SalidaSolicitud solicitud(Caso c, String monto, ContextoSesion soporte) {
        return transaccion.execute(
                t -> reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob(monto), "DUPLICADO"), soporte));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Devolver plata NO borra ni edita el pago original: la correccion es una fila
        // nueva. Un pago que desaparece de la tabla es un pago que el participante no
        // puede probar que hizo.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        ContextoSesion aprobador = contextoDe(fixtura.usuario());
        var solicitud = solicitud(c, "500.00", soporte);

        transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), aprobador));

        assertThat(contar("SELECT count(*)::int FROM aportes.pago WHERE id = ? AND monto = 500.00", c.pago()))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM aportes.reembolso WHERE pago_id = ?", c.pago()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // El asiento lo escribe nucleo-financiero (invariante 12), pero solo puede
        // cuadrarlo si aportes le manda el importe. Un evento de reembolso sin monto
        // deja un asiento a medias que nadie puede cerrar.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        ContextoSesion aprobador = contextoDe(fixtura.usuario());
        var solicitud = solicitud(c, "300.00", soporte);

        transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), aprobador));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM aportes.evento_dominio
                         WHERE tipo = ? AND payload->>'monto' = ?
                        """,
                        "aportes.reembolso_ejecutado",
                        "300.00"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // Lo que se devuelve iguala lo que se quita de la obligacion, al centavo. Si
        // se devolvieran 300 y se descontaran 299,99, el grupo pierde un centavo por
        // reembolso y nadie lo ve hasta el cierre del ejercicio.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        ContextoSesion aprobador = contextoDe(fixtura.usuario());
        var solicitud = solicitud(c, "333.33", soporte);

        transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), aprobador));

        assertThat(dsl.fetchOne("SELECT monto_pagado FROM aportes.obligacion_aporte WHERE id = ?", c.obligacion())
                        .get(0, java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("166.67"));
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        // No se puede devolver hasta dejar la obligacion en negativo. El limite se
        // mide contra lo ya devuelto, no contra el pago original.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        ContextoSesion aprobador = contextoDe(fixtura.usuario());
        var primera = solicitud(c, "500.00", soporte);
        transaccion.execute(t -> reembolsoCU.aprobar(primera.reembolsoId(), aprobador));

        assertThatThrownBy(() -> solicitud(c, "0.01", soporte))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excede el pago");
        assertThat(contar("SELECT monto_pagado::int FROM aportes.obligacion_aporte WHERE id = ?", c.obligacion()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // La base rechaza que el mismo consumidor procese el mismo mensaje del
        // proveedor dos veces: es lo que sostiene que la disputa no se abra dos veces.
        UUID idEvento = UUID.randomUUID();
        transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "proveedor-pago"));

        assertThat(rechazaLaBase(
                        "INSERT INTO aportes.evento_consumido (id_evento, consumidor, consumido_en) VALUES ('%s', 'proveedor-pago', now())"
                                .formatted(idEvento)))
                .contains("pk_aportes_evtcons");
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // Un reembolso ejecutado sin evento en la misma transaccion es un descuadre
        // garantizado al cierre: la plata salio y el cierre no se entera.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        ContextoSesion aprobador = contextoDe(fixtura.usuario());
        var solicitud = solicitud(c, "120.00", soporte);

        transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), aprobador));

        assertThat(contar(
                        "SELECT count(*)::int FROM aportes.evento_dominio WHERE agregado_id = ? AND tipo = ?",
                        solicitud.reembolsoId(),
                        "aportes.reembolso_ejecutado"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CON-01")
    void rechazaRCON01() {
        // El plazo se calcula al ingresar y queda GUARDADO. La base no admite una
        // disputa sin fecha limite: recalcularla despues es mover la vara.
        Caso c = caso("500.00");
        SalidaDisputa disputa = transaccion.execute(t -> reembolsoCU.registrarDisputa(
                new EntradaDisputa(c.pago(), "CONTRACARGO", "desconoce", bob("500.00"), "prov-" + UUID.randomUUID()),
                c.ctx()));

        assertThat(rechazaLaBase("UPDATE aportes.disputa_pago SET fecha_limite_respuesta = NULL WHERE id = '%s'"
                        .formatted(disputa.disputaId())))
                .contains("fecha_limite_respuesta");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Quien autoriza no ejecuta. Un reembolso es la via mas corta para sacar
        // dinero con una excusa verosimil, y sin cuatro ojos alcanza una sola persona.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        var solicitud = solicitud(c, "500.00", soporte);

        assertThatThrownBy(() -> transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser quien lo solicito");
        assertThat(contar(
                        "SELECT count(*)::int FROM aportes.reembolso WHERE id = ? AND estado = 'SOLICITADO'",
                        solicitud.reembolsoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-TAR-11")
    void rechazaRTAR11() {
        // No se devuelve mas de lo cobrado, ni de una ni sumando parciales.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> solicitud(c, "500.01", soporte))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excede el pago");
        assertThat(contar("SELECT count(*)::int FROM aportes.reembolso")).isZero();
    }

    @Test
    @DisplayName("rechaza un motivo que la base no admite, antes de intentar escribirlo")
    void rechazaMotivoNoAdmitido() {
        // El CU-19 nombra MONTO_ERRONEO; ck_reembolso_motivo no lo admite. Manda la
        // DDL, y el desvio esta declarado en el informe del carril: enterarse en el
        // INSERT convierte una regla de negocio en un error 500.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t ->
                        reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob("100.00"), "MONTO_ERRONEO"), soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no admitido");
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO aportes.reembolso (id, pago_id, monto, motivo, estado, solicitado_por, fecha_solicitud)
                        VALUES (gen_random_uuid(), '%s', 1.00, 'MONTO_ERRONEO', 'SOLICITADO', '%s', now())
                        """
                                .formatted(c.pago(), c.usuario())))
                .contains("ck_reembolso_motivo");
    }
}
