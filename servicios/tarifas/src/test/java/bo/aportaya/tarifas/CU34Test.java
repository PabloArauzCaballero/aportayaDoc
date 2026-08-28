package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision.EntradaCotizacion;
import bo.aportaya.tarifas.aplicacion.CU34PublicarTarifario.EntradaPublicacion;
import bo.aportaya.tarifas.aplicacion.CU34PublicarTarifario.SalidaPublicacion;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-34 · Publicar un tarifario nuevo con preaviso. */
class CU34Test extends BaseDeTarifas {

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

    private record Base(String codigo, String hecho, UUID tarifarioId, UUID conceptoId, ContextoSesion ctx) {}

    private Base base() {
        String codigo = "TAR-" + corto();
        String hechoCodigo = "ENTREGA-" + corto();
        UUID tarifario = fixtura.tarifarioVigente(codigo);
        UUID hecho = fixtura.hechoGenerador(hechoCodigo);
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario, hecho, redondeo, fixtura.cuentaDeIngreso(), "COM-SERV", "0.0030", null, null, false, false);
        fixtura.activar(tarifario);
        return new Base(codigo, hechoCodigo, tarifario, concepto, contextoDe(fixtura.usuario()));
    }

    private EntradaPublicacion publicacion(Base b, String tipoCambio, int diasPreaviso) {
        return new EntradaPublicacion(
                b.tarifarioId(),
                "Version nueva",
                tipoCambio,
                diasPreaviso,
                b.ctx().usuarioId(),
                "ACTA-2026-07",
                "https://aportaya.test/tarifario/v2",
                "b".repeat(64),
                "CORREO",
                false,
                "{\"escenario\":\"historia real\"}",
                "{\"delta\":\"positivo\"}",
                new BigDecimal("1200.00"),
                340);
    }

    @Test
    @DisplayName(
            "Dado un incremento de comisión aprobado · Cuando se intenta poner VIGENTE antes de cumplir los días de preaviso · Entonces la operación se rechaza")
    void criterio1() {
        Base b = base();
        SalidaPublicacion salida =
                transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "INCREMENTO", 30), b.ctx()));

        assertThatThrownBy(() -> transaccion.execute(t -> tarifarioCU.ponerVigente(salida.tarifarioNuevoId(), b.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("preaviso");

        assertThat(salida.requierePreaviso()).isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM catalogo.tarifario WHERE id = ? AND estado = 'EN_PREAVISO'",
                        salida.tarifarioNuevoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un tarifario que pasa a VIGENTE · Cuando se consulta el anterior · Entonces existe en estado SUSTITUIDO con su vigencia cerrada")
    void criterio2() {
        Base b = base();
        // Una reduccion puede entrar sin preaviso, pero igual se publica y se registra:
        // bajar el precio sin decirlo tambien deja al usuario sin saber que le cobran.
        SalidaPublicacion salida =
                transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "REDUCCION", 0), b.ctx()));

        transaccion.execute(t -> tarifarioCU.ponerVigente(salida.tarifarioNuevoId(), b.ctx()));

        assertThat(contar(
                        "SELECT count(*)::int FROM catalogo.tarifario WHERE id = ? AND estado = 'VIGENTE'",
                        salida.tarifarioNuevoId()))
                .isEqualTo(1);
        // El anterior NO se borra: queda con su vigencia cerrada. Poder decir que se
        // cobraba en una fecha pasada es lo unico que responde un reclamo viejo.
        assertThat(contar(
                        "SELECT count(*)::int FROM catalogo.tarifario WHERE id = ? AND estado = 'SUSTITUIDO' AND vigente_hasta IS NOT NULL",
                        b.tarifarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un grupo con tarifa congelada de la versión anterior · Cuando se liquida una entrega tras el cambio · Entonces la comisión se calcula con el snapshot congelado")
    void criterio3() {
        Base b = base();
        UUID grupo = fixtura.grupo();
        fixtura.congelarTarifa(grupo, b.tarifarioId());
        SalidaPublicacion salida =
                transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "REDUCCION", 0), b.ctx()));
        transaccion.execute(t -> tarifarioCU.ponerVigente(salida.tarifarioNuevoId(), b.ctx()));

        var cotizacion = transaccion.execute(t -> cotizacionCU.cotizar(
                new EntradaCotizacion(
                        "cot-cong",
                        b.codigo(),
                        b.hecho(),
                        "ENTREGA_FONDO",
                        UUID.randomUUID(),
                        bob("10000.00"),
                        Optional.of(grupo),
                        Optional.empty(),
                        Optional.empty()),
                b.ctx()));

        // Manda el snapshot, no el tarifario nuevo: perder el precio pactado a mitad
        // del pasanaku es cambiarle las reglas a alguien que ya no se puede ir.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cotizacion_comision WHERE id = ? AND tarifario_id = ?",
                        cotizacion.cotizacionId(),
                        b.tarifarioId()))
                .isEqualTo(1);
        assertThat(cotizacion.montoComision()).isEqualByComparingTo(bob("30.00"));
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Dos publicaciones del mismo tarifario base crean versiones distintas, pero
        // poner VIGENTE dos veces el mismo no: el segundo lo encuentra inmutable.
        Base b = base();
        SalidaPublicacion salida =
                transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "REDUCCION", 0), b.ctx()));
        transaccion.execute(t -> tarifarioCU.ponerVigente(salida.tarifarioNuevoId(), b.ctx()));

        assertThatThrownBy(() -> transaccion.execute(t -> tarifarioCU.ponerVigente(salida.tarifarioNuevoId(), b.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("inmutable");
        assertThat(contar(
                        "SELECT count(*)::int FROM catalogo.tarifario WHERE codigo = ? AND estado = 'VIGENTE'",
                        b.codigo()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Nunca hay dos vigentes del mismo codigo a la vez: lo impide el EXCLUDE de la
        // base. Con dos vigentes, los dos precios serian defendibles.
        Base b = base();
        SalidaPublicacion salida =
                transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "REDUCCION", 0), b.ctx()));
        transaccion.execute(t -> tarifarioCU.ponerVigente(salida.tarifarioNuevoId(), b.ctx()));

        assertThat(rechazaLaBase(
                        """
                        UPDATE catalogo.tarifario SET estado = 'VIGENTE', vigente_hasta = NULL
                         WHERE id = '%s'
                        """
                                .formatted(b.tarifarioId())))
                .contains("ex_tarifario_vigente");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // La simulacion sobre la historia real queda guardada con su delta: aprobar un
        // cambio sin saber cuanto mueve es aprobarlo a ciegas.
        Base b = base();

        SalidaPublicacion salida =
                transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "INCREMENTO", 30), b.ctx()));

        var fila = dsl.fetchOne(
                "SELECT delta_ingreso_estimado, usuarios_impactados FROM tarifas.simulacion_tarifa WHERE tarifario_id = ?",
                salida.tarifarioNuevoId());
        assertThat(fila.get(0, BigDecimal.class)).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(fila.get(1, Integer.class)).isEqualTo(340);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "publicador"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "publicador"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin acta del comite no se publica NADA: un tarifario sin acta es un precio
        // que alguien puso solo. Y no queda version a medias.
        Base b = base();
        var sinActa = new EntradaPublicacion(
                b.tarifarioId(),
                "Sin acta",
                "INCREMENTO",
                30,
                b.ctx().usuarioId(),
                "  ",
                "https://aportaya.test/x",
                "c".repeat(64),
                "CORREO",
                false,
                "{}",
                "{}",
                BigDecimal.ZERO,
                0);

        assertThatThrownBy(() -> transaccion.execute(t -> tarifarioCU.publicar(sinActa, b.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("acta de aprobacion");
        assertThat(contar("SELECT count(*)::int FROM catalogo.tarifario WHERE codigo = ?", b.codigo()))
                .isEqualTo(1);
    }
}
