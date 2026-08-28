package bo.aportaya.entregas;

import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino;
import bo.aportaya.entregas.aplicacion.CU22LiquidarEntrega;
import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso;
import bo.aportaya.entregas.infraestructura.CuentaDestinoRepositorio;
import bo.aportaya.entregas.infraestructura.DesembolsoRepositorio;
import bo.aportaya.entregas.infraestructura.EntregaRepositorio;
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

/** El armado del carril de entregas, con las piezas construidas a mano. */
abstract class BaseDeEntregas {

    /**
     * La pimienta del hash de busqueda.
     *
     * <p>En produccion sale del almacen de secretos; aca es un valor de prueba fijo
     * para que el hash sea reproducible entre corridas. Lo que importa del invariante
     * —que exista y que no se guarde junto al hash— se verifica igual.
     */
    protected static final String PIMIENTA = "pimienta-de-prueba-no-es-la-de-produccion";

    protected static final int VERSION_LLAVE = 1;
    protected static final int MAXIMO_DE_CUENTAS = 3;
    protected static final Duration ENFRIAMIENTO = Duration.ofHours(24);
    protected static final int INTENTOS_MAXIMOS = 3;

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeEntregas fixtura;
    protected static Consumidos consumidos;

    protected static CU18RegistrarCuentaDestino cuentaCU;
    protected static CU22LiquidarEntrega entregaCU;
    protected static CU28EmitirDesembolso desembolsoCU;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeEntregas(dslFixtura);
        consumidos = new Consumidos("entregas");

        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("entregas");
        var cuentas = new CuentaDestinoRepositorio();
        var entregas = new EntregaRepositorio();

        cuentaCU = new CU18RegistrarCuentaDestino(
                datos, cuentas, outbox, Reloj.delSistema(), PIMIENTA, VERSION_LLAVE, MAXIMO_DE_CUENTAS, ENFRIAMIENTO);
        entregaCU = new CU22LiquidarEntrega(datos, entregas, outbox, Reloj.delSistema());
        desembolsoCU = new CU28EmitirDesembolso(
                datos,
                new DesembolsoRepositorio(),
                entregas,
                cuentas,
                outbox,
                Reloj.delSistema(),
                INTENTOS_MAXIMOS,
                Duration.ofMinutes(15));
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
