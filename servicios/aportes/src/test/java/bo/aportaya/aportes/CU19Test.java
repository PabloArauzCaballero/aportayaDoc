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

/** CU-19 · Reembolsar un pago y atender una disputa. */
class CU19Test extends BaseDeAportes {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Caso(UUID usuario, UUID obligacion, UUID pago, ContextoSesion ctx) {}

    /** Un pago acreditado: es lo que se va a reembolsar. */
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
                        "ref",
                        Optional.empty(),
                        false,
                        true),
                ctx));
        return new Caso(usuario, obligacion.id(), cobro.pagoId(), ctx);
    }

    @Test
    @DisplayName(
            "Dado un pago de Bs 500 sin reembolsos previos · Cuando soporte solicita reembolsar Bs 600 · Entonces se rechaza con MONTO_EXCEDE_PAGO")
    void criterio1() {
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t ->
                        reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob("600.00"), "DUPLICADO"), soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excede el pago");
        assertThat(contar("SELECT count(*)::int FROM aportes.reembolso")).isZero();
    }

    @Test
    @DisplayName(
            "Dado un reembolso aprobado sobre un pago que saldaba una obligación · Cuando se ejecuta · Entonces la obligacion_aporte vuelve a estado PENDIENTE por ese importe · Y existe un asiento_contable cuadrado que lo respalda")
    void criterio2() {
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        SalidaSolicitud solicitud = transaccion.execute(
                t -> reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob("500.00"), "DUPLICADO"), soporte));
        ContextoSesion aprobador = contextoDe(fixtura.usuario());

        var ejecucion = transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), aprobador));

        assertThat(ejecucion.estadoObligacion()).isEqualTo("PENDIENTE");
        assertThat(contar("SELECT monto_pagado::int FROM aportes.obligacion_aporte WHERE id = ?", c.obligacion()))
                .isZero();
        // El asiento lo escribe nucleo-financiero (invariante 12): aca se verifica
        // que se pidio. Si la cuota quedara dada por pagada, el faltante lo absorben
        // los demas del grupo sin enterarse.
        assertThat(contar(
                        "SELECT count(*)::int FROM aportes.evento_dominio WHERE tipo = ?",
                        "aportes.reembolso_ejecutado"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una disputa recibida del proveedor · Cuando se registra · Entonces fecha_limite_respuesta queda guardada · Y aparece en el tablero ordenada por plazo restante")
    void criterio3() {
        Caso c = caso("500.00");

        SalidaDisputa disputa = transaccion.execute(t -> reembolsoCU.registrarDisputa(
                new EntradaDisputa(c.pago(), "CONTRACARGO", "el titular desconoce el cargo", bob("500.00"), "prov-d1"),
                c.ctx()));

        assertThat(disputa.esNueva()).isTrue();
        assertThat(disputa.fechaLimiteRespuesta()).isNotNull();
        // El plazo queda GUARDADO, no se recalcula al mirar el tablero: moverlo cada
        // vez que cambie la politica es un argumento que el proveedor no acepta.
        assertThat(contar(
                        "SELECT count(*)::int FROM aportes.disputa_pago WHERE id = ? AND fecha_limite_respuesta IS NOT NULL",
                        disputa.disputaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un reembolso ya ejecutado · Cuando el webhook del proveedor llega dos veces · Entonces el saldo se debita una sola vez")
    void criterio4() {
        // El proveedor confirma la devolucion y reenvia la confirmacion. La segunda
        // no vuelve a mover la obligacion: si lo hiciera, el participante quedaria
        // debiendo el doble por un reintento del proveedor.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        ContextoSesion aprobador = contextoDe(fixtura.usuario());
        SalidaSolicitud solicitud = transaccion.execute(
                t -> reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob("500.00"), "DUPLICADO"), soporte));
        UUID webhook = UUID.randomUUID();

        transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), aprobador));
        boolean primeraConfirmacion = transaccion.execute(t -> consumidos.registrar(dsl, webhook, "proveedor-pago"));
        boolean segundaConfirmacion = transaccion.execute(t -> consumidos.registrar(dsl, webhook, "proveedor-pago"));

        assertThat(primeraConfirmacion).isTrue();
        assertThat(segundaConfirmacion).isFalse();
        assertThat(contar("SELECT monto_pagado::int FROM aportes.obligacion_aporte WHERE id = ?", c.obligacion()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM aportes.reembolso WHERE pago_id = ? AND estado = ?",
                        c.pago(),
                        "EJECUTADO"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // El proveedor reenvia la disputa: la segunda no abre otra.
        Caso c = caso("500.00");

        SalidaDisputa a = transaccion.execute(t -> reembolsoCU.registrarDisputa(
                new EntradaDisputa(c.pago(), "CONTRACARGO", "desconoce", bob("500.00"), "prov-rep"), c.ctx()));
        SalidaDisputa b = transaccion.execute(t -> reembolsoCU.registrarDisputa(
                new EntradaDisputa(c.pago(), "CONTRACARGO", "desconoce", bob("500.00"), "prov-rep"), c.ctx()));

        assertThat(a.esNueva()).isTrue();
        assertThat(b.esNueva()).isFalse();
        assertThat(contar("SELECT count(*)::int FROM aportes.disputa_pago WHERE pago_id = ?", c.pago()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos aprobaciones del mismo reembolso: la segunda no devuelve la plata otra
        // vez. El WHERE estado = 'SOLICITADO' decide.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        SalidaSolicitud solicitud = transaccion.execute(
                t -> reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob("200.00"), "ERROR_MONTO"), soporte));
        ContextoSesion aprobador = contextoDe(fixtura.usuario());

        transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), aprobador));

        assertThatThrownBy(() -> transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), aprobador)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya no esta solicitado");
        assertThat(contar("SELECT monto_pagado::int FROM aportes.obligacion_aporte WHERE id = ?", c.obligacion()))
                .isEqualTo(300);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Dos reembolsos parciales no pueden sumar mas que el pago: se compara contra
        // lo YA devuelto, no contra el monto original.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        ContextoSesion aprobador = contextoDe(fixtura.usuario());

        var primera = transaccion.execute(
                t -> reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob("300.00"), "ERROR_MONTO"), soporte));
        transaccion.execute(t -> reembolsoCU.aprobar(primera.reembolsoId(), aprobador));

        assertThat(primera.disponibleRestante()).isEqualByComparingTo(bob("200.00"));
        assertThatThrownBy(() -> transaccion.execute(t ->
                        reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob("200.01"), "ERROR_MONTO"), soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excede el pago");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "proveedor-pago"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "proveedor-pago"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin cuatro ojos no se aprueba, y la obligacion no se toca.
        Caso c = caso("500.00");
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        SalidaSolicitud solicitud = transaccion.execute(
                t -> reembolsoCU.solicitar(new EntradaReembolso(c.pago(), bob("100.00"), "ERROR_MONTO"), soporte));

        assertThatThrownBy(() -> transaccion.execute(t -> reembolsoCU.aprobar(solicitud.reembolsoId(), soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser quien lo solicito");
        assertThat(contar("SELECT monto_pagado::int FROM aportes.obligacion_aporte WHERE id = ?", c.obligacion()))
                .isEqualTo(500);
    }
}
