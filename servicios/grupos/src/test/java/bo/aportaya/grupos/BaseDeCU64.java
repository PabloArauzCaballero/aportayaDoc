package bo.aportaya.grupos;

import bo.aportaya.grupos.aplicacion.CU64TraspasarCupo;
import bo.aportaya.grupos.aplicacion.CU64TraspasarCupo.EntradaTraspaso;
import bo.aportaya.grupos.infraestructura.TraspasoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.math.BigDecimal;
import java.util.List;
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

/** El armado de CU-64, con las piezas construidas a mano. */
abstract class BaseDeCU64 {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static CU64TraspasarCupo traspasar;
    protected static FixturaDeGrupos fixtura;
    protected static Consumidos consumidos;
    protected static UUID actor;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        traspasar = new CU64TraspasarCupo(
                new Datos(dsl), new TraspasoRepositorio(), new Outbox("grupos"), Reloj.delSistema());
        fixtura = new FixturaDeGrupos(dslFixtura);
        consumidos = new Consumidos("grupos");
        actor = fixtura.usuario();
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                actor, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    /** Un grupo con turnos programados, su titular y alguien esperando entrar. */
    protected Escenario escenarioConTurno() {
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 3);
        List<UUID> periodos = fixtura.periodos(grupo, 3, new BigDecimal("1500.00"));
        List<UUID> cupos = cuposDe(grupo);
        fixtura.turnos(grupo, periodos, cupos);
        UUID entrante = fixtura.participanteSuelto(grupo);
        return new Escenario(grupo, cupos.get(0), participantes.get(0), entrante);
    }

    protected UUID traspasar(Escenario escenario, boolean salienteAlDia, String kycDelEntrante, int reputacionMinima) {
        return transaccion.execute(e -> traspasar.ejecutar(
                new EntradaTraspaso(
                        escenario.cupo(),
                        escenario.entrante(),
                        "RETIRO",
                        salienteAlDia,
                        kycDelEntrante,
                        kycMinimoDe(escenario.grupo()),
                        100,
                        reputacionMinima,
                        true,
                        true,
                        Optional.empty()),
                contexto()));
    }

    protected void exigirKyc(UUID grupo, String nivel) {
        dslFixtura.execute("UPDATE grupos.grupo SET requiere_kyc_minimo = ? WHERE id = ?", nivel, grupo);
    }

    protected String kycMinimoDe(UUID grupo) {
        return String.valueOf(dsl.fetchOne("SELECT requiere_kyc_minimo FROM grupos.grupo WHERE id = ?", grupo)
                .get(0));
    }

    protected List<UUID> cuposDe(UUID grupoId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table("grupos.cupo"))
                .where(DSL.field("grupo_id").eq(grupoId))
                .orderBy(DSL.field("numero"))
                .fetch(DSL.field("id", UUID.class));
    }

    protected UUID titularDelCupo(UUID cupoId) {
        return (UUID) dsl.fetchOne("SELECT participante_id FROM grupos.cupo WHERE id = ?", cupoId)
                .get(0);
    }

    protected short ordenDelCupo(UUID cupoId) {
        return (Short) dsl.fetchOne("SELECT orden_asignado FROM grupos.turno WHERE cupo_id = ?", cupoId)
                .get(0);
    }

    protected BigDecimal deudaTransferida(UUID cupoId) {
        return (BigDecimal) dsl.fetchOne("SELECT deuda_transferida FROM grupos.traspaso_cupo WHERE cupo_id = ?", cupoId)
                .get(0);
    }

    protected String estadoDelParticipante(UUID participante) {
        return String.valueOf(dsl.fetchOne("SELECT estado FROM grupos.participante WHERE id = ?", participante)
                .get(0));
    }

    protected int cuposDelGrupo(UUID grupo) {
        return dsl.fetchCount(DSL.table("grupos.cupo"), DSL.field("grupo_id").eq(grupo));
    }

    protected int cuposOcupadosDelGrupo(UUID grupo) {
        return dsl.fetchCount(
                DSL.table("grupos.cupo"),
                DSL.field("grupo_id").eq(grupo).and(DSL.field("estado").eq("OCUPADO")));
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

    protected record Escenario(UUID grupo, UUID cupo, UUID saliente, UUID entrante) {}
}
