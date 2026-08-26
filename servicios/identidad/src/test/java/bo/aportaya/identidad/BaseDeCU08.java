package bo.aportaya.identidad;

import bo.aportaya.identidad.aplicacion.CU08AsignarRol;
import bo.aportaya.identidad.aplicacion.CU08RevocarRol;
import bo.aportaya.identidad.infraestructura.AccesoRepositorio;
import bo.aportaya.identidad.infraestructura.AccesosRepositorio;
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

/** El armado de CU-08, con las piezas construidas a mano. */
abstract class BaseDeCU08 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU08AsignarRol asignar;
    protected static CU08RevocarRol revocar;
    protected static FixturaDeIdentidad fixtura;
    protected static AccesosRepositorio accesos;
    protected static Consumidos consumidos;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));

        Datos datos = new Datos(dsl);
        accesos = new AccesosRepositorio();
        Outbox outbox = new Outbox("identidad");
        asignar = new CU08AsignarRol(datos, accesos, new AccesoRepositorio(), outbox, Reloj.delSistema());
        revocar = new CU08RevocarRol(datos, accesos, outbox, Reloj.delSistema());
        fixtura = new FixturaDeIdentidad(dsl);
        consumidos = new Consumidos("identidad");
    }

    protected ContextoSesion comoAdministrador(UUID administrador) {
        return ContextoSesion.de(
                administrador, "BACKOFFICE", new Traza(UUID.randomUUID().toString()));
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

    protected int sesionesVivasDe(UUID usuario) {
        return dsl.fetchCount(
                DSL.table("identidad.sesion"),
                DSL.field("usuario_id").eq(usuario).and(DSL.field("revocada_en").isNull()));
    }

    protected int asignacionesDe(UUID usuario) {
        return dsl.fetchCount(
                DSL.table("identidad.asignacion_rol"), DSL.field("usuario_id").eq(usuario));
    }

    /**
     * Un DELETE sobre una tabla vacia no dispara un trigger FOR EACH ROW: la prueba
     * tiene que dejar la fila que va a intentar borrar, o depende del orden en que
     * corran las otras — y una prueba que depende del orden no prueba nada.
     */
    protected void dejarUnaFilaEnLaBitacora() {
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO comun.bitacora_evento
                        (id, secuencia, entidad, entidad_id, accion, origen, correlation_id,
                         hash_registro, hash_anterior, fecha_hora)
                    VALUES (gen_random_uuid(),
                            nextval(pg_get_serial_sequence('comun.bitacora_evento','secuencia')),
                            'prueba_append_only', gen_random_uuid(), 'CREACION', 'TAREA_PROGRAMADA',
                            gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                    """);
            return null;
        });
    }
}
