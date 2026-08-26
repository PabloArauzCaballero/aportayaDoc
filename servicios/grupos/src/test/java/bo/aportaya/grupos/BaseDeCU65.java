package bo.aportaya.grupos;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;

import bo.aportaya.grupos.aplicacion.CU65Retirarse;
import bo.aportaya.grupos.aplicacion.CU65Retirarse.EntradaRetiro;
import bo.aportaya.grupos.aplicacion.CU65Retirarse.SalidaRetiro;
import bo.aportaya.grupos.infraestructura.RetiroRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
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

/** El armado de CU-65, con las piezas construidas a mano. */
abstract class BaseDeCU65 {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static CU65Retirarse retirarse;
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
        retirarse =
                new CU65Retirarse(new Datos(dsl), new RetiroRepositorio(), new Outbox("grupos"), Reloj.delSistema());
        fixtura = new FixturaDeGrupos(dslFixtura);
        consumidos = new Consumidos("grupos");
        actor = fixtura.usuario();
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                actor, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    /** Un participante activo con su cupo, en un grupo con el estado que se pida. */
    protected UUID participanteActivo(String estadoDelGrupo) {
        // Tres como minimo: ck_grupo_num_periodos exige >= 3, y tiene razon — un
        // pasanaku de dos personas es un prestamo con otro nombre.
        UUID grupo = fixtura.grupoConformado(3);
        dslFixtura.execute("UPDATE grupos.grupo SET estado = ? WHERE id = ?", estadoDelGrupo, grupo);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 3);
        return participantes.get(0);
    }

    protected SalidaRetiro solicitar(
            UUID participante, boolean yaCobro, String aportado, String deuda, String restantes) {
        return transaccion.execute(e -> retirarse.solicitar(
                new EntradaRetiro(
                        participante,
                        "me mudo de ciudad",
                        yaCobro,
                        Dinero.de(aportado, BOB),
                        Dinero.de(deuda, BOB),
                        Dinero.de(restantes, BOB)),
                contexto()));
    }

    protected void aprobar(SalidaRetiro salida, UUID participante, Optional<UUID> planDePago) {
        transaccion.execute(e -> {
            retirarse.aprobar(
                    salida.solicitudId(), participante, salida.posicion(), planDePago, "retiro voluntario", contexto());
            return null;
        });
    }

    protected String estadoDelParticipante(UUID participante) {
        return String.valueOf(dsl.fetchOne("SELECT estado FROM grupos.participante WHERE id = ?", participante)
                .get(0));
    }

    protected String motivoDeSalida(UUID participante) {
        return String.valueOf(dsl.fetchOne("SELECT motivo_salida FROM grupos.participante WHERE id = ?", participante)
                .get(0));
    }

    protected String estadoDelCupoDe(UUID participante) {
        Object estado = dsl.fetchOne(
                        "SELECT estado FROM grupos.cupo WHERE liberado_en IS NOT NULL AND grupo_id = "
                                + "(SELECT grupo_id FROM grupos.participante WHERE id = ?) LIMIT 1",
                        participante)
                .get(0);
        return String.valueOf(estado);
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
