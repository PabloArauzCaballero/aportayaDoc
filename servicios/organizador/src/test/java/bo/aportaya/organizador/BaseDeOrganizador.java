package bo.aportaya.organizador;

import bo.aportaya.organizador.aplicacion.CU90PostularOrganizador;
import bo.aportaya.organizador.aplicacion.CU91FirmarContrato;
import bo.aportaya.organizador.aplicacion.CU92EvaluarDesempeno;
import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador;
import bo.aportaya.organizador.aplicacion.CU95DefinirAutomatizacion;
import bo.aportaya.organizador.aplicacion.CU96EjecutarTarea;
import bo.aportaya.organizador.infraestructura.AutomatizacionRepositorio;
import bo.aportaya.organizador.infraestructura.ContratoRepositorio;
import bo.aportaya.organizador.infraestructura.DesempenoRepositorio;
import bo.aportaya.organizador.infraestructura.OrganizadorRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.math.BigDecimal;
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

/** El armado del carril de organizador, con las piezas construidas a mano. */
abstract class BaseDeOrganizador {

    /** Los limites del nivel inicial: son configuracion, no constantes del negocio. */
    protected static final int GRUPOS_APRENDIZ = 2;

    protected static final BigDecimal MONTO_APRENDIZ = new BigDecimal("30000.00");
    protected static final BigDecimal UMBRAL_ASCENSO = new BigDecimal("85.00");
    protected static final BigDecimal UMBRAL_DESCENSO = new BigDecimal("50.00");
    protected static final int INTENTOS_MAXIMOS_DE_TAREA = 3;
    protected static final Duration PLAZO_PARA_APELAR = Duration.ofDays(10);

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeOrganizador fixtura;
    protected static Consumidos consumidos;

    protected static CU90PostularOrganizador postulacionCU;
    protected static CU91FirmarContrato contratoCU;
    protected static CU92EvaluarDesempeno desempenoCU;
    protected static CU93SancionarOrganizador sancionCU;
    protected static CU95DefinirAutomatizacion reglaCU;
    protected static CU96EjecutarTarea tareaCU;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeOrganizador(dslFixtura);
        consumidos = new Consumidos("organizador");

        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("organizador");
        var organizadores = new OrganizadorRepositorio();
        var desempenos = new DesempenoRepositorio();
        var automatizaciones = new AutomatizacionRepositorio();

        postulacionCU = new CU90PostularOrganizador(
                datos, organizadores, outbox, Reloj.delSistema(), GRUPOS_APRENDIZ, MONTO_APRENDIZ);
        contratoCU =
                new CU91FirmarContrato(datos, new ContratoRepositorio(), organizadores, outbox, Reloj.delSistema());
        desempenoCU = new CU92EvaluarDesempeno(
                datos, desempenos, organizadores, outbox, Reloj.delSistema(), UMBRAL_ASCENSO, UMBRAL_DESCENSO);
        sancionCU = new CU93SancionarOrganizador(
                datos, desempenos, organizadores, outbox, Reloj.delSistema(), PLAZO_PARA_APELAR);
        reglaCU = new CU95DefinirAutomatizacion(datos, automatizaciones, outbox);
        tareaCU = new CU96EjecutarTarea(datos, automatizaciones, outbox, Reloj.delSistema(), INTENTOS_MAXIMOS_DE_TAREA);
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
