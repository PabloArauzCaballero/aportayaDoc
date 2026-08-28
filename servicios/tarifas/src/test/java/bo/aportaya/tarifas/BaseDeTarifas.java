package bo.aportaya.tarifas;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision;
import bo.aportaya.tarifas.aplicacion.CU31DevengarComision;
import bo.aportaya.tarifas.aplicacion.CU32EmitirFactura;
import bo.aportaya.tarifas.aplicacion.CU33DevolverComision;
import bo.aportaya.tarifas.aplicacion.CU34PublicarTarifario;
import bo.aportaya.tarifas.aplicacion.CU35CerrarLiquidacion;
import bo.aportaya.tarifas.aplicacion.CU36ResolverPrecio;
import bo.aportaya.tarifas.infraestructura.CambioTarifarioRepositorio;
import bo.aportaya.tarifas.infraestructura.CotizacionRepositorio;
import bo.aportaya.tarifas.infraestructura.DevengoRepositorio;
import bo.aportaya.tarifas.infraestructura.DevolucionRepositorio;
import bo.aportaya.tarifas.infraestructura.FacturaRepositorio;
import bo.aportaya.tarifas.infraestructura.LiquidacionRepositorio;
import bo.aportaya.tarifas.infraestructura.SegmentoRepositorio;
import bo.aportaya.tarifas.infraestructura.TarifarioRepositorio;
import bo.aportaya.tarifas.infraestructura.simulado.ServicioDeImpuestosSimulado;
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

/** El armado del carril de tarifas, con las piezas construidas a mano. */
abstract class BaseDeTarifas {

    /** El NIT del emisor: es configuracion, no una constante del negocio. */
    protected static final String NIT_EMISOR = "1234567890";

    protected static final int SUCURSAL = 0;
    protected static final int PUNTO_VENTA = 1;
    protected static final int INTENTOS_ANTES_DE_INCOBRABLE = 3;

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeTarifas fixtura;
    protected static FixturaDeFacturacion facturacion;
    protected static FixturaDeGrupo escenario;
    protected static Consumidos consumidos;
    protected static ServicioDeImpuestosSimulado servicioFiscal;

    protected static CU30CotizarComision cotizacionCU;
    protected static CU31DevengarComision devengoCU;
    protected static CU32EmitirFactura facturaCU;
    protected static CU33DevolverComision devolucionCU;
    protected static CU34PublicarTarifario tarifarioCU;
    protected static CU35CerrarLiquidacion liquidacionCU;
    protected static CU36ResolverPrecio precioCU;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeTarifas(dslFixtura);
        facturacion = new FixturaDeFacturacion(dslFixtura);
        escenario = new FixturaDeGrupo(dslFixtura, fixtura);
        consumidos = new Consumidos("tarifas");
        servicioFiscal = new ServicioDeImpuestosSimulado();

        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("tarifas");
        var tarifarios = new TarifarioRepositorio();
        var cotizaciones = new CotizacionRepositorio();
        var devengos = new DevengoRepositorio();
        var devoluciones = new DevolucionRepositorio();

        cotizacionCU = new CU30CotizarComision(
                datos, tarifarios, cotizaciones, outbox, Reloj.delSistema(), Duration.ofMinutes(15));
        devengoCU = new CU31DevengarComision(
                datos,
                devengos,
                cotizaciones,
                tarifarios,
                outbox,
                Reloj.delSistema(),
                INTENTOS_ANTES_DE_INCOBRABLE,
                30);
        facturaCU = new CU32EmitirFactura(
                datos,
                new FacturaRepositorio(),
                devengos,
                servicioFiscal,
                outbox,
                Reloj.delSistema(),
                NIT_EMISOR,
                SUCURSAL,
                PUNTO_VENTA,
                Duration.ofHours(48));
        devolucionCU = new CU33DevolverComision(
                datos,
                devengos,
                devoluciones,
                outbox,
                Reloj.delSistema(),
                // El CUF de la nota se deriva del documento que corrige Y del momento:
                // determinista para un reintento, distinto entre dos notas sobre la
                // misma factura. Solo con la factura, dos devoluciones parciales
                // producirian el mismo codigo y `uq_nota_cuf` rechazaria la segunda.
                (facturaId, momento) -> "NC%s%d"
                        .formatted(
                                Integer.toHexString(facturaId.hashCode()).toUpperCase(java.util.Locale.ROOT),
                                momento.toInstant().toEpochMilli()));
        tarifarioCU = new CU34PublicarTarifario(datos, new CambioTarifarioRepositorio(), outbox, Reloj.delSistema());
        liquidacionCU = new CU35CerrarLiquidacion(datos, new LiquidacionRepositorio(), outbox, Reloj.delSistema());
        precioCU = new CU36ResolverPrecio(datos, new SegmentoRepositorio(), outbox);
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
