package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision.EntradaCotizacion;
import bo.aportaya.tarifas.dominio.MetodoDeCalculo;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-30 · las pruebas de RECHAZO, una por restriccion citada. */
class CU30RechazosTest extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String codigoCorto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("rechaza por R-CON-07")
    void rechazaRCON07() {
        // Sin tarifario publicado vigente no se cotiza. Denegar por omision: cobrar sin
        // tarifario publicado es exactamente lo que la transparencia prohibe.
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t -> cotizacionCU.cotizar(
                        new EntradaCotizacion(
                                "cot-sin-tar",
                                "NO-PUBLICADO",
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

        // Y la base no admite un tarifario VIGENTE sin publicar: la regla vive en la
        // DDL, no solo en la aplicacion.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.tarifario (id, codigo, version, nombre, estado, moneda_base,
                                                        vigente_desde, dias_preaviso)
                        VALUES (gen_random_uuid(), 'SIN-PUBLICAR', 1, 'Sin publicar', 'VIGENTE', 'BOB', now(), 0)
                        """))
                .contains("ck_tarifario_publicado");
    }

    @Test
    @DisplayName("rechaza por R-TAR-01")
    void rechazaRTAR01() {
        // Un solo tarifario vigente por codigo y rango. Dos vigentes a la vez
        // significan que nadie puede decir cual precio rige, y los dos son defendibles.
        String codigo = "DOBLE-" + codigoCorto();
        fixtura.activar(fixtura.tarifarioVigente(codigo));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO catalogo.tarifario (id, codigo, version, nombre, estado, moneda_base,
                                                        vigente_desde, dias_preaviso, publicado_en,
                                                        url_publicacion, hash_documento)
                        VALUES (gen_random_uuid(), '%s', 2, 'El otro vigente', 'VIGENTE', 'BOB', now(), 0,
                                now(), 'https://x.test', repeat('b', 64))
                        """
                                .formatted(codigo)))
                .contains("ex_tarifario_vigente");
    }

    @Test
    @DisplayName("rechaza por R-TAR-03")
    void rechazaRTAR03() {
        // Metodo y valores coherentes. Un concepto PORCENTUAL sin porcentaje cobra
        // cero, y nadie se entera hasta el cierre del mes.
        assertThatThrownBy(() -> MetodoDeCalculo.exigirCoherencia("PORCENTUAL", new BigDecimal("5.00"), null))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("le faltan los valores");
        assertThatThrownBy(() -> MetodoDeCalculo.exigirCoherencia("FIJO", null, new BigDecimal("0.03")))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThatThrownBy(() -> MetodoDeCalculo.exigirCoherencia("MIXTO", new BigDecimal("1.00"), null))
                .isInstanceOf(ErrorDeNegocio.class);

        // Y la base tiene el mismo CHECK: la aplicacion no es la unica barrera.
        String codigo = "INCOH-" + codigoCorto();
        UUID tarifario = fixtura.tarifarioBorrador(codigo);
        UUID hecho = fixtura.hechoGenerador("HECHO-" + codigoCorto());
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.concepto_tarifa
                            (id, tarifario_id, hecho_generador_id, codigo, nombre_comercial,
                             descripcion_usuario, metodo_calculo, base_calculo, sujeto_obligado,
                             forma_cobro, momento_cobro, gravado_iva, gravado_it,
                             precio_incluye_impuesto, orden_aplicacion, activo)
                        VALUES (gen_random_uuid(), '%s', '%s', 'MAL', 'Mal formado', 'Sin porcentaje',
                                'PORCENTUAL', 'MONTO_BOLSA_BRUTO', 'BENEFICIARIO_DEL_TURNO',
                                'DEDUCCION_DE_ENTREGA', 'AL_LIQUIDAR_ENTREGA', false, false, false, 1, true)
                        """
                                .formatted(tarifario, hecho)))
                .contains("ck_concepto_metodo");
    }

    @Test
    @DisplayName("rechaza por R-TAR-07")
    void rechazaRTAR07() {
        // Un grupo tiene UNA sola tarifa congelada. Perder el precio pactado a mitad
        // del pasanaku es cambiarle las reglas a alguien que ya no se puede ir.
        String codigo = "CONG-" + codigoCorto();
        UUID tarifario = fixtura.tarifarioVigente(codigo);
        fixtura.activar(tarifario);
        UUID grupo = fixtura.grupo();
        String insertar =
                """
                INSERT INTO tarifas.tarifa_congelada_grupo
                    (id, grupo_id, tarifario_id, snapshot_conceptos, hash_snapshot, congelada_en)
                VALUES (gen_random_uuid(), '%s', '%s', '[]'::jsonb, repeat('c', 64), now())
                """;
        dsl.execute(insertar.formatted(grupo, tarifario));

        assertThat(rechazaLaBase(insertar.formatted(grupo, tarifario))).contains("uq_tarifa_congelada_grupo");
    }

    @Test
    @DisplayName("rechaza por R-TAR-12")
    void rechazaRTAR12() {
        // Cuando el precio incluye impuesto, el impuesto se EXTRAE del total y no se
        // suma encima: sumarlo cobraria el impuesto dos veces sobre el mismo servicio.
        String codigoTarifario = "IVA-" + codigoCorto();
        UUID tarifario = fixtura.tarifarioVigente(codigoTarifario);
        String hechoCodigo = "ENTREGA-" + codigoCorto();
        UUID hecho = fixtura.hechoGenerador(hechoCodigo);
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + codigoCorto(), "0.01", "BANCARIO");
        UUID cuenta = fixtura.cuentaDeIngreso();
        fixtura.impuesto("IVA", "0.13");
        // sujeto_obligado BENEFICIARIO_DEL_TURNO + gravado_iva exige que el precio
        // incluya el impuesto: es ck_concepto_precio_final, y es la regla de fondo.
        fixtura.conceptoPorcentual(tarifario, hecho, redondeo, cuenta, "COM-IVA", "0.0030", null, null, true, true);
        fixtura.activar(tarifario);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        var salida = transaccion.execute(t -> cotizacionCU.cotizar(
                new EntradaCotizacion(
                        "cot-iva",
                        codigoTarifario,
                        hechoCodigo,
                        "ENTREGA_FONDO",
                        UUID.randomUUID(),
                        bob("10000.00"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                ctx));

        // 30,00 con el IVA ya adentro: el total mostrado ES el final.
        assertThat(salida.montoComision()).isEqualByComparingTo(bob("30.00"));
        assertThat(salida.montoTotal()).isEqualByComparingTo(bob("30.00"));
        assertThat(salida.montoImpuesto()).isEqualByComparingTo(bob("3.45"));

        // Y la base rechaza un concepto gravado que NO incluya el impuesto cuando lo
        // paga el beneficiario: mostrarle un precio al que despues se le suma 13% es
        // justo lo que R-TAR-12 impide.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.concepto_tarifa
                            (id, tarifario_id, hecho_generador_id, codigo, nombre_comercial,
                             descripcion_usuario, metodo_calculo, base_calculo, valor_porcentual,
                             sujeto_obligado, forma_cobro, momento_cobro, gravado_iva, gravado_it,
                             precio_incluye_impuesto, orden_aplicacion, activo)
                        VALUES (gen_random_uuid(), '%s', '%s', 'SIN-IVA-DENTRO', 'Precio enganoso',
                                'Le suman 13%% despues', 'PORCENTUAL', 'MONTO_BOLSA_BRUTO', 0.0030,
                                'BENEFICIARIO_DEL_TURNO', 'DEDUCCION_DE_ENTREGA', 'AL_LIQUIDAR_ENTREGA',
                                true, false, false, 2, true)
                        """
                                .formatted(tarifario, hecho)))
                .contains("ck_concepto_precio_final");
    }
}
