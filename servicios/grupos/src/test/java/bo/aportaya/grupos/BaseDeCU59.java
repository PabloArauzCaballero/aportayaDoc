package bo.aportaya.grupos;

import bo.aportaya.grupos.aplicacion.CU59CalcularPlazo;
import bo.aportaya.grupos.aplicacion.CU59CalcularPlazo.EntradaPlazo;
import bo.aportaya.grupos.aplicacion.CU59CalcularPlazo.SalidaPlazo;
import bo.aportaya.grupos.infraestructura.CalendarioRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.time.LocalDate;
import java.util.Optional;
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

/** El armado de CU-59, con las piezas construidas a mano. */
abstract class BaseDeCU59 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU59CalcularPlazo calcularPlazo;
    protected static Consumidos consumidos;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        calcularPlazo = new CU59CalcularPlazo(new Datos(dsl), new CalendarioRepositorio());
        consumidos = new Consumidos("grupos");
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                UUID.randomUUID(), "OPERACIONES", new Traza(UUID.randomUUID().toString()));
    }

    protected SalidaPlazo calcular(LocalDate desde, int dias) {
        return transaccion.execute(
                e -> calcularPlazo.ejecutar(new EntradaPlazo(desde, dias, "NACIONAL", Optional.empty()), contexto()));
    }

    /** Un dia no habil sembrado, con su descripcion — que es donde va la fuente. */
    protected void feriado(LocalDate fecha, String alcance, String descripcion) {
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO catalogo.dia_no_habil (id, fecha, descripcion, alcance)
                    VALUES (gen_random_uuid(), ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """,
                    fecha,
                    descripcion,
                    alcance);
            return null;
        });
    }

    protected void dejarUnaFilaEnLaBitacora() {
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO comun.bitacora_evento
                        (id, secuencia, entidad, entidad_id, accion, origen, correlation_id,
                         hash_registro, hash_anterior, fecha_hora)
                    VALUES (gen_random_uuid(),
                            nextval(pg_get_serial_sequence('comun.bitacora_evento','secuencia')),
                            'prueba_calendario', gen_random_uuid(), 'CREACION', 'TAREA_PROGRAMADA',
                            gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                    """);
            return null;
        });
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
