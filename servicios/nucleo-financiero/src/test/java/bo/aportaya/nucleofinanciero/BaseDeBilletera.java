package bo.aportaya.nucleofinanciero;

import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU40EvaluarLimites;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.LimiteRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.RetencionRepositorio;
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

/** El armado de los casos de uso de la billetera, con las piezas construidas a mano. */
abstract class BaseDeBilletera {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeNucleoFinanciero fixtura;
    protected static Consumidos consumidos;
    protected static CU40EvaluarLimites limitesCU;
    protected static CU13RetenerSaldo retencionCU;

    @BeforeAll
    static void armarBilletera() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeNucleoFinanciero(dslFixtura);
        consumidos = new Consumidos("nucleo_financiero");

        limitesCU = new CU40EvaluarLimites(
                new Datos(dsl),
                new CuentaBilleteraRepositorio(),
                new LimiteRepositorio(),
                new Outbox("nucleo_financiero"),
                Reloj.delSistema());
        retencionCU = new CU13RetenerSaldo(
                new Datos(dsl),
                new CuentaBilleteraRepositorio(),
                new RetencionRepositorio(),
                new Outbox("nucleo_financiero"),
                Reloj.delSistema());
    }

    protected ContextoSesion contextoDe(UUID usuarioId) {
        return ContextoSesion.de(
                usuarioId, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected int contar(String consulta, Object... parametros) {
        return ((Number) dsl.fetchOne(consulta, parametros).get(0)).intValue();
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
