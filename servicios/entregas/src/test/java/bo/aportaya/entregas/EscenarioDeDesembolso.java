package bo.aportaya.entregas;

import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino.EntradaRegistro;
import bo.aportaya.entregas.aplicacion.CU22LiquidarEntrega.EntradaLiquidacion;
import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso.EntradaOrden;
import bo.aportaya.entregas.dominio.LiquidacionDeEntrega;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;

/**
 * El escenario que comparten las pruebas de CU-28: una entrega autorizada, con su
 * cuenta de destino y su proveedor de pago.
 *
 * <p>Se arma de punta a punta —cuenta, verificacion, liquidacion, autorizacion— porque
 * una orden de desembolso solo existe sobre una entrega que paso por todo eso. Un
 * escenario armado a mano en la base probaria un camino que en produccion no ocurre.
 */
abstract class EscenarioDeDesembolso extends BaseDeEntregas {

    protected static final String NOMBRE = "Maria Fernanda Quispe";
    protected static final String DOCUMENTO = "8123456";

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    protected Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    protected OffsetDateTime arranco() {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2);
    }

    protected record Caso(UUID usuario, UUID entregaId, UUID cuentaId, UUID proveedorId, ContextoSesion ctx) {}

    /** Una entrega autorizada con cuenta verificada y fuera de enfriamiento. */
    protected Caso caso(boolean cuentaVerificada, boolean fueraDeEnfriamiento) {
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

    protected EntradaOrden orden(Caso c, String clave) {
        return new EntradaOrden(c.entregaId(), c.proveedorId(), c.cuentaId(), "Entrega de turno 1", clave, true);
    }
}
