package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision.EntradaCotizacion;
import bo.aportaya.tarifas.aplicacion.CU36ResolverPrecio.EntradaSegmento;
import bo.aportaya.tarifas.dominio.MetodoDeCalculo;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-36 · las pruebas de RECHAZO, una por restriccion citada. */
class CU36RechazosTest extends BaseDeTarifas {

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

    @Test
    @DisplayName("rechaza por R-CON-07")
    void rechazaRCON07() {
        // Sin tarifario vigente no hay a que colgar la regla del segmento: no se
        // resuelve un precio contra nada.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> precioCU.crear(
                new EntradaSegmento("SEG-" + corto(), "Sin tarifario", Map.of("gruposCompletados", 1), 1), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> cotizacionCU.cotizar(
                        new EntradaCotizacion(
                                "cot-sin-tar",
                                "NO-EXISTE-" + corto(),
                                "CUALQUIERA",
                                "ENTREGA_FONDO",
                                UUID.randomUUID(),
                                bob("100.00"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay tarifario vigente");
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // Un segmento es politica de precios: su criterio tiene que estar escrito y ser
        // evaluable. Uno vacio califica a todo el mundo, que es lo mismo que no tener
        // segmento y ademas parece un beneficio deliberado.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        String codigo = "VACIO-" + corto();

        assertThatThrownBy(() -> transaccion.execute(
                        t -> precioCU.crear(new EntradaSegmento(codigo, "Sin criterio", Map.of(), 1), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin criterio");
        assertThat(contar("SELECT count(*)::int FROM tarifas.segmento_comercial WHERE codigo = ?", codigo))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-TAR-01")
    void rechazaRTAR01() {
        // El precio del segmento se resuelve contra el UNICO tarifario vigente. Con dos
        // vigentes no habria forma de saber cual precio diferenciado aplica.
        Base b = base();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO catalogo.tarifario (id, codigo, version, nombre, estado, moneda_base,
                                                        vigente_desde, dias_preaviso, publicado_en,
                                                        url_publicacion, hash_documento)
                        VALUES (gen_random_uuid(), '%s', 5, 'Otro vigente', 'VIGENTE', 'BOB', now(), 0,
                                now(), 'https://x.test', repeat('j', 64))
                        """
                                .formatted(b.codigo())))
                .contains("ex_tarifario_vigente");
    }

    @Test
    @DisplayName("rechaza por R-TAR-02")
    void rechazaRTAR02() {
        // La regla del segmento no se cuela editando un tarifario ya vigente: se
        // publica la version siguiente. Editarlo cambiaria lo que ya se cobro.
        Base b = base();

        assertThat(rechazaLaBase("UPDATE tarifas.concepto_tarifa SET valor_porcentual = 0.0001 WHERE id = '%s'"
                        .formatted(b.conceptoId())))
                .contains("R-TAR-02");
    }

    @Test
    @DisplayName("rechaza por R-TAR-03")
    void rechazaRTAR03() {
        // El precio diferenciado sigue siendo un metodo de calculo con sus valores: un
        // segmento no habilita un concepto incoherente.
        assertThatThrownBy(() -> MetodoDeCalculo.exigirCoherencia("MIXTO", null, new BigDecimal("0.01")))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("le faltan los valores");
        assertThatThrownBy(() -> MetodoDeCalculo.exigirCoherencia("INVENTADO", null, null))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no admitido");
    }

    @Test
    @DisplayName("rechaza por R-TAR-07")
    void rechazaRTAR07() {
        // El grupo conserva su tarifa congelada aunque el usuario pierda el beneficio:
        // perder el precio pactado a mitad del pasanaku es cambiarle las reglas a
        // alguien que ya no se puede ir sin perder lo que puso.
        Base b = base();
        UUID grupo = fixtura.grupo();
        fixtura.congelarTarifa(grupo, b.tarifarioId());

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.tarifa_congelada_grupo
                            (id, grupo_id, tarifario_id, snapshot_conceptos, hash_snapshot, congelada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', '[]'::jsonb, repeat('k', 64), now())
                        """
                                .formatted(grupo, b.tarifarioId())))
                .contains("uq_tarifa_congelada_grupo");
    }
}
