package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-111 · Lo que la base y el caso de uso rechazan. */
class CU111RechazosTest extends EscenarioDeCampana {

    @Test
    @DisplayName("rechaza por R-PUB-02")
    void rechazaRPUB02() {
        // La cuenta tiene limite de 9.000 y ya consumio 8.900: quedan 100, y una
        // campana de 500 no cabe. El limite es del mes, no de la campana.
        dsl.execute("UPDATE publicidad.cuenta_publicitaria SET saldo_consumido_mes = 8900.00 WHERE id = ?", cuentaId);
        var creada = transaccion.execute(t -> campanaCU.crear(campana("500.00", "50.00", "10.00", "CPM"), operaciones));

        assertThatThrownBy(
                        () -> transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("excede lo disponible del mes"));

        // Y una cuenta que no esta ACTIVA no toma campanas: denegar por omision.
        dsl.execute("UPDATE publicidad.cuenta_publicitaria SET estado = 'SUSPENDIDA' WHERE id = ?", cuentaId);
        assertThatThrownBy(() -> transaccion.execute(
                        t -> campanaCU.crear(campana("100.00", "10.00", "5.00", "CPM"), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("no admite campanas nuevas"));
    }

    @Test
    @DisplayName("rechaza por R-PUB-03")
    void rechazaRPUB03() {
        var creada =
                transaccion.execute(t -> campanaCU.crear(campana("1000.00", "100.00", "10.00", "CPM"), operaciones));

        // Una campana ACTIVA sin aprobador es publicidad que salio al aire sin que
        // nadie la haya mirado.
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.campana_publicitaria SET estado = 'ACTIVA', aprobada_por = NULL WHERE id = ?",
                        creada.campanaPublicitariaId()))
                .contains("ck_campana_pub_aprobacion");

        // El consumo no pasa del presupuesto: mas alla de eso se estaria entregando
        // publicidad que el anunciante no autorizo.
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.campana_publicitaria SET presupuesto_consumido = 1000.01 WHERE id = ?",
                        creada.campanaPublicitariaId()))
                .contains("ck_campana_pub_consumo");

        // Y la vigencia va hacia adelante.
        assertThat(rechazaLaBase(
                        """
                        UPDATE publicidad.campana_publicitaria
                           SET fecha_fin = fecha_inicio - interval '1 day'
                         WHERE id = ?
                        """,
                        creada.campanaPublicitariaId()))
                .contains("ck_campana_pub_vigencia");

        // El espacio tampoco admite cupo cero: un espacio que no muestra nada no es un
        // espacio, es una fila que confunde.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.espacio_publicitario
                            (codigo, nombre, tipo, capacidad_maxima_simultanea, activo)
                        VALUES (?, 'Sin cupo', 'BANNER_INICIO', 0, true)
                        """,
                        "ESP-CERO-" + UUID.randomUUID().toString().substring(0, 6)))
                .contains("ck_espacio_pub_capacidad");
    }
}
