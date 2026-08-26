package bo.aportaya.identidad;

import bo.aportaya.identidad.aplicacion.CU04Autenticar;
import bo.aportaya.identidad.infraestructura.AccesoRepositorio;
import bo.aportaya.identidad.infraestructura.Argon2Hasheador;
import bo.aportaya.identidad.infraestructura.DesafioLocal;
import bo.aportaya.identidad.infraestructura.UsuarioRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.Reloj;
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

/**
 * El armado de CU-04 sin levantar Spring: las piezas se construyen a mano, que es lo
 * que permite que la suite corra en segundos y que la prueba diga exactamente que
 * colabora con que.
 */
abstract class BaseDeCU04 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU04Autenticar cu04;
    protected static FixturaDeIdentidad fixtura;
    protected static Consumidos consumidos;
    protected static String hashDeLaClave;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));

        Argon2Hasheador hasheador = new Argon2Hasheador("pimienta-de-prueba");
        hashDeLaClave = hasheador.hashear("clave-correcta".toCharArray());

        cu04 = new CU04Autenticar(
                new Datos(dsl),
                new UsuarioRepositorio(),
                new AccesoRepositorio(),
                hasheador,
                new DesafioLocal(),
                new Outbox("identidad"),
                Reloj.delSistema());
        fixtura = new FixturaDeIdentidad(dsl);
        consumidos = new Consumidos("identidad");
    }

    protected UUID participanteConCredencial(String telefono) {
        UUID usuario = fixtura.usuario(telefono);
        fixtura.credencial(usuario, hashDeLaClave);
        return usuario;
    }

    /** Devuelve el mensaje del rechazo, o cadena vacia si la base lo dejo pasar. */
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

    protected boolean dispositivoConfiable(UUID usuario, String huella) {
        return Boolean.TRUE.equals(dsl.fetchValue(
                "SELECT es_confiable FROM identidad.dispositivo WHERE usuario_id = ? AND huella = ?", usuario, huella));
    }

    protected int dispositivosDe(UUID usuario, String huella) {
        return dsl.fetchCount(
                DSL.table("identidad.dispositivo"),
                DSL.field("usuario_id").eq(usuario).and(DSL.field("huella").eq(huella)));
    }

    protected boolean tieneBloqueoVigente(UUID usuario) {
        return dsl.fetchCount(
                        DSL.table("identidad.bloqueo_cuenta"),
                        DSL.field("usuario_id").eq(usuario))
                > 0;
    }

    protected int sesionesDe(UUID usuario) {
        return dsl.fetchCount(
                DSL.table("identidad.sesion"), DSL.field("usuario_id").eq(usuario));
    }

    protected int intentosDe(UUID usuario) {
        return dsl.fetchCount(
                DSL.table("identidad.intento_autenticacion"),
                DSL.field("usuario_id").eq(usuario));
    }

    protected int factoresDe(UUID usuario) {
        return dsl.fetchCount(
                DSL.table("identidad.factor_mfa"), DSL.field("usuario_id").eq(usuario));
    }

    protected long movimientosDeBilletera() {
        return dsl.fetchCount(DSL.table("nucleo_financiero.movimiento_billetera"));
    }

    protected boolean consumir(UUID idEvento, String consumidor) {
        return Boolean.TRUE.equals(transaccion.execute(e -> consumidos.registrar(dsl, idEvento, consumidor)));
    }
}
