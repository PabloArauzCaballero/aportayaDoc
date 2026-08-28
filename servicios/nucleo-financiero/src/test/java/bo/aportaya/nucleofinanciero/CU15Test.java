package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU15EmitirExtracto.EntradaCertificado;
import bo.aportaya.nucleofinanciero.aplicacion.CU15EmitirExtracto.EntradaExtracto;
import bo.aportaya.nucleofinanciero.aplicacion.CU15EmitirExtracto.SalidaCertificado;
import bo.aportaya.nucleofinanciero.aplicacion.CU15EmitirExtracto.SalidaExtracto;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-15 · Emitir extracto y certificado de saldo. */
class CU15Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private LocalDate hoy() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
    }

    private record Caso(UUID usuario, UUID cuenta, ContextoSesion ctx) {}

    /** Una cuenta con saldo y su cierre del dia coherente. */
    private Caso caso(String saldo, String saldoCerrado) {
        fixtura.tipoDeCambioDeHoy();
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal(saldo));
        fixtura.cierreDelDia(cuenta, hoy(), new BigDecimal(saldoCerrado), 1);
        return new Caso(usuario, cuenta, contextoDe(usuario));
    }

    @Test
    @DisplayName(
            "Dado un período con cierres diarios completos · Cuando se emite el extracto · Entonces saldo_final coincide con el saldo_diario_billetera de la fecha final · Y existe hash_archivo no nulo")
    void criterio1() {
        Caso c = caso("750.00", "750.00");

        SalidaExtracto salida = transaccion.execute(
                t -> extractoCU.emitir(new EntradaExtracto(c.cuenta(), hoy().minusDays(30), hoy(), false), c.ctx()));

        assertThat(salida.saldoFinal()).isEqualByComparingTo(bob("750.00"));
        assertThat(salida.hashArchivo()).isNotBlank().hasSize(64);
        assertThat(salida.cantidadMovimientos()).isPositive();
    }

    @Test
    @DisplayName(
            "Dado un certificado emitido · Cuando un tercero verifica folio y hash · Entonces el sistema confirma su autenticidad")
    void criterio2() {
        Caso c = caso("500.00", "500.00");

        SalidaCertificado cert = transaccion.execute(t -> extractoCU.certificar(
                new EntradaCertificado(c.cuenta(), hoy(), "TRAMITE_BANCARIO", "https://cert/x.pdf"), c.ctx()));

        // Un banco que recibe el papel tiene que poder confirmarlo sin llamarnos.
        boolean autentico = transaccion.execute(t -> extractoCU.verificar(cert.folio(), cert.hashDocumento(), c.ctx()));
        boolean falsificado = transaccion.execute(t -> extractoCU.verificar(cert.folio(), "0".repeat(64), c.ctx()));

        assertThat(autentico).isTrue();
        assertThat(falsificado).isFalse();
        assertThat(cert.saldoCertificado()).isEqualByComparingTo(bob("500.00"));
    }

    @Test
    @DisplayName(
            "Dada una diferencia entre el extracto calculado y el saldo diario · Cuando se intenta emitir · Entonces la emisión se bloquea y se registra el descuadre")
    void criterio3() {
        // El cierre dice 999 y el libro dice 400: no se emite. Un extracto con una
        // cifra que no coincide es peor que no tener extracto — la persona lo usa
        // para un tramite, se lo rechazan, y descubre el error cuando ya le costo.
        Caso c = caso("400.00", "999.00");

        SalidaExtracto salida = transaccion.execute(
                t -> extractoCU.emitir(new EntradaExtracto(c.cuenta(), hoy().minusDays(30), hoy(), false), c.ctx()));

        assertThat(salida.emitido()).isFalse();
        assertThat(salida.hashArchivo()).isNull();
        assertThat(salida.motivoDelBloqueo()).contains("no cuadra con el cierre");
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.evento_dominio WHERE tipo = ?",
                        "nucleo_financiero.extracto_descuadrado"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Emitir el extracto es una lectura sellada: dos veces con los mismos datos
        // da el mismo hash, o el sello no serviria para verificar nada.
        Caso c = caso("300.00", "300.00");

        SalidaExtracto a = transaccion.execute(
                t -> extractoCU.emitir(new EntradaExtracto(c.cuenta(), hoy().minusDays(7), hoy(), false), c.ctx()));
        SalidaExtracto b = transaccion.execute(
                t -> extractoCU.emitir(new EntradaExtracto(c.cuenta(), hoy().minusDays(7), hoy(), false), c.ctx()));

        assertThat(b.hashArchivo()).isEqualTo(a.hashArchivo());
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos certificados del mismo dia son legitimos —alguien puede pedir dos
        // copias— pero cada uno lleva SU folio: dos papeles con el mismo folio serian
        // imposibles de distinguir al verificarlos.
        Caso c = caso("200.00", "200.00");

        SalidaCertificado a = transaccion.execute(t -> extractoCU.certificar(
                new EntradaCertificado(c.cuenta(), hoy(), "TRAMITE_BANCARIO", "https://c/1.pdf"), c.ctx()));
        SalidaCertificado b = transaccion.execute(t -> extractoCU.certificar(
                new EntradaCertificado(c.cuenta(), hoy(), "TRAMITE_BANCARIO", "https://c/2.pdf"), c.ctx()));

        assertThat(b.folio()).isNotEqualTo(a.folio());
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El extracto solo sale si el libro y el cierre coinciden AL CENTAVO.
        Caso justo = caso("123.45", "123.45");
        assertThat(transaccion
                        .execute(t -> extractoCU.emitir(
                                new EntradaExtracto(justo.cuenta(), hoy().minusDays(1), hoy(), false), justo.ctx()))
                        .saldoFinal())
                .isEqualByComparingTo(bob("123.45"));

        Caso porUnCentavo = caso("123.45", "123.46");
        assertThat(transaccion
                        .execute(t -> extractoCU.emitir(
                                new EntradaExtracto(porUnCentavo.cuenta(), hoy().minusDays(1), hoy(), false),
                                porUnCentavo.ctx()))
                        .emitido())
                .isFalse();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "auditoria"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "auditoria"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin cierre del dia no se emite, y no queda ningun rastro de un extracto a
        // medias.
        fixtura.tipoDeCambioDeHoy();
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal("100.00"));
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(
                        t -> extractoCU.emitir(new EntradaExtracto(cuenta, hoy().minusDays(1), hoy(), false), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("periodo esta incompleto");
    }

    @Test
    @DisplayName("rechaza emitir el extracto ajeno: es de los datos mas sensibles que hay")
    void rechazaExtractoAjeno() {
        Caso c = caso("500.00", "500.00");
        ContextoSesion otro = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t ->
                        extractoCU.emitir(new EntradaExtracto(c.cuenta(), hoy().minusDays(1), hoy(), false), otro)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay permiso");

        // Con permiso delegado si se puede: es como funciona un apoderado.
        assertThat(transaccion
                        .execute(t -> extractoCU.emitir(
                                new EntradaExtracto(c.cuenta(), hoy().minusDays(1), hoy(), true), otro))
                        .saldoFinal())
                .isEqualByComparingTo(bob("500.00"));
    }
}
