package bo.aportaya.cumplimiento;

import bo.aportaya.cumplimiento.aplicacion.CU05AceptarContrato;
import bo.aportaya.cumplimiento.aplicacion.CU46VerificarAlcance;
import bo.aportaya.cumplimiento.infraestructura.AceptacionRepositorio;
import bo.aportaya.cumplimiento.infraestructura.ContratoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.LicenciaRepositorio;
import bo.aportaya.cumplimiento.infraestructura.SandboxRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.util.Set;
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

/** El armado del carril de cumplimiento, con las piezas construidas a mano. */
abstract class BaseDeCumplimiento {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeCumplimiento fixtura;
    protected static Consumidos consumidos;
    protected static CU46VerificarAlcance alcanceCU;
    protected static CU05AceptarContrato contratoCU;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeCumplimiento(dslFixtura);
        consumidos = new Consumidos("cumplimiento");
        alcanceCU = new CU46VerificarAlcance(
                new Datos(dsl),
                new LicenciaRepositorio(),
                new SandboxRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema(),
                Set.of("RETIRO"));
        contratoCU = new CU05AceptarContrato(
                new Datos(dsl),
                new ContratoRepositorio(),
                new AceptacionRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema());
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected int contar(String consulta, Object... parametros) {
        return ((Number) dsl.fetchOne(consulta, parametros).get(0)).intValue();
    }

    /** Devuelve el mensaje con que la BASE rechaza, o vacio si no rechazo. */
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
