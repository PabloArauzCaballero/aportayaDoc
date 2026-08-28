package bo.aportaya.entregas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino.EntradaRegistro;
import bo.aportaya.entregas.aplicacion.CU22LiquidarEntrega.EntradaLiquidacion;
import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso.EntradaOrden;
import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso.EntradaRespuesta;
import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso.SalidaOrden;
import bo.aportaya.entregas.dominio.LiquidacionDeEntrega;
import bo.aportaya.entregas.dominio.ReintentoDeDesembolso;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-28 · las pruebas de RECHAZO, una por restriccion citada. */
class CU28RechazosTest extends BaseDeEntregas {

    private static final String NOMBRE = "Maria Fernanda Quispe";
    private static final String DOCUMENTO = "8123456";

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private OffsetDateTime arranco() {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
    }

    private record Caso(UUID usuario, UUID entregaId, UUID cuentaId, UUID proveedorId, ContextoSesion ctx) {}

    private Caso caso(boolean verificada, boolean fueraDeEnfriamiento) {
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        var escenario = fixtura.escenario(usuario);
        var cuenta = transaccion.execute(t -> cuentaCU.registrar(
                new EntradaRegistro(
                        "AHORRO", "Banco", "4098765432", "cifrado:x", NOMBRE, DOCUMENTO, NOMBRE, DOCUMENTO, "BOB"),
                ctx));
        if (verificada) {
            transaccion.execute(t -> cuentaCU.verificar(cuenta.cuentaId(), "MICRODEPOSITO", ctx));
            if (fueraDeEnfriamiento) {
                dsl.execute(
                        "UPDATE entregas.cuenta_bancaria_beneficiario SET bloqueada_hasta = now() - interval '1 hour' WHERE id = ?",
                        cuenta.cuentaId());
            }
        }
        var entrega = transaccion.execute(t -> entregaCU.liquidar(
                new EntradaLiquidacion(
                        escenario.grupoId(),
                        escenario.periodoId(),
                        escenario.turnoId(),
                        escenario.cupoId(),
                        escenario.participanteId(),
                        bob("6000.00"),
                        bob("6000.00"),
                        List.of(new LiquidacionDeEntrega.Deduccion(
                                "COMISION_PLATAFORMA", "Comision", bob("18.00"), UUID.randomUUID(), true)),
                        "TRANSFERENCIA_BANCARIA",
                        LocalDate.now()),
                ctx));
        transaccion.execute(t -> entregaCU.autorizar(entrega.entregaId(), contextoDe(fixtura.usuario())));
        return new Caso(usuario, entrega.entregaId(), cuenta.cuentaId(), fixtura.proveedor(), ctx);
    }

    private SalidaOrden emitir(Caso c, String clave) {
        return transaccion.execute(t -> desembolsoCU.emitir(
                new EntradaOrden(c.entregaId(), c.proveedorId(), c.cuentaId(), "Turno 1", clave, true), c.ctx()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El intento conserva su resultado y su momento: sin ellos no hay forma de
        // reconstruir que le paso a esa plata.
        Caso c = caso(true, true);
        SalidaOrden orden = emitir(c, "aud-01");

        assertThat(rechazaLaBase(
                        "UPDATE entregas.intento_desembolso SET resultado = NULL WHERE orden_desembolso_id = '%s'"
                                .formatted(orden.ordenId())))
                .isNotEmpty();
        assertThat(rechazaLaBase(
                        "UPDATE entregas.intento_desembolso SET iniciado_en = NULL WHERE orden_desembolso_id = '%s'"
                                .formatted(orden.ordenId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // El asiento lo escribe nucleo-financiero, y necesita el monto acreditado y su
        // referencia bancaria. Sin la referencia, la acreditacion no cruza con el
        // extracto y el dia no cierra.
        Caso c = caso(true, true);
        SalidaOrden orden = emitir(c, "aud-05");
        transaccion.execute(t -> desembolsoCU.anotarRespuesta(
                new EntradaRespuesta(orden.ordenId(), arranco(), true, "REF-CRUCE-1", null, "OK"), c.ctx()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.evento_dominio
                         WHERE tipo = ? AND agregado_id = ? AND payload->>'referenciaProveedor' = 'REF-CRUCE-1'
                        """,
                        "entregas.desembolso_acreditado",
                        orden.ordenId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // Lo ordenado iguala el neto de la entrega, al centavo.
        Caso c = caso(true, true);
        SalidaOrden orden = emitir(c, "bil-01");

        var fila = dsl.fetchOne(
                """
                SELECT o.monto AS ordenado, e.monto_neto_a_entregar AS neto
                  FROM entregas.orden_desembolso o
                  JOIN entregas.entrega_fondo e ON e.id = o.entrega_id
                 WHERE o.id = ?
                """,
                orden.ordenId());
        assertThat(fila.get("ordenado", java.math.BigDecimal.class))
                .isEqualByComparingTo(fila.get("neto", java.math.BigDecimal.class));
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        // El monto de la orden nunca es negativo ni cero: una orden de desembolso por
        // cero le ocupa un lugar al beneficiario sin entregarle nada.
        Caso c = caso(true, true);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.orden_desembolso
                            (id, entrega_id, proveedor_id, cuenta_destino_id, monto, moneda, estado,
                             glosa, clave_idempotencia)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', -1.00, 'BOB', 'CREADA', 'negativa',
                                'bil-02-neg')
                        """
                                .formatted(c.entregaId(), c.proveedorId(), c.cuentaId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // Idempotencia de toda operacion con dinero: la BASE rechaza dos ordenes con
        // la misma clave para la misma entrega, aunque la aplicacion se equivoque.
        Caso c = caso(true, true);
        emitir(c, "bil-06");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.orden_desembolso
                            (id, entrega_id, proveedor_id, cuenta_destino_id, monto, moneda, estado,
                             glosa, clave_idempotencia)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', 5982.00, 'BOB', 'CREADA', 'colada',
                                'bil-06')
                        """
                                .formatted(c.entregaId(), c.proveedorId(), c.cuentaId())))
                .contains("uq_orden_desembolso_clave");
    }

    @Test
    @DisplayName("rechaza por R-BIL-09")
    void rechazaRBIL09() {
        // No se desembolsa a una cuenta dentro de su ventana de enfriamiento. Si
        // alguien tomo la sesion y cambio el destino, la ventana le da al titular real
        // el tiempo de enterarse.
        Caso c = caso(true, false);

        assertThatThrownBy(() -> emitir(c, "bil-09"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ventana de enfriamiento");
        assertThat(contar("SELECT count(*)::int FROM entregas.orden_desembolso WHERE entrega_id = ?", c.entregaId()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // El dia no se marca cuadrado con excepciones abiertas, y lo impide la BASE.
        // Una orden acreditada sin referencia bancaria es un descuadre garantizado.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_validar_cierre_diario"))
                .isEqualTo(1);
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.cierre_diario
                            (id, fecha, total_recaudado, total_conciliado, total_excepciones,
                             cantidad_pagos, cuadrado, cerrado_por, cerrado_en)
                        VALUES (gen_random_uuid(), current_date, 100.00, 50.00, 50.00, 1, true,
                                gen_random_uuid(), now())
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-DES-01")
    void rechazaRDES01() {
        // Una orden VIVA por entrega. Dos es pagar dos veces, y no se descubre hasta
        // que alguien concilia el extracto.
        Caso c = caso(true, true);
        emitir(c, "des-01");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.orden_desembolso
                            (id, entrega_id, proveedor_id, cuenta_destino_id, monto, moneda, estado,
                             glosa, clave_idempotencia)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', 5982.00, 'BOB', 'CREADA', 'segunda viva',
                                'des-01-otra')
                        """
                                .formatted(c.entregaId(), c.proveedorId(), c.cuentaId())))
                .contains("uq_orden_desembolso_entrega_viva");
    }

    @Test
    @DisplayName("rechaza por R-DES-02")
    void rechazaRDES02() {
        // No se ordena un desembolso a cuenta sin verificar. Lo impide la aplicacion
        // con su mensaje y la BASE con su trigger: pagar a una cuenta cuya titularidad
        // nadie confirmo es como sale la plata de un grupo hacia afuera.
        Caso c = caso(false, false);

        assertThatThrownBy(() -> emitir(c, "des-02"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin verificar");
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.orden_desembolso
                            (id, entrega_id, proveedor_id, cuenta_destino_id, monto, moneda, estado,
                             glosa, clave_idempotencia)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', 5982.00, 'BOB', 'CREADA',
                                'saltando la app', 'des-02-base')
                        """
                                .formatted(c.entregaId(), c.proveedorId(), c.cuentaId())))
                .contains("R-DES-02");

        // Y el atomo dice, sin base de datos, que los errores definitivos no se
        // reintentan: la cuenta cerrada no se reabre porque insistamos.
        assertThat(ReintentoDeDesembolso.esDefinitivo("CUENTA_INEXISTENTE")).isTrue();
        assertThat(ReintentoDeDesembolso.esDefinitivo("TIMEOUT_PROVEEDOR")).isFalse();
        assertThat(ReintentoDeDesembolso.siguiente(
                        "CUENTA_CERRADA", 1, 3, Duration.ofMinutes(15), OffsetDateTime.now(ZoneOffset.UTC)))
                .isEmpty();
        assertThat(ReintentoDeDesembolso.siguiente(
                        "TIMEOUT_PROVEEDOR", 1, 3, Duration.ofMinutes(15), OffsetDateTime.now(ZoneOffset.UTC)))
                .isPresent();
    }
}
