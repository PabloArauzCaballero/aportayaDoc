package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU51EjecutarCierreDiario.EntradaCierre;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-51 · las pruebas de RECHAZO, una por restriccion citada. */
class CU51RechazosTest extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        dsl.execute("DELETE FROM nucleo_financiero.cierre_diario");
        dsl.execute("DELETE FROM nucleo_financiero.conciliacion_custodia");
        fixtura.limpiarBilleteras();
    }

    private LocalDate diaDe(int desplazamiento) {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().minusDays(desplazamiento);
    }

    private EntradaCierre entrada(LocalDate fecha, int excepciones, boolean custodiaCuadrada) {
        return new EntradaCierre(
                fecha,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                1,
                excepciones,
                custodiaCuadrada);
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // Un asiento sin confirmar en la fecha frena el cierre: la contabilidad del
        // dia todavia se esta escribiendo, y cerrar sobre ella es firmar un numero
        // que aun puede cambiar.
        LocalDate fecha = diaDe(121);
        UUID usuario = fixtura.usuario();
        fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);
        UUID cuenta =
                contable.cuentaDeMovimiento(String.valueOf(System.nanoTime()).substring(8, 14), "ACTIVO", "DEUDORA");
        UUID asiento = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.asiento_contable
                    (id, fecha, glosa, origen_tipo, origen_id, estado)
                VALUES (?, ?, 'a medio escribir', 'AJUSTE', gen_random_uuid(), 'BORRADOR')
                """,
                asiento,
                fecha);
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.movimiento_contable
                    (id, asiento_id, cuenta_id, debe, haber, descripcion)
                VALUES (gen_random_uuid(), ?, ?, 5.00, 0.00, 'solo debe')
                """,
                asiento,
                cuenta);

        assertThatThrownBy(() -> transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin confirmar");
    }

    @Test
    @DisplayName("rechaza por R-AUD-07")
    void rechazaRAUD07() {
        // Un saldo diario por cuenta y fecha, y encadenado. La BASE rechaza el
        // segundo: dos fotos del mismo dia harian imposible saber cual es la buena.
        LocalDate fecha = diaDe(122);
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);
        transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, true), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.saldo_diario_billetera
                            (id, cuenta_billetera_id, fecha, saldo_disponible, saldo_retenido,
                             cantidad_movimientos, hash_registro, cerrado_en)
                        VALUES (gen_random_uuid(), '%s', DATE '%s', 0, 0, 0, repeat('d', 64), now())
                        """
                                .formatted(cuenta, fecha)))
                .isNotEmpty();
        // Y tampoco se puede corregir la que ya esta: es append-only.
        assertThat(rechazaLaBase(
                        "UPDATE nucleo_financiero.saldo_diario_billetera SET saldo_disponible = 999 WHERE cuenta_billetera_id = '%s' AND fecha = DATE '%s'"
                                .formatted(cuenta, fecha)))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // La BASE lo impide, no la aplicacion: fn_bil_validar_cierre_diario cuenta las
        // excepciones y los descuadres de custodia de esa fecha y no deja marcar el
        // dia cuadrado. Cerrar con un descuadre abierto es firmar que se sabe cuanto
        // hay cuando todavia no se sabe.
        LocalDate fecha = diaDe(123);
        UUID usuario = fixtura.usuario();
        fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(usuario);
        UUID cuentaCustodia = custodia.cuentaDeCustodia();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.conciliacion_custodia
                    (id, cuenta_custodia_id, fecha, saldo_dinero_electronico, saldo_custodia,
                     saldo_en_transito, cumple_encaje, estado, ejecutada_en)
                VALUES (gen_random_uuid(), ?, ?, 100.00, 90.00, 0, false,
                        'DESCUADRADA', now())
                """,
                cuentaCustodia,
                fecha);

        // El caso de uso no fuerza el cuadre: con la custodia mal, lo marca descuadrado.
        var salida = transaccion.execute(t -> cierreDiarioCU.ejecutar(entrada(fecha, 0, false), ctx));
        assertThat(salida.cuadrado()).isFalse();

        // Y si alguien intentara marcarlo cuadrado igual, la base lo rechaza por su
        // nombre: no hay forma de cerrar el dia "a mano" desde ningun servicio.
        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cierre_diario SET cuadrado = true WHERE fecha = DATE '%s'"
                        .formatted(fecha)))
                .contains("R-BIL-12");
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.cierre_diario
                            (id, fecha, total_recaudado, total_conciliado, total_excepciones,
                             cantidad_pagos, cuadrado, cerrado_por, cerrado_en)
                        VALUES (gen_random_uuid(), DATE '%s', 1.00, 1.00, 0.00, 1, true,
                                gen_random_uuid(), now())
                        """
                                .formatted(diaDe(124))))
                .isNotEmpty();
    }
}
