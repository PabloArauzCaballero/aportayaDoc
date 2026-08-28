package bo.aportaya.garantia;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento;
import bo.aportaya.garantia.aplicacion.CU26EjecutarAval;
import bo.aportaya.garantia.aplicacion.CU27RestringirDeudor;
import bo.aportaya.garantia.aplicacion.CU29DevolverFondo;
import bo.aportaya.garantia.aplicacion.CU66ReemplazarParticipante;
import bo.aportaya.garantia.aplicacion.CU67DisolverGrupo;
import bo.aportaya.garantia.infraestructura.ExpedienteRepositorio;
import bo.aportaya.garantia.infraestructura.FondoRepositorio;
import bo.aportaya.garantia.infraestructura.GestionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.time.Duration;
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

/** El armado del carril de garantia, con las piezas construidas a mano. */
abstract class BaseDeGarantia {

    /** Plazos y politicas: son configuracion, no constantes del negocio. */
    protected static final Duration PLAZO_DE_DESCARGO = Duration.ofDays(5);

    protected static final Duration PLAZO_DE_RESPUESTA_DEL_AVAL = Duration.ofDays(10);
    protected static final int DIAS_PARA_EXIGIR_LA_DEUDA = 30;
    protected static final int ANIOS_DE_PRESCRIPCION = 5;

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeGarantia fixtura;
    protected static Consumidos consumidos;

    protected static CU23CubrirIncumplimiento coberturaCU;
    protected static CU25DeclararIncumplimiento expedienteCU;
    protected static CU26EjecutarAval avalCU;
    protected static CU27RestringirDeudor restriccionCU;
    protected static CU29DevolverFondo devolucionCU;
    protected static CU66ReemplazarParticipante reemplazoCU;
    protected static CU67DisolverGrupo disolucionCU;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeGarantia(dslFixtura);
        consumidos = new Consumidos("garantia");

        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("garantia");
        var expedientes = new ExpedienteRepositorio();
        var fondos = new FondoRepositorio();
        var gestion = new GestionRepositorio();

        expedienteCU =
                new CU25DeclararIncumplimiento(datos, expedientes, outbox, Reloj.delSistema(), PLAZO_DE_DESCARGO);
        coberturaCU = new CU23CubrirIncumplimiento(
                datos,
                fondos,
                expedientes,
                outbox,
                Reloj.delSistema(),
                DIAS_PARA_EXIGIR_LA_DEUDA,
                ANIOS_DE_PRESCRIPCION);
        avalCU = new CU26EjecutarAval(
                datos, gestion, fondos, expedientes, outbox, Reloj.delSistema(), PLAZO_DE_RESPUESTA_DEL_AVAL);
        restriccionCU = new CU27RestringirDeudor(datos, gestion, fondos, expedientes, outbox, Reloj.delSistema());
        devolucionCU = new CU29DevolverFondo(datos, fondos, outbox, Reloj.delSistema());
        reemplazoCU = new CU66ReemplazarParticipante(datos, gestion, fondos, expedientes, outbox, Reloj.delSistema());
        disolucionCU = new CU67DisolverGrupo(datos, gestion, outbox, Reloj.delSistema());
    }

    protected ContextoSesion contextoDe(UUID usuarioId) {
        return ContextoSesion.de(
                usuarioId, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected int contar(String consulta, Object... parametros) {
        return ((Number) dsl.fetchOne(consulta, parametros).get(0)).intValue();
    }

    protected String raizDe(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return String.valueOf(raiz.getMessage());
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
            return raizDe(e);
        }
    }
}
