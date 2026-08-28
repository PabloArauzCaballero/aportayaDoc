package bo.aportaya.publicidad;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import bo.aportaya.publicidad.aplicacion.CU110AltaDeAnunciante;
import bo.aportaya.publicidad.aplicacion.CU111CrearCampana;
import bo.aportaya.publicidad.aplicacion.CU112ModerarPieza;
import bo.aportaya.publicidad.aplicacion.CU113EntregarAnuncio;
import bo.aportaya.publicidad.aplicacion.CU114LiquidarPublicidad;
import bo.aportaya.publicidad.infraestructura.AnuncianteRepositorio;
import bo.aportaya.publicidad.infraestructura.CampanaRepositorio;
import bo.aportaya.publicidad.infraestructura.CreativaRepositorio;
import bo.aportaya.publicidad.infraestructura.EntregaRepositorio;
import bo.aportaya.publicidad.infraestructura.FacturacionRepositorio;
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

/** El armado del carril de publicidad, con las piezas construidas a mano. */
abstract class BaseDePublicidad {

    static DSLContext dsl;
    static DSLContext dslFixtura;
    static TransactionTemplate transaccion;
    static FixturaDePublicidad fixtura;
    protected static Consumidos consumidos;

    static CU110AltaDeAnunciante anuncianteCU;
    static CU111CrearCampana campanaCU;
    static CU112ModerarPieza moderacionCU;
    static CU113EntregarAnuncio entregaCU;
    static CU114LiquidarPublicidad liquidacionCU;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDePublicidad(dslFixtura);
        consumidos = new Consumidos("publicidad");

        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("publicidad");
        var anunciantes = new AnuncianteRepositorio();
        var campanas = new CampanaRepositorio();
        var creativas = new CreativaRepositorio();
        var entregas = new EntregaRepositorio();
        var facturacion = new FacturacionRepositorio();

        anuncianteCU = new CU110AltaDeAnunciante(datos, anunciantes, outbox, Reloj.delSistema());
        campanaCU = new CU111CrearCampana(datos, campanas, anunciantes, outbox);
        moderacionCU = new CU112ModerarPieza(datos, creativas, outbox, Reloj.delSistema());
        entregaCU = new CU113EntregarAnuncio(datos, entregas, campanas, outbox, Reloj.delSistema());
        liquidacionCU = new CU114LiquidarPublicidad(datos, facturacion, anunciantes, outbox, Reloj.delSistema());
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
