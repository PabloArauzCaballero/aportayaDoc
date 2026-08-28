package bo.aportaya.erp;

import bo.aportaya.erp.aplicacion.CU100AbrirCerrarPeriodo;
import bo.aportaya.erp.aplicacion.CU101Presupuestar;
import bo.aportaya.erp.aplicacion.CU102AltaDeTercero;
import bo.aportaya.erp.aplicacion.CU103FacturaDeProveedor;
import bo.aportaya.erp.aplicacion.CU104CobrarCuenta;
import bo.aportaya.erp.aplicacion.CU105DepreciarActivo;
import bo.aportaya.erp.aplicacion.CU106GenerarEstadoFinanciero;
import bo.aportaya.erp.infraestructura.ActivosRepositorio;
import bo.aportaya.erp.infraestructura.CobranzasRepositorio;
import bo.aportaya.erp.infraestructura.ComprasRepositorio;
import bo.aportaya.erp.infraestructura.PeriodoRepositorio;
import bo.aportaya.erp.infraestructura.PresupuestoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
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

/** El armado del carril de erp, con las piezas construidas a mano. */
abstract class BaseDeErp {

    static DSLContext dsl;
    static DSLContext dslFixtura;
    static TransactionTemplate transaccion;
    static FixturaDeErp fixtura;
    protected static Consumidos consumidos;

    static CU100AbrirCerrarPeriodo periodoCU;
    static CU101Presupuestar presupuestoCU;
    static CU102AltaDeTercero terceroCU;
    static CU103FacturaDeProveedor facturaCU;
    static CU104CobrarCuenta cobroCU;
    static CU105DepreciarActivo depreciacionCU;
    static CU106GenerarEstadoFinanciero estadoCU;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeErp(dslFixtura);
        consumidos = new Consumidos("erp");

        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("erp");
        var periodos = new PeriodoRepositorio();
        var compras = new ComprasRepositorio();
        var cobranzas = new CobranzasRepositorio();
        var activos = new ActivosRepositorio();
        var presupuestos = new PresupuestoRepositorio();

        periodoCU = new CU100AbrirCerrarPeriodo(datos, periodos, outbox, Reloj.delSistema());
        presupuestoCU = new CU101Presupuestar(datos, presupuestos, outbox, Reloj.delSistema());
        terceroCU = new CU102AltaDeTercero(datos, compras, outbox);
        facturaCU = new CU103FacturaDeProveedor(datos, compras, periodos, presupuestos, outbox, Reloj.delSistema());
        cobroCU = new CU104CobrarCuenta(datos, cobranzas, outbox, Reloj.delSistema());
        depreciacionCU = new CU105DepreciarActivo(datos, activos, periodos, outbox, Reloj.delSistema());
        estadoCU = new CU106GenerarEstadoFinanciero(datos, presupuestos, periodos, outbox, Reloj.delSistema());
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

    /** Devuelve el mensaje con que la BASE rechaza, o vacio si no rechazo. */
    protected String rechazaLaBase(String sql, Object... parametros) {
        try {
            transaccion.execute(estado -> {
                dsl.execute(sql, parametros);
                estado.setRollbackOnly();
                return null;
            });
            return "";
        } catch (RuntimeException e) {
            return raizDe(e);
        }
    }
}
