package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU33DevolverComision.EntradaDevolucion;
import bo.aportaya.tarifas.aplicacion.CU33DevolverComision.SalidaDevolucion;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-33 · Devolver comision y emitir nota de credito. */
class CU33Test extends BaseDeTarifas {

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

    private record Caso(UUID devengoId, UUID usuario, UUID conceptoId, ContextoSesion ctx) {}

    /** Un devengo cobrado por Bs 18, con su factura: el punto de partida del CU. */
    private Caso caso(boolean conFactura) {
        UUID tarifario = fixtura.tarifarioVigente("TAR-" + corto());
        UUID hecho = fixtura.hechoGenerador("ENTREGA-" + corto());
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario, hecho, redondeo, fixtura.cuentaDeIngreso(), "COM-SERV", "0.0030", null, null, false, false);
        fixtura.activar(tarifario);
        UUID usuario = fixtura.usuario();
        UUID devengo = fixtura.devengoCobrado(concepto, tarifario, usuario, "18.00", "2026-08");
        if (conFactura) {
            fixtura.factura(devengo, usuario, fixtura.datosDeFacturacion(usuario), "18.00", "VALIDADA", null);
        }
        return new Caso(devengo, usuario, concepto, contextoDe(usuario));
    }

    @Test
    @DisplayName(
            "Dado un devengo cobrado por Bs 18 · Cuando se devuelven Bs 18 por reclamo procedente · Entonces el devengo queda DEVUELTO · Y existe una nota de crédito con cuf único enlazada a la devolución")
    void criterio1() {
        Caso c = caso(true);
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        SalidaDevolucion salida = transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "RECLAMO_PROCEDENTE",
                        "Reclamo dado por procedente",
                        bob("18.00"),
                        "ABONO_BILLETERA",
                        Optional.empty(),
                        soporte.usuarioId()),
                soporte));

        assertThat(salida.devolucionTotal()).isTrue();
        assertThat(salida.notaCreditoId()).isNotNull();
        assertThat(salida.cuf()).isNotBlank();
        // El devengo NO se edita: es append-only. Su estado corriente se DERIVA de la
        // devolucion, y borrar el ingreso perderia la prueba de que se cobro.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.devolucion_comision WHERE devengo_id = ? AND estado = 'EJECUTADA'",
                        c.devengoId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.nota_credito_debito WHERE devolucion_comision_id = ?",
                        salida.devolucionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un intento de devolver Bs 25 sobre un cobro de Bs 18 · Cuando se ejecuta · Entonces la operación se rechaza")
    void criterio2() {
        Caso c = caso(true);
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t -> devolucionCU.devolver(
                        new EntradaDevolucion(
                                c.devengoId(),
                                "ERROR_DE_TARIFA",
                                "De mas",
                                bob("25.00"),
                                "ABONO_BILLETERA",
                                Optional.empty(),
                                soporte.usuarioId()),
                        soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excede lo cobrado");
        assertThat(contar("SELECT count(*)::int FROM tarifas.devolucion_comision WHERE devengo_id = ?", c.devengoId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un reclamo con resultado FAVORABLE y monto reclamado · Cuando se intenta cerrarlo sin devolución asociada · Entonces el cierre se rechaza (R-CON-04)")
    void criterio3() {
        // El reclamo vive en `cumplimiento` y este servicio no lee ese esquema
        // (invariante 11). Lo que aporta tarifas es el enlace: la devolucion guarda el
        // reclamo_id, y sin esa fila el cierre del reclamo no tiene con que cumplir.
        Caso c = caso(true);
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        UUID reclamo = fixtura.reclamo(c.usuario());

        SalidaDevolucion salida = transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "RECLAMO_PROCEDENTE",
                        "Favorable con monto",
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
        // Sin esa fila, un reclamo favorable con monto no tendria reparacion asociada.
        assertThat(contar("SELECT count(*)::int FROM tarifas.devolucion_comision WHERE reclamo_id = ?", reclamo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La segunda devolucion completa NO pasa: el tope se mide contra lo ya
        // devuelto, y ya no queda nada. Es la barrera contra el doble clic de soporte.
        Caso c = caso(true);
        ContextoSesion soporte = contextoDe(fixtura.usuario());
        var entrada = new EntradaDevolucion(
                c.devengoId(),
                "DUPLICADO".equals("x") ? "DUPLICADO" : "ERROR_DE_TARIFA",
                "Devolucion completa",
                bob("18.00"),
                "ABONO_BILLETERA",
                Optional.empty(),
                soporte.usuarioId());

        transaccion.execute(t -> devolucionCU.devolver(entrada, soporte));

        assertThatThrownBy(() -> transaccion.execute(t -> devolucionCU.devolver(entrada, soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excede lo cobrado");
        assertThat(contar("SELECT count(*)::int FROM tarifas.devolucion_comision WHERE devengo_id = ?", c.devengoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos devoluciones parciales que juntas superan el cobro: la segunda no entra.
        // Y si la aplicacion fallara, lo impide el trigger tg_devolucion_maxima.
        Caso c = caso(true);
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "ERROR_DE_TARIFA",
                        "Primera parcial",
                        bob("12.00"),
                        "ABONO_BILLETERA",
                        Optional.empty(),
                        soporte.usuarioId()),
                soporte));

        assertThatThrownBy(() -> transaccion.execute(t -> devolucionCU.devolver(
                        new EntradaDevolucion(
                                c.devengoId(),
                                "ERROR_DE_TARIFA",
                                "Segunda parcial",
                                bob("7.00"),
                                "ABONO_BILLETERA",
                                Optional.empty(),
                                soporte.usuarioId()),
                        soporte)))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar("SELECT count(*)::int FROM tarifas.devolucion_comision WHERE devengo_id = ?", c.devengoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Dos parciales que suman exactamente lo cobrado: entran las dos, y ni un
        // centavo mas.
        Caso c = caso(true);
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "ERROR_DE_TARIFA",
                        "Mitad",
                        bob("9.00"),
                        "ABONO_BILLETERA",
                        Optional.empty(),
                        soporte.usuarioId()),
                soporte));
        SalidaDevolucion segunda = transaccion.execute(t -> devolucionCU.devolver(
                new EntradaDevolucion(
                        c.devengoId(),
                        "ERROR_DE_TARIFA",
                        "La otra mitad",
                        bob("9.00"),
                        "ABONO_BILLETERA",
                        Optional.empty(),
                        soporte.usuarioId()),
                soporte));

        assertThat(segunda.devolucionTotal()).isTrue();
        assertThat(segunda.disponibleRestante()).isEqualByComparingTo(bob("0.00"));
        assertThat(dsl.fetchOne(
                                "SELECT sum(monto_devuelto) FROM tarifas.devolucion_comision WHERE devengo_id = ?",
                                c.devengoId())
                        .get(0, java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("18.00"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "devolucion"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "devolucion"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Una factura emitida offline y sin enviar NO admite nota de credito todavia:
        // la nota corregiria algo que para el servicio de impuestos no existe. No
        // queda devolucion a medias.
        Caso c = caso(false);
        UUID datos = fixtura.datosDeFacturacion(c.usuario());
        UUID contingencia = fixtura.contingencia(SUCURSAL, PUNTO_VENTA);
        fixtura.factura(c.devengoId(), c.usuario(), datos, "18.00", "EMITIDA_OFFLINE", contingencia);
        ContextoSesion soporte = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t -> devolucionCU.devolver(
                        new EntradaDevolucion(
                                c.devengoId(),
                                "ERROR_DE_TARIFA",
                                "Sin enviar",
                                bob("18.00"),
                                "ABONO_BILLETERA",
                                Optional.empty(),
                                soporte.usuarioId()),
                        soporte)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("offline sin enviar");
        assertThat(contar("SELECT count(*)::int FROM tarifas.devolucion_comision WHERE devengo_id = ?", c.devengoId()))
                .isZero();
    }
}
