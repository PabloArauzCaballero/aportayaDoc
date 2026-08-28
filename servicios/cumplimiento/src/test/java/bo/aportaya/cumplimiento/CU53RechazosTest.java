package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaReclamo;
import bo.aportaya.cumplimiento.aplicacion.CU52AtenderReclamo.EntradaRespuesta;
import bo.aportaya.cumplimiento.aplicacion.CU53ElevarReclamo.EntradaInstancia;
import bo.aportaya.cumplimiento.aplicacion.CU53ElevarReclamo.EntradaResolucion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-53 · Lo que la base y el caso de uso rechazan. */
class CU53RechazosTest extends BaseDeCumplimiento {

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

    private UUID reclamoRespondido() {
        var salida = transaccion.execute(t -> reclamoCU.ingresar(
                new EntradaReclamo(
                        usuario, punto, "COMISION", "BILLETERA", null, "Cobro no reconocido en el extracto.", "APP"),
                ctx));
        transaccion.execute(t -> reclamoCU.responder(
                new EntradaRespuesta(salida.reclamoId(), "DESFAVORABLE", "El cobro esta en el tarifario", null), ctx));
        return salida.reclamoId();
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // La instancia conserva su fecha de elevacion y su expediente: son la prueba de
        // que el cliente ejercio su derecho, y de cuando.
        UUID reclamo = reclamoRespondido();
        var elevacion = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "REGULADOR", "EXP-1", true), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.instancia_reclamo
                         WHERE id = ? AND fecha_elevacion IS NOT NULL AND numero_expediente IS NOT NULL
                        """,
                        elevacion.instanciaId()))
                .isEqualTo(1);
        // Y el reclamo de origen conserva sus diez años de conservacion.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.reclamo_cliente
                         WHERE id = ? AND conservar_hasta >= (fecha_ingreso + interval '10 years')::date
                        """,
                        reclamo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CON-04")
    void rechazaRCON04() {
        // Un resarcimiento exige la transaccion que lo materializa: la resolucion dice
        // cuanto, y sin el movimiento el cliente tiene un papel y no la plata.
        UUID reclamo = reclamoRespondido();
        var elevacion = transaccion.execute(
                t -> instanciaCU.elevar(new EntradaInstancia(reclamo, "DEFENSORIA", "EXP-2", true), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> instanciaCU.resolver(
                        new EntradaResolucion(
                                elevacion.instanciaId(), "Se ordena devolver", new BigDecimal("18.00"), null),
                        ctx)))
                .hasMessageContaining("es un papel");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.instancia_reclamo WHERE id = ? AND estado = 'PRESENTADA'",
                        elevacion.instanciaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CON-05")
    void rechazaRCON05() {
        // La instancia solo existe sobre un reclamo que existe: la clave foranea impide
        // elevar algo que no esta.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO cumplimiento.instancia_reclamo
                            (reclamo_id, instancia, fecha_elevacion, estado)
                        VALUES (gen_random_uuid(), 'REGULADOR', now(), 'PRESENTADA')
                        """))
                .contains("fk_instancia_reclamo_reclamo_id");

        // Y la instancia tiene catalogo cerrado: un organismo inventado deja el
        // expediente fuera de todo tablero.
        UUID reclamo = reclamoRespondido();
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.instancia_reclamo
                            (reclamo_id, instancia, fecha_elevacion, estado)
                        VALUES (?, 'OMBUDSMAN', now(), 'PRESENTADA')
                        """,
                        reclamo))
                .contains("ck_instancia_reclamo_instancia");
    }
}
