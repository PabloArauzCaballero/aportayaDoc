package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaProrroga;
import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaReclamo;
import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaRespuesta;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-52 · Lo que la base y el caso de uso rechazan. */
class CU52RechazosTest extends BaseDeCumplimiento {

    private static final String DESCRIPCION =
            "Me cobraron una comision que no figura en el tarifario publicado del mes.";

    private UUID usuario;
    private String punto;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        punto = "APP-" + UUID.randomUUID().toString().substring(0, 8);
        gobiernoFixtura.puntoDeReclamo(punto, "APP", true);
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaReclamo reclamo(String monto) {
        return new EntradaReclamo(
                usuario,
                punto,
                "COMISION",
                "BILLETERA",
                monto == null ? null : new BigDecimal(monto),
                DESCRIPCION,
                "APP");
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // Diez años de conservacion, guardados al ingresar. Sin la fecha, el expediente
        // se depuraria antes de que el cliente pueda reclamarlo.
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));

        assertThat(rechazaLaBase(
                        """
                        UPDATE cumplimiento.reclamo_cliente
                           SET conservar_hasta = (fecha_ingreso + interval '2 years')::date
                         WHERE id = ?
                        """,
                        salida.reclamoId()))
                .contains("ck_reclamo_conservacion");
    }

    @Test
    @DisplayName("rechaza por R-CON-01")
    void rechazaRCON01() {
        // El plazo se guarda y va de 1 a 5 dias habiles. Uno de quince no es una
        // atencion mas holgada: es incumplir la norma con otro numero.
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));

        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.reclamo_cliente SET dias_habiles_plazo = 15 WHERE id = ?",
                        salida.reclamoId()))
                .contains("ck_reclamo_dias");
        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.reclamo_cliente SET plazo_respuesta = fecha_ingreso - interval '1 day' WHERE id = ?",
                        salida.reclamoId()))
                .contains("ck_reclamo_plazo");
    }

    @Test
    @DisplayName("rechaza por R-CON-02")
    void rechazaRCON02() {
        // La prorroga se comunica al cliente ANTES de que venza el plazo original.
        // Avisar despues es contarle que ya lo hicimos esperar de mas.
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reclamoCU.prorrogar(
                            new EntradaProrroga(
                                    salida.reclamoId(),
                                    salida.plazoRespuesta().plusDays(2),
                                    salida.plazoRespuesta().plusDays(1),
                                    null,
                                    null),
                            ctx);
                    return null;
                }))
                .hasMessageContaining("antes de que venza");

        assertThat(rechazaLaBase(
                        """
                        UPDATE cumplimiento.reclamo_cliente
                           SET plazo_prorrogado_hasta = plazo_respuesta + interval '2 days'
                         WHERE id = ?
                        """,
                        salida.reclamoId()))
                .contains("ck_reclamo_prorroga");
    }

    @Test
    @DisplayName("rechaza por R-CON-03")
    void rechazaRCON03() {
        // Pasar del maximo exige comunicacion escrita al organismo y justificacion.
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reclamoCU.prorrogar(
                            new EntradaProrroga(
                                    salida.reclamoId(),
                                    salida.plazoRespuesta().plusDays(30),
                                    OffsetDateTime.now(ZoneOffset.UTC),
                                    null,
                                    null),
                            ctx);
                    return null;
                }))
                .hasMessageContaining("comunicacion escrita al organismo");

        assertThat(rechazaLaBase(
                        """
                        UPDATE cumplimiento.reclamo_cliente
                           SET plazo_prorrogado_hasta = fecha_ingreso + interval '40 days',
                               prorroga_comunicada_al_cliente_en = fecha_ingreso
                         WHERE id = ?
                        """,
                        salida.reclamoId()))
                .contains("ck_reclamo_prorroga_extendida");
    }

    @Test
    @DisplayName("rechaza por R-CON-04")
    void rechazaRCON04() {
        // Un reclamo favorable con monto exige reparacion asociada. Darle la razon a
        // alguien y no devolverle la plata es darsela de mentira.
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo("18.00"), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> reclamoCU.responder(
                        new EntradaRespuesta(salida.reclamoId(), "FAVORABLE", "Tenia razon", null), ctx)))
                .hasMessageContaining("exige la devolucion");

        assertThat(rechazaLaBase(
                        """
                        UPDATE cumplimiento.reclamo_cliente
                           SET estado = 'CERRADO', resultado = 'FAVORABLE'
                         WHERE id = ?
                        """,
                        salida.reclamoId()))
                .contains("ck_reclamo_reparacion");
    }

    @Test
    @DisplayName("rechaza por R-CON-05")
    void rechazaRCON05() {
        // Los reclamos se conservan diez años, y el codigo es unico: dos con el mismo
        // codigo harian imposible seguir uno de los dos.
        var salida = transaccion.execute(t -> reclamoCU.ingresar(reclamo(null), ctx));
        String codigo = dsl.fetchOne("SELECT codigo FROM cumplimiento.reclamo_cliente WHERE id = ?", salida.reclamoId())
                .get(0, String.class);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.reclamo_cliente
                            (codigo, usuario_id, punto_reclamo_id, categoria, producto, descripcion,
                             canal_ingreso, estado, dias_habiles_plazo, plazo_respuesta, conservar_hasta)
                        SELECT ?, usuario_id, punto_reclamo_id, categoria, producto, descripcion,
                               canal_ingreso, 'INGRESADO', dias_habiles_plazo, plazo_respuesta, conservar_hasta
                          FROM cumplimiento.reclamo_cliente WHERE id = ?
                        """,
                        codigo,
                        salida.reclamoId()))
                .contains("uq_reclamo_codigo");
    }
}
