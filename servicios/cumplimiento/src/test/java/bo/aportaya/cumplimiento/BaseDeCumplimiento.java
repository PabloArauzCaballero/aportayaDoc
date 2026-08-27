package bo.aportaya.cumplimiento;

import bo.aportaya.cumplimiento.aplicacion.CU02ElevarDiligencia;
import bo.aportaya.cumplimiento.aplicacion.CU03DeclararPep;
import bo.aportaya.cumplimiento.aplicacion.CU05AceptarContrato;
import bo.aportaya.cumplimiento.aplicacion.CU06RevisarConocimiento;
import bo.aportaya.cumplimiento.aplicacion.CU46VerificarAlcance;
import bo.aportaya.cumplimiento.aplicacion.CU54EscalarPlanesVencidos;
import bo.aportaya.cumplimiento.aplicacion.CU54RegistrarRiesgoOperativo;
import bo.aportaya.cumplimiento.aplicacion.CU55EscalarIncidentesVencidos;
import bo.aportaya.cumplimiento.aplicacion.CU55GestionarIncidente;
import bo.aportaya.cumplimiento.dominio.DesvioDePerfil;
import bo.aportaya.cumplimiento.dominio.NivelDeDiligencia;
import bo.aportaya.cumplimiento.dominio.PeriodicidadDeRevision;
import bo.aportaya.cumplimiento.dominio.PlazosDelIncidente;
import bo.aportaya.cumplimiento.dominio.RequisitosDeNivel;
import bo.aportaya.cumplimiento.infraestructura.AceptacionRepositorio;
import bo.aportaya.cumplimiento.infraestructura.CalificacionRiesgoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.CasoLftRepositorio;
import bo.aportaya.cumplimiento.infraestructura.ContratoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.DeclaracionPepRepositorio;
import bo.aportaya.cumplimiento.infraestructura.DiligenciaRepositorio;
import bo.aportaya.cumplimiento.infraestructura.IncidenteSeguridadRepositorio;
import bo.aportaya.cumplimiento.infraestructura.LicenciaRepositorio;
import bo.aportaya.cumplimiento.infraestructura.LimiteRepositorio;
import bo.aportaya.cumplimiento.infraestructura.PerfilTransaccionalRepositorio;
import bo.aportaya.cumplimiento.infraestructura.RevisionKycRepositorio;
import bo.aportaya.cumplimiento.infraestructura.RiesgoOperativoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.SandboxRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.util.Set;
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

/** El armado del carril de cumplimiento, con las piezas construidas a mano. */
abstract class BaseDeCumplimiento {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeCumplimiento fixtura;
    protected static FixturaDeRiesgos riesgos;
    protected static FixturaDeIncidentes incidentes;
    protected static Consumidos consumidos;
    protected static CU46VerificarAlcance alcanceCU;
    protected static CU05AceptarContrato contratoCU;
    protected static CU03DeclararPep pepCU;
    protected static CU02ElevarDiligencia diligenciaCU;
    protected static CU06RevisarConocimiento revisionCU;
    protected static CU54RegistrarRiesgoOperativo riesgoCU;
    protected static CU54EscalarPlanesVencidos escalarCU;
    protected static CU55GestionarIncidente incidenteCU;
    protected static CU55EscalarIncidentesVencidos escalarIncidentesCU;

    /**
     * Los plazos con que se arma el caso de uso en las pruebas. Son los mismos que la
     * configuracion por omision: una prueba que usa plazos propios comprueba su propia
     * aritmetica, no la politica que va a regir.
     */
    protected static final PlazosDelIncidente PLAZOS = new PlazosDelIncidente(
            java.util.Map.of(
                    "CRITICA", java.time.Duration.ofHours(24),
                    "ALTA", java.time.Duration.ofHours(48),
                    "MEDIA", java.time.Duration.ofHours(72),
                    "BAJA", java.time.Duration.ofHours(120)),
            java.time.Duration.ofHours(72));

    /** El plazo de regularizacion con que se arma el control en las pruebas. */
    protected static final int DIAS_DE_REGULARIZACION = 30;

    /** Umbrales de desvio: politica de cumplimiento, declarada, no constantes. */
    protected static final DesvioDePerfil.Umbrales UMBRALES = new DesvioDePerfil.Umbrales(
            new java.math.BigDecimal("100"), new java.math.BigDecimal("200"), new java.math.BigDecimal("500"));

    protected static final String REGLA_DESVIO = "RM-DESVIO-PERFIL";

    /** Los requisitos por nivel son configuracion declarada, no catalogo de la boveda. */
    protected static final RequisitosDeNivel REQUISITOS = new RequisitosDeNivel(java.util.Map.of(
            NivelDeDiligencia.ESTANDAR,
            java.util.List.of("CEDULA", "DOMICILIO"),
            NivelDeDiligencia.AMPLIADA,
            java.util.List.of("CEDULA", "DOMICILIO", "INGRESOS"),
            NivelDeDiligencia.REFORZADA,
            java.util.List.of("CEDULA", "DOMICILIO", "INGRESOS", "ORIGEN_FONDOS")));

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeCumplimiento(dslFixtura);
        riesgos = new FixturaDeRiesgos(dslFixtura);
        incidentes = new FixturaDeIncidentes(dslFixtura);
        consumidos = new Consumidos("cumplimiento");
        alcanceCU = new CU46VerificarAlcance(
                new Datos(dsl),
                new LicenciaRepositorio(),
                new SandboxRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema(),
                Set.of("RETIRO"));
        contratoCU = new CU05AceptarContrato(
                new Datos(dsl),
                new ContratoRepositorio(),
                new AceptacionRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema());
        pepCU = new CU03DeclararPep(
                new Datos(dsl),
                new DeclaracionPepRepositorio(),
                new CalificacionRiesgoRepositorio(),
                new DiligenciaRepositorio(),
                new CasoLftRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema(),
                6,
                30);
        diligenciaCU = new CU02ElevarDiligencia(
                new Datos(dsl),
                new DiligenciaRepositorio(),
                new CalificacionRiesgoRepositorio(),
                new DeclaracionPepRepositorio(),
                new CasoLftRepositorio(),
                new LimiteRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema(),
                REQUISITOS,
                new PeriodicidadDeRevision(6, 12, 24));
        revisionCU = new CU06RevisarConocimiento(
                new Datos(dsl),
                new CalificacionRiesgoRepositorio(),
                new PerfilTransaccionalRepositorio(),
                new RevisionKycRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema(),
                new PeriodicidadDeRevision(6, 12, 24),
                UMBRALES,
                REGLA_DESVIO);
        riesgoCU = new CU54RegistrarRiesgoOperativo(
                new Datos(dsl), new RiesgoOperativoRepositorio(), new Outbox("cumplimiento"), Reloj.delSistema());
        escalarCU = new CU54EscalarPlanesVencidos(
                new Datos(dsl),
                new RiesgoOperativoRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema(),
                DIAS_DE_REGULARIZACION,
                200);
        incidenteCU = new CU55GestionarIncidente(
                new Datos(dsl),
                new IncidenteSeguridadRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema(),
                PLAZOS);
        escalarIncidentesCU = new CU55EscalarIncidentesVencidos(
                new Datos(dsl),
                new IncidenteSeguridadRepositorio(),
                new RiesgoOperativoRepositorio(),
                new Outbox("cumplimiento"),
                Reloj.delSistema(),
                DIAS_DE_REGULARIZACION,
                200);
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected int contar(String consulta, Object... parametros) {
        return ((Number) dsl.fetchOne(consulta, parametros).get(0)).intValue();
    }

    /** Devuelve el mensaje con que la BASE rechaza, o vacio si no rechazo. */
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
