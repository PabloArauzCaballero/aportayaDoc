package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU95DefinirAutomatizacion.EntradaRegla;
import bo.aportaya.organizador.aplicacion.CU95DefinirAutomatizacion.SalidaRegla;
import bo.aportaya.organizador.dominio.AccionSensible;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-95 · las pruebas de RECHAZO, una por restriccion citada. */
class CU95RechazosTest extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private EntradaRegla regla(String accion, boolean confirmacion, int prioridad) {
        return new EntradaRegla(
                "REGLA-" + corto(),
                "Regla de prueba",
                "CRON",
                "0 8 * * *",
                "dias_para_vencer = 3",
                accion,
                confirmacion,
                prioridad);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Una regla no se borra para tapar lo que hizo: su codigo es lo que ata cada
        // tarea ejecutada a la decision que la ordeno.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaRegla definida = transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 70), ctx));

        assertThat(rechazaLaBase("UPDATE organizador.regla_automatizacion SET codigo = NULL WHERE id = '%s'"
                        .formatted(definida.reglaId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaRegla definida = transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 71), ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "organizador.regla_definida",
                        definida.reglaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-ORG-06")
    void rechazaRORG06() {
        // Las acciones sensibles exigen confirmacion humana. Se comprueba ANTES de
        // tocar la base: una regla sensible marcada como automatica que llega a la
        // tabla puede dispararse antes de que alguien la revise.
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        for (String sensible : AccionSensible.EXIGEN_CONFIRMACION) {
            assertThatThrownBy(() -> transaccion.execute(t -> reglaCU.definir(regla(sensible, false, 72), ctx)))
                    .as("accion sensible sin confirmacion: %s", sensible)
                    .isInstanceOf(ErrorDeNegocio.class)
                    .hasMessageContaining("exige confirmacion humana");
        }
        assertThat(contar("SELECT count(*)::int FROM organizador.regla_automatizacion"))
                .isZero();

        // Y una accion fuera del catalogo tampoco entra: la base tiene la lista.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.regla_automatizacion
                            (id, codigo, descripcion, disparador, expresion_disparo, condicion, accion,
                             requiere_confirmacion_humana, prioridad, activa)
                        VALUES (gen_random_uuid(), 'INVENTADA-%s', 'Accion de fantasia', 'CRON',
                                '0 8 * * *', 'siempre', 'VACIAR_LA_CAJA', false, 99, false)
                        """
                                .formatted(corto())))
                .contains("ck_regla_automatizacion_accion");
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // Dos reglas activas con el mismo disparador y prioridad hacen que el orden de
        // ejecucion dependa de como la base devuelva las filas: dos corridas del mismo
        // dia harian cosas distintas. La BASE lo impide.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaRegla primera = transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 73), ctx));
        transaccion.execute(t -> reglaCU.activar(primera.reglaId(), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.regla_automatizacion
                            (id, codigo, descripcion, disparador, expresion_disparo, condicion, accion,
                             requiere_confirmacion_humana, prioridad, activa)
                        VALUES (gen_random_uuid(), 'COLADA-%s', 'Se salta la aplicacion', 'CRON',
                                '0 9 * * *', 'siempre', 'GENERAR_COBROS', false, 73, true)
                        """
                                .formatted(corto())))
                .contains("uq_regla_automatizacion_prioridad");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Definir y encender son actos separados. Una regla nace inactiva: publicarla y
        // encenderla en el mismo acto no deja momento para que otra persona revise que
        // la condicion diga lo que se cree que dice.
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaRegla definida = transaccion.execute(t -> reglaCU.definir(regla("ENVIAR_RECORDATORIO", false, 74), ctx));

        assertThat(definida.activa()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.regla_automatizacion WHERE id = ? AND NOT activa",
                        definida.reglaId()))
                .isEqualTo(1);
    }
}
