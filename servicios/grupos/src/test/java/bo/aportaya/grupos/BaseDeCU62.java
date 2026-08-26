package bo.aportaya.grupos;

import bo.aportaya.grupos.aplicacion.CU62Permutar;
import bo.aportaya.grupos.aplicacion.CU62Permutar.EntradaPermuta;
import bo.aportaya.grupos.infraestructura.PermutaRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

/** El armado de CU-62, con las piezas construidas a mano. */
abstract class BaseDeCU62 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU62Permutar permutar;
    protected static FixturaDeGrupos fixtura;
    protected static Consumidos consumidos;
    protected static UUID actor;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        DSLContext dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        permutar = new CU62Permutar(new Datos(dsl), new PermutaRepositorio(), new Outbox("grupos"), Reloj.delSistema());
        fixtura = new FixturaDeGrupos(dslFixtura);
        consumidos = new Consumidos("grupos");
        actor = fixtura.usuario();
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                actor, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected UUID solicitar(
            UUID origen,
            UUID destino,
            List<UUID> participantes,
            boolean solicitanteAlDia,
            boolean contraparteAlDia,
            boolean reglamentoLoPermite) {
        return transaccion.execute(e -> permutar.solicitar(
                new EntradaPermuta(
                        origen,
                        destino,
                        participantes.get(0),
                        participantes.get(1),
                        "prefiero mi turno mas tarde",
                        solicitanteAlDia,
                        contraparteAlDia,
                        reglamentoLoPermite),
                contexto()));
    }

    protected List<UUID> cuposDe(UUID grupoId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table("grupos.cupo"))
                .where(DSL.field("grupo_id").eq(grupoId))
                .orderBy(DSL.field("numero"))
                .fetch(DSL.field("id", UUID.class));
    }

    protected UUID cupoDe(UUID turnoId) {
        return (UUID) dsl.fetchOne("SELECT cupo_id FROM grupos.turno WHERE id = ?", turnoId)
                .get(0);
    }

    protected short ordenDe(UUID turnoId) {
        return (Short) dsl.fetchOne("SELECT orden_asignado FROM grupos.turno WHERE id = ?", turnoId)
                .get(0);
    }

    protected UUID permutadoConDe(UUID turnoId) {
        return (UUID) dsl.fetchOne("SELECT permutado_con_turno_id FROM grupos.turno WHERE id = ?", turnoId)
                .get(0);
    }

    protected void cobrar(UUID turnoId) {
        transaccion.execute(e -> {
            dsl.execute("UPDATE grupos.turno SET estado = 'COBRADO' WHERE id = ?", turnoId);
            return null;
        });
    }

    protected int turnosDe(UUID grupoId) {
        return dsl.fetchCount(DSL.table("grupos.turno"), DSL.field("grupo_id").eq(grupoId));
    }

    protected int periodosSinTurno(UUID grupoId) {
        return ((Number) dsl.fetchOne(
                                """
                                SELECT count(*)::int FROM grupos.periodo p
                                 WHERE p.grupo_id = ?
                                   AND NOT EXISTS (SELECT 1 FROM grupos.turno t WHERE t.periodo_id = p.id)
                                """,
                                grupoId)
                        .get(0))
                .intValue();
    }

    protected String rechazaLaBase(String sql) {
        try {
            transaccion.execute(estado -> {
                dsl.execute(sql);
                estado.setRollbackOnly();
                return null;
            });
            return "";
        } catch (RuntimeException e) {
            Throwable raiz = e;
            while (raiz.getCause() != null && raiz.getCause() != raiz) {
                raiz = raiz.getCause();
            }
            return String.valueOf(raiz.getMessage());
        }
    }
}
