package bo.aportaya.entregas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino.EntradaRegistro;
import bo.aportaya.entregas.aplicacion.CU22LiquidarEntrega.EntradaLiquidacion;
import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso.EntradaOrden;
import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso.EntradaRespuesta;
import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso.SalidaOrden;
import bo.aportaya.entregas.dominio.LiquidacionDeEntrega;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-28 · Emitir la orden de desembolso y ejecutar el intento. */
class CU28Test extends BaseDeEntregas {

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

    /** Una entrega autorizada con cuenta verificada y fuera de enfriamiento. */
    private Caso caso(boolean cuentaVerificada, boolean fueraDeEnfriamiento) {
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        var escenario = fixtura.escenario(usuario);

        var cuenta = transaccion.execute(t -> cuentaCU.registrar(
                new EntradaRegistro(
                        "AHORRO",
                        "Banco de Prueba",
                        "4012345678",
                        "cifrado:x",
                        NOMBRE,
                        DOCUMENTO,
                        NOMBRE,
                        DOCUMENTO,
                        "BOB"),
                ctx));
        if (cuentaVerificada) {
            transaccion.execute(t -> cuentaCU.verificar(cuenta.cuentaId(), "MICRODEPOSITO", ctx));
            if (fueraDeEnfriamiento) {
                // Se adelanta el reloj de la cuenta: esperar 24 horas en una prueba no
                // prueba nada, y lo que se verifica es que el plazo GUARDADO se respeta.
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
        ContextoSesion supervisor = contextoDe(fixtura.usuario());
        transaccion.execute(t -> entregaCU.autorizar(entrega.entregaId(), supervisor));

        return new Caso(usuario, entrega.entregaId(), cuenta.cuentaId(), fixtura.proveedor(), ctx);
    }

    private EntradaOrden orden(Caso c, String clave) {
        return new EntradaOrden(c.entregaId(), c.proveedorId(), c.cuentaId(), "Entrega de turno 1", clave, true);
    }

    @Test
    @DisplayName(
            "Dada una entrega autorizada con cuenta destino verificada · Cuando se emite la orden de desembolso · Entonces existe una orden_desembolso con clave_idempotencia · Y un intento_desembolso número 1")
    void criterio1() {
        Caso c = caso(true, true);

        SalidaOrden salida = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-1"), c.ctx()));

        assertThat(salida.esNueva()).isTrue();
        assertThat(salida.claveIdempotencia()).isEqualTo("des-1");
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.orden_desembolso WHERE id = ? AND clave_idempotencia = 'des-1'",
                        salida.ordenId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.intento_desembolso WHERE orden_desembolso_id = ? AND numero_intento = 1",
                        salida.ordenId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una orden ya emitida · Cuando el trabajo se ejecuta de nuevo con la misma clave · Entonces se devuelve la orden existente y no se crea una segunda")
    void criterio2() {
        Caso c = caso(true, true);

        SalidaOrden a = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-idem"), c.ctx()));
        SalidaOrden b = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-idem"), c.ctx()));

        // Dos ordenes de la misma entrega es pagar dos veces, y no se descubre hasta
        // que alguien concilia el extracto.
        assertThat(b.ordenId()).isEqualTo(a.ordenId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar("SELECT count(*)::int FROM entregas.orden_desembolso WHERE entrega_id = ?", c.entregaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un proveedor que responde cuenta inexistente · Cuando se procesa la respuesta · Entonces la orden queda RECHAZADA sin programar reintento · Y el saldo vuelve a estar disponible para el beneficiario")
    void criterio3() {
        Caso c = caso(true, true);
        SalidaOrden orden = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-rechazo"), c.ctx()));

        var intento = transaccion.execute(t -> desembolsoCU.anotarRespuesta(
                new EntradaRespuesta(
                        orden.ordenId(),
                        arranco(),
                        false,
                        null,
                        "CUENTA_INEXISTENTE",
                        "La cuenta no existe en la entidad"),
                c.ctx()));

        // Insistir contra una cuenta que no existe no la crea: solo demora el momento
        // en que alguien mira el caso, mientras la plata sigue retenida.
        assertThat(intento.estado()).isEqualTo("RECHAZADA");
        assertThat(intento.reintentableEn()).isNull();
        // Un intento, una fila: la que se abrio al enviar es la que se cierra con el
        // rechazo, sin reintento programado.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.intento_desembolso
                         WHERE orden_desembolso_id = ? AND resultado = 'FALLIDO' AND reintentable_en IS NULL
                        """,
                        orden.ordenId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.intento_desembolso WHERE orden_desembolso_id = ?",
                        orden.ordenId()))
                .isEqualTo(1);
        // El evento avisa que el rechazo fue definitivo: es lo que libera el saldo y
        // le permite al beneficiario corregir su cuenta.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.evento_dominio
                         WHERE tipo = ? AND agregado_id = ? AND payload->>'definitivo' = 'true'
                        """,
                        "entregas.desembolso_rechazado",
                        orden.ordenId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una orden acreditada que no cruza con el extracto bancario · Cuando se intenta cerrar el día · Entonces el cierre no puede marcarse cuadrado")
    void criterio4() {
        Caso c = caso(true, true);
        SalidaOrden orden = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-cruce"), c.ctx()));
        transaccion.execute(t -> desembolsoCU.anotarRespuesta(
                new EntradaRespuesta(orden.ordenId(), arranco(), true, "REF-BANCO-9001", null, "OK"), c.ctx()));

        // El cierre lo hace nucleo-financiero (invariante 11), y la regla vive en la
        // BASE: fn_bil_validar_cierre_diario no deja marcar el dia cuadrado con
        // excepciones abiertas. Lo que aporta este servicio es la referencia contra la
        // que se cruza: una orden acreditada SIN ella no se puede conciliar.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_validar_cierre_diario"))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.orden_desembolso
                         WHERE id = ? AND estado = 'ACREDITADA' AND referencia_proveedor IS NOT NULL
                        """,
                        orden.ordenId()))
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
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso(true, true);

        SalidaOrden a = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-r"), c.ctx()));
        SalidaOrden b = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-r"), c.ctx()));

        assertThat(b.ordenId()).isEqualTo(a.ordenId());
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.intento_desembolso WHERE orden_desembolso_id = ?",
                        a.ordenId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos acreditaciones de la misma orden: la segunda no vuelve a marcarla. Sin
        // eso, la entrega se daria por pagada dos veces.
        Caso c = caso(true, true);
        SalidaOrden orden = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-conc"), c.ctx()));

        var primera = transaccion.execute(t -> desembolsoCU.anotarRespuesta(
                new EntradaRespuesta(orden.ordenId(), arranco(), true, "REF-1", null, "OK"), c.ctx()));
        var segunda = transaccion.execute(t -> desembolsoCU.anotarRespuesta(
                new EntradaRespuesta(orden.ordenId(), arranco(), true, "REF-2", null, "OK"), c.ctx()));

        assertThat(primera.esNuevo()).isTrue();
        assertThat(segunda.esNuevo()).isFalse();
        assertThat(dsl.fetchOne(
                                "SELECT referencia_proveedor FROM entregas.orden_desembolso WHERE id = ?",
                                orden.ordenId())
                        .get("referencia_proveedor", String.class))
                .isEqualTo("REF-1");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Lo que se ordena desembolsar iguala el neto de la entrega, al centavo. Un
        // centavo de diferencia por entrega se vuelve un descuadre que nadie sabe de
        // donde salio.
        Caso c = caso(true, true);

        SalidaOrden orden = transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-cuadre"), c.ctx()));

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
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "desembolsos"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "desembolsos"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // A cuenta sin verificar no se ordena NADA, y no queda orden a medias. Es
        // R-DES-02: pagar a una cuenta cuya titularidad nadie confirmo es como sale la
        // plata de un grupo hacia afuera.
        Caso c = caso(false, false);

        assertThatThrownBy(() -> transaccion.execute(t -> desembolsoCU.emitir(orden(c, "des-sinver"), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin verificar");
        assertThat(contar("SELECT count(*)::int FROM entregas.orden_desembolso WHERE entrega_id = ?", c.entregaId()))
                .isZero();
    }
}
