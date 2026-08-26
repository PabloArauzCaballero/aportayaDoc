package bo.aportaya.nucleofinanciero;

import bo.aportaya.nucleofinanciero.aplicacion.CU24RegistrarAsiento;
import bo.aportaya.nucleofinanciero.infraestructura.AsientoRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaContableRepositorio;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
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

/** El armado de CU-24, con las piezas construidas a mano — igual que {@code BaseDeCU08} en identidad. */
abstract class BaseDeCU24 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU24RegistrarAsiento registrar;
    protected static FixturaDeNucleoFinanciero fixtura;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));

        registrar = new CU24RegistrarAsiento(
                new CuentaContableRepositorio(),
                new AsientoRepositorio(),
                new Outbox("nucleo_financiero"),
                Reloj.delSistema());
        fixtura = new FixturaDeNucleoFinanciero(dsl);
    }

    /** CU-24 · actor Sistema: no hay un usuario detrás del hecho económico. */
    protected ContextoSesion comoSistema() {
        return ContextoSesion.deSistema(
                UUID.randomUUID(), new Traza(UUID.randomUUID().toString()));
    }

    /** Ejercita una restricción saltándose la aplicación: prueba la base, no el `if` que la anticipa. */
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

    /**
     * El mensaje del motor, al fondo de la cadena de causas. Hace falta aparte de
     * {@link #rechazaLaBase} para los triggers {@code DEFERRABLE}, que solo disparan al
     * intentar el COMMIT y por lo tanto nunca saltan en una transacción que se revierte
     * a propósito.
     */
    protected String raizDe(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return String.valueOf(raiz.getMessage());
    }

    /** {@code cuenta_contable.codigo} es VARCHAR(20): un UUID entero no entra. */
    protected String codigoCorto() {
        return "T." + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Un asiento cuadrado de dos patas, ya confirmado. Devuelve su id. */
    protected UUID asientoCuadrado(UUID cuentaDebe, UUID cuentaHaber, String monto, String glosa) {
        UUID asientoId = UUID.randomUUID();
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.asiento_contable
                        (id, fecha, glosa, origen_tipo, origen_id, estado)
                    VALUES (?, now(), ?, 'AJUSTE', gen_random_uuid(), 'CONFIRMADO')
                    """,
                    asientoId,
                    glosa);
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.movimiento_contable
                        (id, asiento_id, cuenta_id, debe, haber, descripcion)
                    VALUES (gen_random_uuid(), ?, ?, ?::numeric, 0.00, 'debe')
                    """,
                    asientoId,
                    cuentaDebe,
                    monto);
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.movimiento_contable
                        (id, asiento_id, cuenta_id, debe, haber, descripcion)
                    VALUES (gen_random_uuid(), ?, ?, 0.00, ?::numeric, 'haber')
                    """,
                    asientoId,
                    cuentaHaber,
                    monto);
            return null;
        });
        return asientoId;
    }
}
