package bo.aportaya.nucleofinanciero;

import bo.aportaya.nucleofinanciero.aplicacion.CU10RecargarSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU11RetirarSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU12TransferirSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU14ReversarTransaccion;
import bo.aportaya.nucleofinanciero.aplicacion.CU40EvaluarLimites;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera;
import bo.aportaya.nucleofinanciero.infraestructura.LimiteRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.OrdenRecargaRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.OrdenRetiroRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.RetencionRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.ReversoRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.TransferenciaRepositorio;
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
    protected static FixturaDeBilletera fixtura;

    /** La contable: el retiro escribe asientos, y esos necesitan cuentas. */
    protected static FixturaDeNucleoFinanciero contable;

    /** La cadena de grupo que una obligacion de aporte necesita para existir. */
    protected static FixturaDeObligacion obligaciones;

    /** La cuenta de destino y la custodia: lo que la billetera toca de afuera. */
    protected static FixturaDeCustodia custodia;

    protected static Consumidos consumidos;
    protected static CU40EvaluarLimites limitesCU;
    protected static CU13RetenerSaldo retencionCU;
    protected static CU10RecargarSaldo recargaCU;
    protected static CU11RetirarSaldo retiroCU;
    protected static CU12TransferirSaldo transferenciaCU;
    protected static CU14ReversarTransaccion reversoCU;

    /** La cuenta puente: el otro lado de todo ingreso. Una sola para toda la corrida. */
    protected static UUID puente;

    @BeforeAll
    static void armarBilletera() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeBilletera(dslFixtura);
        contable = new FixturaDeNucleoFinanciero(dslFixtura);
        obligaciones = new FixturaDeObligacion(dslFixtura);
        custodia = new FixturaDeCustodia(dslFixtura);
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
        puente = fixtura.puenteDeCustodia();
        recargaCU = new CU10RecargarSaldo(
                new Datos(dsl),
                new CuentaBilleteraRepositorio(),
                new OrdenRecargaRepositorio(),
                new LibroDeBilletera(),
                limitesCU,
                new Outbox("nucleo_financiero"),
                Reloj.delSistema(),
                java.time.Duration.ofMinutes(30),
                puente);
        retiroCU = new CU11RetirarSaldo(
                new Datos(dsl),
                new CuentaBilleteraRepositorio(),
                new OrdenRetiroRepositorio(),
                retencionCU,
                limitesCU,
                new LibroDeBilletera(),
                new Outbox("nucleo_financiero"),
                Reloj.delSistema(),
                puente);
        transferenciaCU = new CU12TransferirSaldo(
                new Datos(dsl),
                new CuentaBilleteraRepositorio(),
                new TransferenciaRepositorio(),
                new LibroDeBilletera(),
                limitesCU,
                new Outbox("nucleo_financiero"),
                Reloj.delSistema());
        reversoCU = new CU14ReversarTransaccion(
                new Datos(dsl),
                new ReversoRepositorio(),
                new CuentaBilleteraRepositorio(),
                new LibroDeBilletera(),
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

    /**
     * Igual que {@link #rechazaLaBase}, pero para restricciones DIFERIDAS.
     *
     * <p>Un {@code CONSTRAINT TRIGGER ... INITIALLY DEFERRED} solo dispara al COMMIT,
     * y estas pruebas revierten a proposito para no ensuciar el contenedor. Sin
     * adelantarlo, la prueba veria que el INSERT «paso» y daria por buena una fila
     * que en produccion nunca se habria confirmado. {@code SET CONSTRAINTS ALL
     * IMMEDIATE} lo hace disparar donde se lo puede observar.
     */
    /** El mensaje del fondo de la cadena: lo de arriba suele ser «JDBC commit failed». */
    protected String raizDe(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return String.valueOf(raiz.getMessage());
    }

    protected String rechazaLaBaseAlCerrar(String sql) {
        try {
            transaccion.execute(estado -> {
                dsl.execute("SET CONSTRAINTS ALL IMMEDIATE");
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
