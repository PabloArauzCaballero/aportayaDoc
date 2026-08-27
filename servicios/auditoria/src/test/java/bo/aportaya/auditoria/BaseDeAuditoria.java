package bo.aportaya.auditoria;

import bo.aportaya.auditoria.aplicacion.CU98PublicarTablero;
import bo.aportaya.auditoria.infraestructura.IndicadorRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
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

/** El armado del carril de auditoria, con las piezas construidas a mano. */
abstract class BaseDeAuditoria {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeAuditoria fixtura;
    protected static CU98PublicarTablero tableroCU;

    /** Un operador con permiso de ver indicadores. El rol decide que filas ve. */
    protected static final UUID OPERADOR = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        // El proxy consciente de la transaccion no es opcional: sin el, jOOQ pide una
        // conexion nueva al pool y la consulta corre FUERA de la transaccion que acaba
        // de hacer `SET LOCAL`, con lo cual el contexto de RLS no aplica.
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeAuditoria(dslFixtura);
        tableroCU = new CU98PublicarTablero(new Datos(dsl), new IndicadorRepositorio());
    }

    protected static ContextoSesion contexto() {
        return ContextoSesion.de(
                OPERADOR, "auditor", new Traza(UUID.randomUUID().toString()));
    }

    protected static CU98PublicarTablero.SalidaTablero tablero(String periodo) {
        return transaccion.execute(estado -> tableroCU.ejecutar(
                new CU98PublicarTablero.EntradaTablero(periodo, "GLOBAL", java.util.Optional.empty(), 6), contexto()));
    }
}
