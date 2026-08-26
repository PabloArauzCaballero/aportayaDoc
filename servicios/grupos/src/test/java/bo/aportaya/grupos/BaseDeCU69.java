package bo.aportaya.grupos;

import bo.aportaya.grupos.aplicacion.CU69Invitar;
import bo.aportaya.grupos.aplicacion.CU69Invitar.EntradaInvitacion;
import bo.aportaya.grupos.aplicacion.CU69Invitar.Resultado;
import bo.aportaya.grupos.infraestructura.InvitacionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
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

/** El armado de CU-69, con las piezas construidas a mano. */
abstract class BaseDeCU69 {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static CU69Invitar invitar;
    protected static FixturaDeGrupos fixtura;
    protected static Consumidos consumidos;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        invitar = new CU69Invitar(
                new Datos(dsl), new InvitacionRepositorio(), new Outbox("grupos"), Reloj.delSistema(), Ids.seguros());
        fixtura = new FixturaDeGrupos(dslFixtura);
        consumidos = new Consumidos("grupos");
    }

    protected ContextoSesion contexto(UUID usuario) {
        return ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    /** Un grupo con tres cupos, uno de ellos libre. */
    protected UUID grupoConCupoLibre() {
        UUID grupo = fixtura.grupoConformado(3);
        fixtura.participantesConCupo(grupo, 3);
        dslFixtura.execute(
                """
                UPDATE grupos.cupo SET estado = 'LIBRE', participante_id = NULL
                 WHERE id = (SELECT id FROM grupos.cupo WHERE grupo_id = ? ORDER BY numero DESC LIMIT 1)
                """,
                grupo);
        return grupo;
    }

    /** El usuario de un participante activo del grupo: quien puede invitar. */
    protected UUID participanteActivo(UUID grupo) {
        return (UUID) dslFixtura
                .fetchOne(
                        "SELECT usuario_id FROM grupos.participante WHERE grupo_id = ? AND estado = 'ACTIVO' LIMIT 1",
                        grupo)
                .get(0);
    }

    protected Resultado invitar(UUID grupo, UUID emisor, String telefono, boolean suprimido, boolean yaEsParticipante) {
        return transaccion.execute(e -> invitar.invitar(
                new EntradaInvitacion(
                        grupo,
                        telefono,
                        "Contacto",
                        "ENLACE",
                        suprimido,
                        yaEsParticipante,
                        3,
                        fixtura.tokenDeInvitacion()),
                contexto(emisor)));
    }

    protected String estadoDe(UUID invitacionId) {
        return String.valueOf(dsl.fetchOne("SELECT estado FROM grupos.invitacion WHERE id = ?", invitacionId)
                .get(0));
    }

    protected int invitacionesDe(UUID grupo) {
        return dsl.fetchCount(
                DSL.table("grupos.invitacion"), DSL.field("grupo_id").eq(grupo));
    }

    protected int cuposLibresDe(UUID grupo) {
        return dsl.fetchCount(
                DSL.table("grupos.cupo"),
                DSL.field("grupo_id").eq(grupo).and(DSL.field("estado").eq("LIBRE")));
    }

    protected int contar(String consulta) {
        return ((Number) dsl.fetchOne(consulta).get(0)).intValue();
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
