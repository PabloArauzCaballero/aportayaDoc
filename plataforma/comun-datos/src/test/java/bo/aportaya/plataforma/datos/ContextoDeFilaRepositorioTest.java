package bo.aportaya.plataforma.datos;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
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
 * El invariante 3, contra PostgreSQL real: el contexto de fila se fija con
 * {@code SET LOCAL}, muere en el {@code COMMIT}, y la politica de fila deja ver la
 * fila propia y **cero filas** —no un error— cuando se pide la ajena.
 *
 * <p>Que devuelva cero filas y no permiso denegado es el punto: un error revela que
 * la fila existe. El silencio no revela nada.
 */
class ContextoDeFilaRepositorioTest {

    private static final UUID MIO = UUID.fromString("00000000-0000-4000-8000-00000000a001");
    private static final UUID AJENO = UUID.fromString("00000000-0000-4000-8000-00000000a002");

    private static DataSource fuente;
    private static DSLContext dsl;
    private static TransactionTemplate transaccion;
    private static Datos datos;

    @BeforeAll
    static void prepararLaBase() {
        var contenedor = BaseDePrueba.contenedor();
        fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        datos = new Datos(dsl);

        // El dueno de la base es superusuario y por eso NO le aplica RLS: es lo que
        // permite sembrar. La prueba de verdad corre despues, con SET ROLE.
        // Las columnas salen del modelo, no de la memoria: `usuario` no tiene
        // tipo_persona ni telefono, y estado y nivel_kyc tienen CHECK cerrado.
        DSL.using(fuente, SQLDialect.POSTGRES)
                .execute(
                        """
                        INSERT INTO identidad.usuario
                            (id, codigo_publico, nombres, apellidos, telefono_e164,
                             fecha_nacimiento, estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                        VALUES (?, 'AY-RLS-001', 'Ana', 'Quispe', '+59170000001',
                                DATE '1990-01-01', 'ACTIVO', 'BASICO', 'es', 'America/La_Paz', now()),
                               (?, 'AY-RLS-002', 'Luis', 'Mamani', '+59170000002',
                                DATE '1988-05-20', 'ACTIVO', 'BASICO', 'es', 'America/La_Paz', now())
                        ON CONFLICT (id) DO NOTHING
                        """,
                        MIO,
                        AJENO);
    }

    @Test
    @DisplayName("Con contexto propio se ve la fila propia; la ajena devuelve cero filas, no error")
    void laFilaAjenaDevuelveCeroFilas() {
        Integer propias = transaccion.execute(estado -> {
            dsl.execute("SET LOCAL ROLE svc_identidad");
            return datos.conContexto(contextoDe(MIO), d -> d.fetchCount(DSL.table("identidad.usuario")));
        });

        Integer ajenas = transaccion.execute(estado -> {
            dsl.execute("SET LOCAL ROLE svc_identidad");
            return datos.conContexto(
                    contextoDe(AJENO),
                    d -> d.fetchCount(
                            DSL.table("identidad.usuario"), DSL.field("id").eq(MIO)));
        });

        assertThat(propias).isEqualTo(1);
        assertThat(ajenas).isZero();
    }

    @Test
    @DisplayName("El rol privilegiado ve todo: es una politica, no una excepcion a las politicas")
    void elRolPrivilegiadoVeTodo() {
        Integer todas = transaccion.execute(estado -> {
            dsl.execute("SET LOCAL ROLE svc_identidad");
            return datos.conContexto(
                    new ContextoSesion(MIO, "CUMPLIMIENTO", new Traza("t-privilegiada"), null),
                    d -> d.fetchCount(DSL.table("identidad.usuario")));
        });

        assertThat(todas).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("SET LOCAL muere en el COMMIT: la siguiente transaccion no hereda la identidad")
    void elContextoMuereEnElCommit() {
        String dentro = transaccion.execute(estado ->
                datos.conContexto(contextoDe(MIO), d -> d.fetchValue("SELECT current_setting('app.usuario_id', true)")
                        .toString()));

        String despues = transaccion.execute(
                estado -> String.valueOf(dsl.fetchValue("SELECT current_setting('app.usuario_id', true)")));

        assertThat(dentro).isEqualTo(MIO.toString());
        assertThat(despues).isIn("", "null");
    }

    private ContextoSesion contextoDe(UUID usuario) {
        return ContextoSesion.de(usuario, "participante", new Traza("t-" + usuario));
    }
}
