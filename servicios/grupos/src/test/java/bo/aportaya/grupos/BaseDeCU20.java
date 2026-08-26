package bo.aportaya.grupos;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;

import bo.aportaya.grupos.aplicacion.CU20CrearGrupo;
import bo.aportaya.grupos.aplicacion.CU20CrearGrupo.EntradaCreacion;
import bo.aportaya.grupos.aplicacion.CU20CrearGrupo.SalidaCreacion;
import bo.aportaya.grupos.dominio.GrupoNuevo;
import bo.aportaya.grupos.infraestructura.CreacionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
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

/** El armado de CU-20, con las piezas construidas a mano. */
abstract class BaseDeCU20 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU20CrearGrupo crearGrupo;
    protected static DoblesDeTarifasYNucleo dobles;
    protected static FixturaDeGrupos fixtura;
    protected static Consumidos consumidos;
    protected static UUID creador;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        DSLContext dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        crearGrupo = new CU20CrearGrupo(
                new Datos(dsl),
                new CreacionRepositorio(),
                new Outbox("grupos"),
                Reloj.delSistema(),
                Ids.seguros(),
                new java.math.BigDecimal("0.600"));
        dobles = new DoblesDeTarifasYNucleo(dslFixtura);
        fixtura = new FixturaDeGrupos(dslFixtura);
        consumidos = new Consumidos("grupos");
        creador = fixtura.usuario();
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                creador, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected GrupoNuevo datos() {
        return new GrupoNuevo(
                "Pasanaku de la cuadra",
                Dinero.de("500.00", BOB),
                "MENSUAL",
                5,
                4,
                LocalDate.now().plusDays(7));
    }

    protected EntradaCreacion entrada(UUID tarifario, boolean conLicencia, boolean conTarifario) {
        return new EntradaCreacion(
                datos(),
                Optional.empty(),
                true,
                conTarifario ? Optional.of(tarifario) : Optional.empty(),
                conLicencia,
                true);
    }

    protected SalidaCreacion crear(UUID tarifario) {
        dobles.publicarTarifario(tarifario);
        return transaccion.execute(e -> crearGrupo.ejecutar(entrada(tarifario, true, true), contexto()));
    }

    protected SalidaCreacion crearSinTarifario() {
        return transaccion.execute(e -> crearGrupo.ejecutar(entrada(UUID.randomUUID(), true, false), contexto()));
    }

    protected SalidaCreacion crearSinLicencia() {
        return transaccion.execute(e -> crearGrupo.ejecutar(entrada(UUID.randomUUID(), false, true), contexto()));
    }

    protected String eventoLleva(UUID grupoId, String clave) {
        return String.valueOf(dsl.fetchOne(
                        "SELECT payload ->> ? FROM grupos.evento_dominio WHERE agregado_id = ? LIMIT 1", clave, grupoId)
                .get(0));
    }

    protected String hashDelSnapshot(UUID congeladaId) {
        return String.valueOf(
                dsl.fetchOne("SELECT hash_snapshot FROM tarifas.tarifa_congelada_grupo WHERE id = ?", congeladaId)
                        .get(0));
    }

    protected UUID tarifarioDe(UUID congeladaId) {
        return (UUID) dsl.fetchOne("SELECT tarifario_id FROM tarifas.tarifa_congelada_grupo WHERE id = ?", congeladaId)
                .get(0);
    }

    protected UUID grupoDeLaCuenta(UUID cuentaId) {
        return (UUID) dsl.fetchOne("SELECT grupo_id FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuentaId)
                .get(0);
    }

    protected UUID usuarioDeLaCuenta(UUID cuentaId) {
        return (UUID) dsl.fetchOne("SELECT usuario_id FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuentaId)
                .get(0);
    }

    protected String estadoDelGrupo(UUID grupoId) {
        return String.valueOf(dsl.fetchOne("SELECT estado FROM grupos.grupo WHERE id = ?", grupoId)
                .get(0));
    }

    protected String codigoDe(UUID grupoId) {
        return String.valueOf(dsl.fetchOne("SELECT codigo_publico FROM grupos.grupo WHERE id = ?", grupoId)
                .get(0));
    }

    protected int cuposLibres(UUID grupoId) {
        return dsl.fetchCount(
                DSL.table("grupos.cupo"),
                DSL.field("grupo_id").eq(grupoId).and(DSL.field("estado").eq("LIBRE")));
    }

    protected long gruposTotales() {
        return dsl.fetchCount(DSL.table("grupos.grupo"));
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
