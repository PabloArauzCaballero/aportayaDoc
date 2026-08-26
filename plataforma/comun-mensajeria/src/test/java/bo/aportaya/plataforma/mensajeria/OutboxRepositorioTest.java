package bo.aportaya.plataforma.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que el outbox promete: el evento y el hecho viven o mueren juntos, y el mismo
 * evento entregado dos veces produce **un** efecto.
 */
class OutboxRepositorioTest {

    private static final String ESQUEMA = "identidad";

    private static DSLContext dsl;
    private static TransactionTemplate transaccion;
    private static Outbox outbox;
    private static Consumidos consumidos;

    @BeforeAll
    static void prepararLaBase() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        outbox = new Outbox(ESQUEMA);
        consumidos = new Consumidos(ESQUEMA);
    }

    @Test
    @DisplayName("Si la transaccion revierte, el evento no existio")
    void siLaTransaccionRevierteElEventoNoExistio() {
        UUID agregado = UUID.randomUUID();

        transaccion.execute(estado -> {
            outbox.emitir(dsl, unEvento(agregado));
            estado.setRollbackOnly();
            return null;
        });

        assertThat(eventosDe(agregado)).isZero();
    }

    @Test
    @DisplayName("El evento queda PENDIENTE en la misma transaccion que el hecho")
    void elEventoQuedaPendienteConElHecho() {
        UUID agregado = UUID.randomUUID();

        transaccion.execute(estado -> {
            outbox.emitir(dsl, unEvento(agregado));
            return null;
        });

        assertThat(eventosDe(agregado)).isEqualTo(1);
        assertThat(dsl.fetchValue("SELECT estado FROM identidad.evento_dominio WHERE agregado_id = ?", agregado))
                .isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("El mismo evento entregado dos veces produce UN efecto")
    void elMismoEventoDosVecesProduceUnEfecto() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(estado -> consumidos.registrar(dsl, idEvento, "notificaciones"));
        Boolean segunda = transaccion.execute(estado -> consumidos.registrar(dsl, idEvento, "notificaciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("Dos consumidores distintos procesan el mismo evento, cada uno una vez")
    void dosConsumidoresDistintosProcesanElMismoEvento() {
        UUID idEvento = UUID.randomUUID();

        Boolean paraNotificaciones = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "notificaciones"));
        Boolean paraTransparencia = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "transparencia"));
        Boolean repetida = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "transparencia"));

        assertThat(paraNotificaciones).isTrue();
        assertThat(paraTransparencia).isTrue();
        assertThat(repetida).isFalse();
    }

    private EventoDominio unEvento(UUID agregado) {
        return new EventoDominio(
                "identidad.usuario_registrado",
                "usuario",
                agregado,
                Map.of("usuarioId", agregado.toString()),
                UUID.randomUUID());
    }

    private Integer eventosDe(UUID agregado) {
        return dsl.fetchCount(
                DSL.table("identidad.evento_dominio"), DSL.field("agregado_id").eq(agregado));
    }
}
