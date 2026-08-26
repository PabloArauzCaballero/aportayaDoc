package bo.aportaya.grupos;

import bo.aportaya.grupos.aplicacion.CU63Acordar;
import bo.aportaya.grupos.aplicacion.CU63Acordar.EntradaPropuesta;
import bo.aportaya.grupos.dominio.ComputoDeVotacion.Sentido;
import bo.aportaya.grupos.infraestructura.AcuerdoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

/** El armado de CU-63, con las piezas construidas a mano. */
abstract class BaseDeCU63 {

    protected static DSLContext dsl;
    protected static TransactionTemplate transaccion;
    protected static CU63Acordar acordar;
    protected static FixturaDeGrupos fixtura;
    protected static Consumidos consumidos;
    protected static UUID proponente;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        DSLContext dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        acordar = new CU63Acordar(new Datos(dsl), new AcuerdoRepositorio(), new Outbox("grupos"), Reloj.delSistema());
        fixtura = new FixturaDeGrupos(dslFixtura);
        consumidos = new Consumidos("grupos");
        proponente = fixtura.usuario();
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                proponente, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected UUID proponer(UUID grupo, String tipo, BigDecimal quorum, Optional<UUID> afectado) {
        return transaccion.execute(e -> acordar.proponer(
                new EntradaPropuesta(
                        grupo,
                        tipo,
                        "propuesta de prueba",
                        proponente,
                        quorum,
                        afectado,
                        OffsetDateTime.now().plusDays(7)),
                contexto()));
    }

    /** Empuja el plazo al pasado, para probar el cierre por vencimiento. */
    protected void vencer(UUID acuerdo) {
        transaccion.execute(estado -> {
            dsl.execute("UPDATE grupos.acuerdo SET cierra_en = now() - interval '1 day' WHERE id = ?", acuerdo);
            return null;
        });
    }

    protected Sentido votar(UUID acuerdo, UUID participante, String sentido) {
        return transaccion.execute(e -> acordar.votar(acuerdo, participante, sentido, contexto()));
    }

    protected BigDecimal pesoDelVoto(UUID acuerdo, UUID participante) {
        return (BigDecimal) dsl.fetchOne(
                        "SELECT peso FROM grupos.voto_participante WHERE acuerdo_id = ? AND participante_id = ?",
                        acuerdo,
                        participante)
                .get(0);
    }

    protected String estadoDelAcuerdo(UUID acuerdo) {
        return String.valueOf(dsl.fetchOne("SELECT estado FROM grupos.acuerdo WHERE id = ?", acuerdo)
                .get(0));
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
                            'prueba_acuerdo', gen_random_uuid(), 'CREACION', 'TAREA_PROGRAMADA',
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
