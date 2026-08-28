package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.tarifas.aplicacion.CU34PublicarTarifario.EntradaPublicacion;
import bo.aportaya.tarifas.dominio.EntradaEnVigencia;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-34 · las pruebas de RECHAZO, una por restriccion citada. */
class CU34RechazosTest extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Base(String codigo, UUID tarifarioId, UUID conceptoId, ContextoSesion ctx) {}

    private Base base() {
        String codigo = "TAR-" + corto();
        UUID tarifario = fixtura.tarifarioVigente(codigo);
        UUID hecho = fixtura.hechoGenerador("ENTREGA-" + corto());
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario,
                hecho,
                redondeo,
                facturacion.cuentaDeIngreso(),
                "COM-SERV",
                "0.0030",
                null,
                null,
                false,
                false);
        fixtura.activar(tarifario);
        return new Base(codigo, tarifario, concepto, contextoDe(fixtura.usuario()));
    }

    private EntradaPublicacion publicacion(Base b, String tipoCambio, int dias, String acta) {
        return new EntradaPublicacion(
                b.tarifarioId(),
                "Version nueva",
                tipoCambio,
                dias,
                b.ctx().usuarioId(),
                acta,
                "https://aportaya.test/v2",
                "b".repeat(64),
                "CORREO",
                false,
                "{}",
                "{}",
                BigDecimal.ZERO,
                0);
    }

    @Test
    @DisplayName("rechaza por R-CON-07")
    void rechazaRCON07() {
        // Un tarifario VIGENTE sin publicar no existe: la base lo impide. Cobrar con
        // un tarifario que nadie publico es lo que la transparencia prohibe.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO catalogo.tarifario (id, codigo, version, nombre, estado, moneda_base,
                                                        vigente_desde, dias_preaviso)
                        VALUES (gen_random_uuid(), 'NO-PUB-%s', 1, 'Sin publicar', 'VIGENTE', 'BOB', now(), 0)
                        """
                                .formatted(corto())))
                .contains("ck_tarifario_publicado");
    }

    @Test
    @DisplayName("rechaza por R-LIC-01")
    void rechazaRLIC01() {
        // Publicar un tarifario es una politica interna vigente y toda politica vigente
        // tiene acta de aprobacion (R-LIC-03). Sin acta no se publica NADA: un precio
        // que alguien puso solo no es una politica, es una decision sin dueno.
        Base b = base();

        assertThatThrownBy(() ->
                        transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "INCREMENTO", 30, "  "), b.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("acta de aprobacion");
        assertThat(contar("SELECT count(*)::int FROM catalogo.tarifario WHERE codigo = ?", b.codigo()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // Igual que arriba, con el acta nula: no se acepta ni vacia ni ausente.
        Base b = base();

        assertThatThrownBy(() ->
                        transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "INCREMENTO", 30, null), b.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("acta de aprobacion");
        assertThat(contar("SELECT count(*)::int FROM tarifas.cambio_tarifario")).isZero();
    }

    @Test
    @DisplayName("rechaza por R-TAR-01")
    void rechazaRTAR01() {
        // Un solo vigente por codigo y rango. Con dos, los dos precios serian
        // defendibles y ninguno seria el correcto.
        Base b = base();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO catalogo.tarifario (id, codigo, version, nombre, estado, moneda_base,
                                                        vigente_desde, dias_preaviso, publicado_en,
                                                        url_publicacion, hash_documento)
                        VALUES (gen_random_uuid(), '%s', 9, 'El otro', 'VIGENTE', 'BOB', now(), 0, now(),
                                'https://x.test', repeat('d', 64))
                        """
                                .formatted(b.codigo())))
                .contains("ex_tarifario_vigente");
    }

    @Test
    @DisplayName("rechaza por R-TAR-02")
    void rechazaRTAR02() {
        // Un tarifario vigente es inmutable: sus conceptos no se editan ni se borran.
        // Se publica la version siguiente. Editarlo cambiaria retroactivamente lo que
        // ya se cobro con el, y una cotizacion vieja dejaria de poder explicarse.
        Base b = base();

        assertThat(rechazaLaBase("UPDATE tarifas.concepto_tarifa SET valor_porcentual = 0.9 WHERE id = '%s'"
                        .formatted(b.conceptoId())))
                .contains("R-TAR-02");
        assertThat(rechazaLaBase("DELETE FROM tarifas.concepto_tarifa WHERE id = '%s'".formatted(b.conceptoId())))
                .contains("R-TAR-02");

        // HUECO DECLARADO: `tg_concepto_tarifa_inmutable` es BEFORE DELETE OR UPDATE.
        // **No cubre INSERT**, asi que agregar un concepto NUEVO a un tarifario ya
        // vigente si entra. La prueba lo deja escrito en vez de afirmar lo contrario:
        // un concepto colado despues de publicar cobra algo que el tarifario publicado
        // no anunciaba. Esta en planes/informes/carril-2B.md como H-8.
        UUID otroHecho = fixtura.hechoGenerador("OTRO-" + corto());
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.concepto_tarifa
                            (id, tarifario_id, hecho_generador_id, codigo, nombre_comercial,
                             descripcion_usuario, metodo_calculo, base_calculo, valor_fijo,
                             sujeto_obligado, forma_cobro, momento_cobro, gravado_iva, gravado_it,
                             precio_incluye_impuesto, orden_aplicacion, activo)
                        VALUES (gen_random_uuid(), '%s', '%s', 'COLADO', 'Agregado despues',
                                'La DDL todavia lo admite', 'FIJO', 'SIN_BASE', 5.00,
                                'PLATAFORMA_ASUME', 'COMPENSACION', 'AL_DEVENGAR', false, false,
                                false, 9, true)
                        """
                                .formatted(b.tarifarioId(), otroHecho)))
                .as("la DDL todavia admite el INSERT: es el hueco H-8, no una regla que funcione")
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-TAR-07")
    void rechazaRTAR07() {
        // Un grupo, una tarifa congelada. Dos snapshots del mismo grupo harian que dos
        // liquidaciones del mismo pasanaku cobren distinto.
        Base b = base();
        UUID grupo = escenario.grupo();
        escenario.congelarTarifa(grupo, b.tarifarioId());

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.tarifa_congelada_grupo
                            (id, grupo_id, tarifario_id, snapshot_conceptos, hash_snapshot, congelada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', '[]'::jsonb, repeat('e', 64), now())
                        """
                                .formatted(grupo, b.tarifarioId())))
                .contains("uq_tarifa_congelada_grupo");
    }

    @Test
    @DisplayName("rechaza por R-TAR-08")
    void rechazaRTAR08() {
        // Un incremento no entra sin preaviso cumplido. Lo verifica la aplicacion con
        // su mensaje y la BASE con su trigger: las dos, porque la unica diferencia
        // entre subir un precio y cobrarle de sorpresa a alguien es el aviso.
        Base b = base();
        var salida =
                transaccion.execute(t -> tarifarioCU.publicar(publicacion(b, "INCREMENTO", 30, "ACTA-1"), b.ctx()));

        assertThatThrownBy(() -> transaccion.execute(t -> tarifarioCU.ponerVigente(salida.tarifarioNuevoId(), b.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("preaviso");
        assertThat(rechazaLaBase("UPDATE catalogo.tarifario SET estado = 'VIGENTE' WHERE id = '%s'"
                        .formatted(salida.tarifarioNuevoId())))
                .contains("R-TAR-08");

        // Y el atomo dice lo mismo sin base de datos: cumplido el plazo, entra.
        OffsetDateTime aviso = OffsetDateTime.now(ZoneOffset.UTC).minusDays(31);
        assertThat(new EntradaEnVigencia(aviso, 30, true).cumplidoEn(OffsetDateTime.now(ZoneOffset.UTC)))
                .isTrue();
        assertThat(new EntradaEnVigencia(aviso, 60, true).cumplidoEn(OffsetDateTime.now(ZoneOffset.UTC)))
                .isFalse();
    }
}
