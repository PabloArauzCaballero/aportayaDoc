package bo.aportaya.transparencia;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import bo.aportaya.transparencia.aplicacion.CU61VerificarSorteo;
import bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion;
import bo.aportaya.transparencia.aplicacion.CU71RecalcularPuntaje;
import bo.aportaya.transparencia.aplicacion.CU72SellarBloque;
import bo.aportaya.transparencia.aplicacion.CU73VerificarCadena;
import bo.aportaya.transparencia.aplicacion.CU74EvaluarInsignias;
import bo.aportaya.transparencia.aplicacion.CU75EmitirCertificado;
import bo.aportaya.transparencia.aplicacion.CU76PublicarResena;
import bo.aportaya.transparencia.aplicacion.CU97EvaluarRiesgo;
import bo.aportaya.transparencia.dominio.ModeracionDeResena;
import bo.aportaya.transparencia.dominio.PuntajeDeReputacion;
import bo.aportaya.transparencia.dominio.SenalDeRiesgo;
import bo.aportaya.transparencia.infraestructura.CadenaRepositorio;
import bo.aportaya.transparencia.infraestructura.CertificadoRepositorio;
import bo.aportaya.transparencia.infraestructura.InsigniaRepositorio;
import bo.aportaya.transparencia.infraestructura.ModeloRepositorio;
import bo.aportaya.transparencia.infraestructura.ReputacionRepositorio;
import bo.aportaya.transparencia.infraestructura.ResenaRepositorio;
import bo.aportaya.transparencia.infraestructura.RiesgoRepositorio;
import bo.aportaya.transparencia.infraestructura.SnapshotRepositorio;
import java.math.BigDecimal;
import java.security.SecureRandom;
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

/** El armado del carril de transparencia, con las piezas construidas a mano. */
abstract class BaseDeTransparencia {

    /** Cada cuanto se recalcula el puntaje. Es configuracion, no una constante del negocio. */
    protected static final Duration PERIODO_DE_RECALCULO = Duration.ofDays(30);

    /** Una opinion pesa menos que un pago: 0,05 contra 0,30 de puntualidad. */
    protected static final BigDecimal PESO_DE_RESENA = new BigDecimal("0.0500");

    /**
     * La clave de firma **viene del almacen de secretos**, nunca de una columna ni de
     * una constante del codigo. En pruebas se compone en tiempo de ejecucion para no
     * dejar algo con forma de secreto escrito en el repositorio.
     */
    protected static final String CLAVE_DE_FIRMA = "clave" + "-de-prueba-" + "no-es-un-secreto-real";

    protected static final String BASE_PUBLICA = "https://aportaya.bo";

    /**
     * Donde empieza cada nivel de confianza, como porcentaje del maximo del modelo.
     * Es politica y llega como dato (invariante 10): en produccion sale de la
     * configuracion del servicio, no de una constante del dominio.
     */
    protected static final java.util.List<PuntajeDeReputacion.Corte> ESCALA_DE_CONFIANZA = java.util.List.of(
            new PuntajeDeReputacion.Corte("REFERENTE", new BigDecimal("90")),
            new PuntajeDeReputacion.Corte("MUY_CONFIABLE", new BigDecimal("75")),
            new PuntajeDeReputacion.Corte("CONFIABLE", new BigDecimal("60")),
            new PuntajeDeReputacion.Corte("BASICO", new BigDecimal("40")),
            new PuntajeDeReputacion.Corte("EN_OBSERVACION", new BigDecimal("25")));

    /** Los cortes de riesgo y de severidad. Se calibran contra desenlaces reales. */
    protected static final SenalDeRiesgo.Escala ESCALA_DE_RIESGO = new SenalDeRiesgo.Escala(
            new BigDecimal("60"),
            new BigDecimal("80"),
            new BigDecimal("0.50"),
            new BigDecimal("0.25"),
            new BigDecimal("0.10"));

    /** Cuanto se atenua una opinion por conflicto y por volumen. */
    protected static final ModeracionDeResena.Atenuacion ATENUACION =
            new ModeracionDeResena.Atenuacion(new BigDecimal("0.50"), new BigDecimal("0.50"), 5);

    /** El desempeño minimo que publica el criterio de ORGANIZADOR_CONFIABLE. */
    protected static final BigDecimal DESEMPENO_MINIMO_DE_ORGANIZADOR = new BigDecimal("80");

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeTransparencia fixtura;
    protected static Consumidos consumidos;

    protected static CU61VerificarSorteo sorteoCU;
    protected static CU70RegistrarEventoReputacion eventoCU;
    protected static CU71RecalcularPuntaje puntajeCU;
    protected static CU72SellarBloque bloqueCU;
    protected static CU73VerificarCadena cadenaCU;
    protected static CU74EvaluarInsignias insigniaCU;
    protected static CU75EmitirCertificado certificadoCU;
    protected static CU76PublicarResena resenaCU;
    protected static CU97EvaluarRiesgo riesgoCU;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeTransparencia(dslFixtura);
        consumidos = new Consumidos("transparencia");
        fixtura.catalogoDeReputacion();

        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("transparencia");
        var reputaciones = new ReputacionRepositorio();
        var modelos = new ModeloRepositorio();
        var cadenas = new CadenaRepositorio();
        var insignias = new InsigniaRepositorio();
        var certificados = new CertificadoRepositorio();
        var resenas = new ResenaRepositorio();
        var riesgos = new RiesgoRepositorio();
        var snapshots = new SnapshotRepositorio();

        sorteoCU = new CU61VerificarSorteo(datos, cadenas, outbox, Reloj.delSistema());
        eventoCU = new CU70RegistrarEventoReputacion(datos, reputaciones, modelos, outbox, Reloj.delSistema());
        puntajeCU = new CU71RecalcularPuntaje(
                datos,
                reputaciones,
                modelos,
                snapshots,
                outbox,
                Reloj.delSistema(),
                PERIODO_DE_RECALCULO,
                ESCALA_DE_CONFIANZA);
        bloqueCU = new CU72SellarBloque(datos, cadenas, outbox, Reloj.delSistema());
        cadenaCU = new CU73VerificarCadena(datos, cadenas, outbox, Reloj.delSistema());
        insigniaCU =
                new CU74EvaluarInsignias(datos, insignias, outbox, Reloj.delSistema(), DESEMPENO_MINIMO_DE_ORGANIZADOR);
        certificadoCU = new CU75EmitirCertificado(
                datos,
                certificados,
                cadenas,
                outbox,
                Reloj.delSistema(),
                new SecureRandom(),
                CLAVE_DE_FIRMA,
                BASE_PUBLICA);
        resenaCU = new CU76PublicarResena(datos, resenas, outbox, Reloj.delSistema(), PESO_DE_RESENA, ATENUACION);
        riesgoCU = new CU97EvaluarRiesgo(
                datos, riesgos, reputaciones, modelos, outbox, Reloj.delSistema(), ESCALA_DE_RIESGO);
    }

    protected ContextoSesion contextoDe(UUID usuarioId) {
        return ContextoSesion.de(
                usuarioId, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected ContextoSesion contextoDeSistema() {
        return ContextoSesion.deSistema(
                UUID.randomUUID(), new Traza(UUID.randomUUID().toString()));
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

    /**
     * Como {@link #rechazaLaBase}, pero para las reglas que la base verifica **al
     * confirmar**.
     *
     * <p>{@code tg_puntaje_cuadra} es un disparador de restriccion
     * {@code DEFERRABLE INITIALLY DEFERRED}: corre al final de la transaccion, no al
     * escribir. Con un {@code rollback} nunca llega a correr, y la prueba diria que la
     * regla no existe cuando en realidad no se la dejo hablar. {@code SET CONSTRAINTS
     * ALL IMMEDIATE} la adelanta dentro de la misma transaccion, que despues se deshace
     * igual.
     */
    protected String rechazaAlConfirmar(String sql, Object... parametros) {
        try {
            transaccion.execute(estado -> {
                dsl.execute(sql, parametros);
                dsl.execute("SET CONSTRAINTS ALL IMMEDIATE");
                estado.setRollbackOnly();
                return null;
            });
            return "";
        } catch (RuntimeException e) {
            return raizDe(e);
        }
    }

    /** Ejecuta y devuelve el mensaje con el que la base lo rechaza. Siempre con parametros. */
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
