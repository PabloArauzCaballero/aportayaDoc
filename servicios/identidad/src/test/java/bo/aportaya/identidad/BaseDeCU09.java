package bo.aportaya.identidad;

import bo.aportaya.identidad.aplicacion.CU09CambiarCredencial;
import bo.aportaya.identidad.aplicacion.CU09CambiarCredencial.EntradaCambio;
import bo.aportaya.identidad.aplicacion.CU09CambiarCredencial.SalidaCambio;
import bo.aportaya.identidad.aplicacion.CU09SolicitarBaja;
import bo.aportaya.identidad.dominio.PoliticaDeClave;
import bo.aportaya.identidad.infraestructura.Argon2Hasheador;
import bo.aportaya.identidad.infraestructura.CredencialRepositorio;
import bo.aportaya.identidad.infraestructura.UsuarioRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.time.Duration;
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

/** El armado de CU-09, con las piezas construidas a mano. */
abstract class BaseDeCU09 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU09CambiarCredencial cambiarCredencial;
    protected static CU09SolicitarBaja baja;
    protected static FixturaDeIdentidad fixtura;
    protected static Argon2Hasheador hasheador;
    protected static Consumidos consumidos;
    protected static PoliticaDeClave politica;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));

        hasheador = new Argon2Hasheador("pimienta-de-prueba");
        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("identidad");
        cambiarCredencial = new CU09CambiarCredencial(
                datos, new UsuarioRepositorio(), new CredencialRepositorio(), hasheador, outbox, Reloj.delSistema());
        baja = new CU09SolicitarBaja(datos, new CredencialRepositorio(), outbox, Reloj.delSistema());
        fixtura = new FixturaDeIdentidad(dsl);
        consumidos = new Consumidos("identidad");
        politica = new PoliticaDeClave(10, 5);
    }

    protected ContextoSesion comoTitular(UUID usuario) {
        return ContextoSesion.de(
                usuario, "participante", new Traza(UUID.randomUUID().toString()));
    }

    protected UUID participanteConClave(String telefono) {
        UUID usuario = fixtura.usuario(telefono);
        fixtura.credencial(usuario, hasheador.hashear(claveInicial(telefono).toCharArray()));
        return usuario;
    }

    /** La clave inicial de cada usuario de prueba, derivada de su telefono. */
    private String claveInicial(String telefono) {
        return "clave-actual-" + Integer.parseInt(telefono.substring(telefono.length() - 2));
    }

    protected SalidaCambio cambiar(UUID usuario, Optional<UUID> sesion, String actual, String nueva) {
        return transaccion.execute(e -> cambiarCredencial.ejecutar(
                new EntradaCambio(
                        actual.toCharArray(),
                        nueva.toCharArray(),
                        sesion,
                        Optional.empty(),
                        Optional.empty(),
                        false,
                        Duration.ofHours(24)),
                comoTitular(usuario),
                politica));
    }

    protected SalidaCambio restablecerSinAprobacion(UUID usuario, String nueva) {
        return transaccion.execute(e -> cambiarCredencial.ejecutar(
                new EntradaCambio(
                        new char[0],
                        nueva.toCharArray(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true,
                        Duration.ofHours(24)),
                comoTitular(usuario),
                politica));
    }

    protected String hashDe(UUID usuario) {
        return String.valueOf(
                dsl.fetchOne("SELECT hash_contrasena FROM identidad.credencial_acceso WHERE usuario_id = ?", usuario)
                        .get(0));
    }

    protected int historialDe(UUID usuario) {
        return dsl.fetchCount(
                DSL.table("identidad.historial_credencial"),
                DSL.field("usuario_id").eq(usuario));
    }

    protected int sesionesVivasDe(UUID usuario) {
        return dsl.fetchCount(
                DSL.table("identidad.sesion"),
                DSL.field("usuario_id").eq(usuario).and(DSL.field("revocada_en").isNull()));
    }

    protected boolean sesionViva(UUID sesionId) {
        return dsl.fetchCount(
                        DSL.table("identidad.sesion"),
                        DSL.field("id")
                                .eq(sesionId)
                                .and(DSL.field("revocada_en").isNull()))
                == 1;
    }

    protected int dispositivosConfiablesDe(UUID usuario) {
        return dsl.fetchCount(
                DSL.table("identidad.dispositivo"),
                DSL.field("usuario_id")
                        .eq(usuario)
                        .and(DSL.field("es_confiable").isTrue()));
    }

    protected String motivoDeRevocacion(UUID usuario) {
        return String.valueOf(
                dsl.fetchOne("SELECT motivo_revocacion FROM identidad.sesion WHERE usuario_id = ? LIMIT 1", usuario)
                        .get(0));
    }

    protected boolean bloqueadaPorObligaciones(UUID solicitudId) {
        return Boolean.TRUE.equals(dsl.fetchOne(
                        "SELECT bloqueada_por_obligaciones FROM identidad.solicitud_baja WHERE id = ?", solicitudId)
                .get(0));
    }

    protected long movimientosDeBilletera() {
        return dsl.fetchCount(DSL.table("nucleo_financiero.movimiento_billetera"));
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
