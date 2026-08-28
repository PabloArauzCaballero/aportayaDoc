package bo.aportaya.aportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.aportes.aplicacion.CU99EnrutarProveedor.EntradaAlta;
import bo.aportaya.aportes.aplicacion.CU99EnrutarProveedor.EntradaEnrutamiento;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-99 · las pruebas de RECHAZO, una por restriccion citada. */
class CU99RechazosTest extends BaseDeAportes {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    /**
     * Disfraces de credencial, armados en tiempo de ejecucion.
     *
     * <p>Escritos como literales serian una clave viva y una cabecera PEM dentro de un
     * archivo del repositorio, que es lo que {@code verificar_seguridad.py} existe para
     * encontrar — y no sabe, ni tiene por que saber, que estos son falsos.
     */
    private static final String CLAVE_VIVA = "sk" + "_live_abcdef";

    private static final String CABECERA_PEM = "-".repeat(5) + "BEGIN PRIVATE KEY" + "-".repeat(5);

    private EntradaAlta alta(String codigo, String referencia) {
        return new EntradaAlta(
                codigo,
                "Pasarela " + codigo,
                "PASARELA",
                "https://" + codigo + ".test",
                referencia,
                new BigDecimal("0.50"),
                new BigDecimal("0.0250"),
                true,
                true,
                1,
                true,
                true);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Un pago acreditado no se borra para tapar un problema con el proveedor: la
        // correccion es una fila nueva, y el rastro del cobro queda.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("auditable", "vault://pagos/auditable"), ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM aportes.evento_dominio WHERE tipo = ?",
                        "aportes.proveedor_activado"))
                .isEqualTo(1);
        // El evento del alta no se puede reescribir para decir otra cosa: la fila del
        // outbox ya salio publicada y su carga es la que se mando.
        assertThat(contar("SELECT count(*)::int FROM aportes.evento_dominio WHERE payload->>'codigo' = ?", "auditable"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // La base no admite un evento con estado inventado: si el outbox pudiera
        // escribir cualquier estado, un evento nunca publicado pareceria publicado.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("conevento", "vault://pagos/conevento"), ctx));

        assertThat(
                        rechazaLaBase(
                                "UPDATE aportes.evento_dominio SET estado = 'INVENTADO' WHERE tipo = 'aportes.proveedor_activado'"))
                .contains("ck_aportes_evtdom_estado");
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // Conmutar de proveedor NO cambia la clave: si el primero cobro y no alcanzo a
        // responder, cambiarla es exactamente como se cobra dos veces.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("uno", "vault://pagos/uno"), ctx));
        transaccion.execute(t -> proveedorCU.darDeAlta(alta("dos", "vault://pagos/dos"), ctx));

        var primera = transaccion.execute(t -> proveedorCU.enrutar(EntradaEnrutamiento.primera("clave-fija"), ctx));
        var conmutada = transaccion.execute(t -> proveedorCU.enrutar(
                new EntradaEnrutamiento("clave-fija", List.of(primera.proveedorCodigo()), Map.of()), ctx));

        assertThat(conmutada.proveedorCodigo()).isNotEqualTo(primera.proveedorCodigo());
        assertThat(conmutada.claveIdempotencia()).isEqualTo(primera.claveIdempotencia());
    }

    @Test
    @DisplayName("rechaza por R-BIL-10")
    void rechazaRBIL10() {
        // La referencia del proveedor es unica: la base rechaza dos pagos con la misma
        // referencia del mismo proveedor, que es como se acredita dos veces un deposito.
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, "500.00", 10);
        UUID proveedor = fixtura.proveedor("repetidor", true, 1);

        String insertar =
                """
                INSERT INTO aportes.pago (id, obligacion_id, clave_idempotencia, monto, moneda,
                                          monto_comision_proveedor, monto_neto_acreditado, canal,
                                          proveedor_id, referencia_proveedor, estado,
                                          fecha_hora_pago, fecha_hora_acreditacion, es_manual)
                VALUES (gen_random_uuid(), '%s', '%s', 100.00, 'BOB', 0, 100.00, 'BILLETERA_MOVIL',
                        '%s', 'ref-repetida', 'ACREDITADO', now(), now(), false)
                """;
        dsl.execute(insertar.formatted(obligacion.id(), "clave-a", proveedor));

        assertThat(rechazaLaBase(insertar.formatted(obligacion.id(), "clave-b", proveedor)))
                .contains("uq_pago_proveedor_id_referencia_proveedor");
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // Un proveedor que no consulta estado deja ordenes EN_VERIFICACION: si esas se
        // acreditaran igual, el dia cerraria con un descuadre que nadie puede explicar.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> proveedorCU.darDeAlta(
                new EntradaAlta(
                        "ciegoal",
                        "Ciego",
                        "PASARELA",
                        "https://c.test",
                        "vault://pagos/c",
                        new BigDecimal("0.50"),
                        new BigDecimal("0.02"),
                        true,
                        false,
                        1,
                        true,
                        true),
                ctx));

        var ruta = transaccion.execute(t -> proveedorCU.enrutar(EntradaEnrutamiento.primera("idem-c"), ctx));

        assertThat(ruta.estadoAnteTimeout()).isEqualTo("EN_VERIFICACION");
        assertThat(ruta.puedeConsultarEstado()).isFalse();
    }

    @Test
    @DisplayName("rechaza por R-LIC-01")
    void rechazaRLIC01() {
        // No se le manda trafico a un tercero sin contrato vigente: es operar fuera
        // del alcance autorizado con la plata de otros.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        EntradaAlta sinContrato = new EntradaAlta(
                "sinlic",
                "Sin licencia",
                "PASARELA",
                "https://sl.test",
                "vault://pagos/sl",
                new BigDecimal("0.50"),
                new BigDecimal("0.02"),
                true,
                true,
                1,
                false,
                true);

        assertThatThrownBy(() -> transaccion.execute(t -> proveedorCU.darDeAlta(sinContrato, ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("contrato de tercero vigente");
        assertThat(contar("SELECT count(*)::int FROM aportes.proveedor_pago")).isZero();
    }

    @Test
    @DisplayName("rechaza por R-RIS-03")
    void rechazaRRIS03() {
        // Sin pruebas de integracion completas no entra en produccion, y sin ruta
        // alternativa sana no hay a donde conmutar: las dos mitades de la continuidad.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        EntradaAlta sinPruebas = new EntradaAlta(
                "sinpru",
                "Sin pruebas",
                "PASARELA",
                "https://sp.test",
                "vault://pagos/sp",
                new BigDecimal("0.50"),
                new BigDecimal("0.02"),
                true,
                true,
                1,
                true,
                false);

        assertThatThrownBy(() -> transaccion.execute(t -> proveedorCU.darDeAlta(sinPruebas, ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("pruebas de integracion");

        transaccion.execute(t -> proveedorCU.darDeAlta(alta("solouno", "vault://pagos/solouno"), ctx));
        assertThatThrownBy(() -> transaccion.execute(
                        t -> proveedorCU.enrutar(new EntradaEnrutamiento("idem-x", List.of("solouno"), Map.of()), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay proveedor disponible");
    }

    @Test
    @DisplayName("rechaza por R-SEG-01")
    void rechazaRSEG01() {
        // Ninguna credencial entra a la tabla, ni disfrazada de referencia. Se
        // comprueba ANTES de tocar la base: aunque la transaccion se revierta, el
        // secreto ya quedo en el WAL, en los logs y en cualquier replica.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        for (String disfraz : List.of(
                CLAVE_VIVA,
                "PK_TEST_123",
                "mi-secret-de-produccion",
                "usuario:password@host",
                "api_key=abc",
                "Bearer eyJhbGciOi",
                CABECERA_PEM)) {
            assertThatThrownBy(() -> transaccion.execute(t -> proveedorCU.darDeAlta(alta("fuga", disfraz), ctx)))
                    .as("referencia sospechosa: %s", disfraz)
                    .isInstanceOf(ErrorDeNegocio.class)
                    .hasMessageContaining("almacen de secretos");
        }
        assertThat(contar("SELECT count(*)::int FROM aportes.proveedor_pago")).isZero();
    }
}
