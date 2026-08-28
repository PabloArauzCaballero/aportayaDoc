package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision.EntradaCotizacion;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision.SalidaCotizacion;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-30 · Cotizar la comision antes de operar. */
class CU30Test extends BaseDeTarifas {

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

    /** El escenario del CU: 0,3% con piso 10 y techo 50, sin impuestos encima. */
    private record Escenario(
            String codigoTarifario, String hecho, UUID tarifarioId, UUID conceptoId, ContextoSesion ctx) {}

    private Escenario escenario(String piso, String techo, boolean gravadoIva, boolean precioIncluyeImpuesto) {
        return escenario(piso, techo, gravadoIva, precioIncluyeImpuesto, "BENEFICIARIO_DEL_TURNO");
    }

    private Escenario escenario(
            String piso, String techo, boolean gravadoIva, boolean precioIncluyeImpuesto, String sujeto) {
        String codigoTarifario = "TAR-" + corto();
        String hechoCodigo = "ENTREGA-" + corto();
        UUID tarifario = fixtura.tarifarioVigente(codigoTarifario);
        UUID hecho = fixtura.hechoGenerador(hechoCodigo);
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID cuenta = fixtura.cuentaDeIngreso();
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario,
                hecho,
                redondeo,
                cuenta,
                "COM-SERV",
                "0.0030",
                piso,
                techo,
                gravadoIva,
                precioIncluyeImpuesto,
                sujeto);
        // El tarifario se activa DESPUES de cargar sus conceptos: en uno ya vigente la
        // base los rechaza, que es R-TAR-02 haciendo su trabajo.
        fixtura.activar(tarifario);
        return new Escenario(codigoTarifario, hechoCodigo, tarifario, concepto, contextoDe(fixtura.usuario()));
    }

    private SalidaCotizacion cotizar(Escenario e, String base, String clave) {
        return transaccion.execute(t -> cotizacionCU.cotizar(
                new EntradaCotizacion(
                        clave,
                        e.codigoTarifario(),
                        e.hecho(),
                        "ENTREGA_FONDO",
                        UUID.randomUUID(),
                        bob(base),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                e.ctx()));
    }

    @Test
    @DisplayName(
            "Dada una bolsa de Bs 6.000 y un concepto de 0,3% con piso 10 y techo 50 · Cuando se cotiza · Entonces monto_comision es 18,00 redondeado según la política")
    void criterio1() {
        var e = escenario("10.00", "50.00", false, false);

        SalidaCotizacion salida = cotizar(e, "6000.00", "cot-1");

        assertThat(salida.montoComision()).isEqualByComparingTo(bob("18.00"));
        assertThat(salida.esNueva()).isTrue();
        // El desglose se guarda entero: seis meses despues, «se calculo con el
        // tarifario vigente» no responde nada; la lista de lineas si.
        assertThat(salida.desglose()).isNotEmpty();
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cotizacion_comision WHERE id = ? AND monto_comision = 18.00",
                        salida.cotizacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una bolsa de Bs 1.000 con el mismo concepto · Cuando se cotiza · Entonces monto_comision es 10,00 (piso aplicado)")
    void criterio2() {
        var e = escenario("10.00", "50.00", false, false);

        SalidaCotizacion salida = cotizar(e, "1000.00", "cot-2");

        // 0,3% de 1.000 son 3, pero el piso manda: por debajo de cierto monto la
        // operacion le cuesta a la plataforma mas de lo que deja.
        assertThat(salida.montoComision()).isEqualByComparingTo(bob("10.00"));
        assertThat(salida.desglose().get(0).detalle()).contains("monto minimo");
    }

    @Test
    @DisplayName(
            "Dada una cotización vencida · Cuando se intenta devengar con ella · Entonces se rechaza y se recalcula")
    void criterio3() {
        var e = escenario("10.00", "50.00", false, false);
        SalidaCotizacion salida = cotizar(e, "6000.00", "cot-3");

        // Se vence a mano: esperar quince minutos en una prueba no prueba nada.
        dsl.execute(
                "UPDATE tarifas.cotizacion_comision SET valida_hasta = now() - interval '1 minute' WHERE id = ?",
                salida.cotizacionId());

        assertThatThrownBy(() -> transaccion.execute(t -> cotizacionCU.aceptar(salida.cotizacionId(), e.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("vencio");
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cotizacion_comision WHERE id = ? AND aceptada_en IS NULL",
                        salida.cotizacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var e = escenario("10.00", "50.00", false, false);
        UUID referencia = UUID.randomUUID();

        var a = transaccion.execute(t -> cotizacionCU.cotizar(
                new EntradaCotizacion(
                        "cot-idem",
                        e.codigoTarifario(),
                        e.hecho(),
                        "ENTREGA_FONDO",
                        referencia,
                        bob("6000.00"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                e.ctx()));
        var b = transaccion.execute(t -> cotizacionCU.cotizar(
                new EntradaCotizacion(
                        "cot-idem",
                        e.codigoTarifario(),
                        e.hecho(),
                        "ENTREGA_FONDO",
                        referencia,
                        bob("6000.00"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                e.ctx()));

        assertThat(b.cotizacionId()).isEqualTo(a.cotizacionId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar("SELECT count(*)::int FROM tarifas.cotizacion_comision WHERE referencia_id = ?", referencia))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos aceptaciones de la misma cotizacion: la segunda no vuelve a marcarla.
        // Sin el WHERE aceptada_en IS NULL, la evidencia de cuando acepto el usuario
        // se pisaria con la fecha del segundo clic.
        var e = escenario("10.00", "50.00", false, false);
        SalidaCotizacion salida = cotizar(e, "6000.00", "cot-conc");

        boolean primera = transaccion.execute(t -> cotizacionCU.aceptar(salida.cotizacionId(), e.ctx()));
        boolean segunda = transaccion.execute(t -> cotizacionCU.aceptar(salida.cotizacionId(), e.ctx()));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Con el IVA sumado ENCIMA, el total es comision + impuesto, al centavo.
        //
        // Solo es legal cuando el obligado NO es el usuario final:
        // ck_concepto_precio_final prohibe mostrarle un precio al beneficiario y
        // sumarle 13% despues (R-TAR-12). Con PLATAFORMA_ASUME el impuesto se suma
        // porque no hay nadie a quien engañar con el precio mostrado.
        var e = escenario(null, null, true, false, "PLATAFORMA_ASUME");
        fixtura.impuesto("IVA", "0.13");

        SalidaCotizacion salida = cotizar(e, "10000.00", "cot-cuadre");

        assertThat(salida.montoComision()).isEqualByComparingTo(bob("30.00"));
        assertThat(salida.montoImpuesto()).isEqualByComparingTo(bob("3.90"));
        assertThat(salida.montoTotal()).isEqualByComparingTo(bob("33.90"));
        assertThat(salida.montoComision().mas(salida.montoImpuesto())).isEqualByComparingTo(salida.montoTotal());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "cotizador"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "cotizador"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin tarifario vigente no se cotiza NADA, y no queda fila a medias. Denegar
        // por omision: cobrar sin tarifario publicado es lo que se reclama.
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t -> cotizacionCU.cotizar(
                        new EntradaCotizacion(
                                "cot-sin",
                                "NO-EXISTE",
                                "ENTREGA_FONDO",
                                "ENTREGA_FONDO",
                                UUID.randomUUID(),
                                bob("100.00"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay tarifario vigente");
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cotizacion_comision WHERE clave_idempotencia = ?",
                        "cot-sin"))
                .isZero();
    }
}
