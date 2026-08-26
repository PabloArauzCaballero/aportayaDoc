package bo.aportaya.identidad;

import bo.aportaya.identidad.aplicacion.CU01RegistrarUsuario;
import bo.aportaya.identidad.aplicacion.CU01RegistrarUsuario.EntradaRegistro;
import bo.aportaya.identidad.aplicacion.CU01RegistrarUsuario.SalidaRegistro;
import bo.aportaya.identidad.dominio.DocumentoDeIdentidad;
import bo.aportaya.identidad.infraestructura.RegistroRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

/** El armado de CU-01, con las piezas construidas a mano. */
abstract class BaseDeCU01 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU01RegistrarUsuario registrarUsuario;
    protected static DoblesDeLaCoreografia dobles;
    protected static Consumidos consumidos;
    protected static long eventosAlEmpezar;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        registrarUsuario = new CU01RegistrarUsuario(
                new Datos(dsl), new RegistroRepositorio(), new Outbox("identidad"), Reloj.delSistema(), Ids.seguros());
        dobles = new DoblesDeLaCoreografia(DSL.using(fuente, SQLDialect.POSTGRES));
        consumidos = new Consumidos("identidad");
        eventosAlEmpezar = contarEventos("identidad.usuario_registrado");
    }

    /** El alta corre SIN sesion previa: es el unico momento en que todavia no hay usuario. */
    protected ContextoSesion contexto() {
        return ContextoSesion.deSistema(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                new Traza(UUID.randomUUID().toString()));
    }

    protected EntradaRegistro entrada(String telefono, String documento, boolean conContrato, boolean conLicencia) {
        return new EntradaRegistro(
                telefono,
                "Ana",
                "Quispe",
                LocalDate.of(1990, 1, 1),
                DocumentoDeIdentidad.de(DocumentoDeIdentidad.Tipo.CI, documento, "pimienta-de-prueba", "BO"),
                "cifrado:" + documento,
                "0".repeat(64),
                conContrato ? List.of(UUID.randomUUID()) : List.of(),
                conLicencia,
                "127.0.0.1",
                "prueba");
    }

    protected SalidaRegistro registrar(String telefono, String documento) {
        return transaccion.execute(
                e -> registrarUsuario.ejecutar(entrada(telefono, documento, true, true), contexto()));
    }

    protected SalidaRegistro registrarSinContrato(String telefono, String documento) {
        return transaccion.execute(
                e -> registrarUsuario.ejecutar(entrada(telefono, documento, false, true), contexto()));
    }

    protected SalidaRegistro registrarSinLicencia(String telefono, String documento) {
        return transaccion.execute(
                e -> registrarUsuario.ejecutar(entrada(telefono, documento, true, false), contexto()));
    }

    protected int eventosDe(UUID usuarioId, String tipo) {
        return dsl.fetchCount(
                DSL.table("identidad.evento_dominio"),
                DSL.field("agregado_id").eq(usuarioId).and(DSL.field("tipo").eq(tipo)));
    }

    protected UUID idDelEvento(UUID usuarioId) {
        return (UUID) dsl.fetchOne("SELECT id FROM identidad.evento_dominio WHERE agregado_id = ? LIMIT 1", usuarioId)
                .get(0);
    }

    protected long eventosDeTipo(String tipo) {
        return contarEventos(tipo);
    }

    private static long contarEventos(String tipo) {
        return ((Number) dsl.fetchOne("SELECT count(*)::int FROM identidad.evento_dominio WHERE tipo = ?", tipo)
                        .get(0))
                .longValue();
    }

    protected long usuariosTotales() {
        return dsl.fetchCount(DSL.table("identidad.usuario"));
    }

    protected int cuentasDe(UUID usuarioId) {
        return dsl.fetchCount(
                DSL.table("nucleo_financiero.cuenta_billetera"),
                DSL.field("usuario_id").eq(usuarioId));
    }

    protected int calificacionesDe(UUID usuarioId) {
        return dsl.fetchCount(
                DSL.table("cumplimiento.calificacion_riesgo_cliente"),
                DSL.field("usuario_id").eq(usuarioId));
    }

    protected String estadoDeLaCuenta(UUID cuentaId) {
        return String.valueOf(
                dsl.fetchOne("SELECT estado FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuentaId)
                        .get(0));
    }

    protected String nivelDeLaCuenta(UUID cuentaId) {
        return String.valueOf(dsl.fetchOne(
                        "SELECT nivel_debida_diligencia FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuentaId)
                .get(0));
    }

    protected BigDecimal saldoDe(UUID cuentaId) {
        return (BigDecimal)
                dsl.fetchOne("SELECT saldo_disponible FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuentaId)
                        .get(0);
    }

    protected int movimientosDe(UUID cuentaId) {
        return dsl.fetchCount(
                DSL.table("nucleo_financiero.movimiento_billetera"),
                DSL.field("cuenta_billetera_id").eq(cuentaId));
    }

    protected String sqlSegundaCuenta(UUID usuarioId) {
        return """
               INSERT INTO nucleo_financiero.cuenta_billetera
                   (id, numero_cuenta, tipo, usuario_id, moneda, estado, nivel_debida_diligencia,
                    saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
               VALUES (gen_random_uuid(), 'AYSEGUNDA00001', 'USUARIO', '%s', 'BOB', 'ACTIVA',
                       'SIMPLIFICADA', 0.00, 0.00, false, now(), 0)
               """
                .formatted(usuarioId);
    }

    /**
     * Parametrizado y no concatenado. En una prueba el nombre es una constante, pero
     * la prohibicion 2 no admite excepciones «porque aca no se puede inyectar»: es
     * justamente ese razonamiento el que despues se copia a un sitio donde si.
     */
    protected boolean constraintExiste(String nombre) {
        Number cuantos = (Number) dsl.fetchOne(
                        """
                        SELECT (SELECT count(*) FROM pg_constraint WHERE conname = ?)
                             + (SELECT count(*) FROM pg_trigger    WHERE tgname  = ?)
                        """,
                        nombre,
                        nombre)
                .get(0);
        return cuantos.intValue() > 0;
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
