package bo.aportaya.grupos;

import bo.aportaya.grupos.aplicacion.CU68Postular;
import bo.aportaya.grupos.aplicacion.CU68Postular.EntradaPostulacion;
import bo.aportaya.grupos.aplicacion.CU68Postular.SalidaPostulacion;
import bo.aportaya.grupos.infraestructura.EmparejamientoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.math.BigDecimal;
import java.util.ArrayList;
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

/** El armado de CU-68, con las piezas construidas a mano. */
abstract class BaseDeCU68 {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static CU68Postular postularCU;
    protected static FixturaDeGrupos fixtura;
    protected static Consumidos consumidos;

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        postularCU = new CU68Postular(
                new Datos(dsl), new EmparejamientoRepositorio(), new Outbox("grupos"), Reloj.delSistema());
        fixtura = new FixturaDeGrupos(dslFixtura);
        consumidos = new Consumidos("grupos");
    }

    private static UUID postulante;

    /**
     * El mismo usuario en toda la clase. Crear uno nuevo por llamada haria que «ya
     * tenes una postulacion pendiente» no se disparara nunca: la prueba pasaria por
     * la razon equivocada, que es la peor forma de pasar.
     */
    protected ContextoSesion contexto() {
        if (postulante == null) {
            postulante = fixtura.usuario();
        }
        return ContextoSesion.de(
                postulante, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected BigDecimal uno() {
        return BigDecimal.ONE;
    }

    /** El criterio vigente, con sus pesos: datos con vigencia, no constantes. */
    protected void criterioVigente() {
        dslFixtura.execute(
                """
                INSERT INTO grupos.criterio_emparejamiento
                    (id, peso_reputacion, peso_monto, peso_geografia, peso_historial_comun,
                     reputacion_minima, max_morosos_por_grupo, vigente_desde)
                VALUES (gen_random_uuid(), 0.40, 0.30, 0.20, 0.10, 0, 2, now() - interval '1 day')
                ON CONFLICT DO NOTHING
                """);
    }

    protected UUID grupoConCupoLibre() {
        UUID grupo = fixtura.grupoConformado(3);
        fixtura.participantesConCupo(grupo, 3);
        dslFixtura.execute(
                """
                UPDATE grupos.cupo SET estado = 'LIBRE', participante_id = NULL
                 WHERE id = (SELECT id FROM grupos.cupo WHERE grupo_id = ? ORDER BY numero DESC LIMIT 1)
                """,
                grupo);
        return grupo;
    }

    protected UUID grupoSinCuposLibres() {
        UUID grupo = fixtura.grupoConformado(3);
        fixtura.participantesConCupo(grupo, 3);
        return grupo;
    }

    protected SalidaPostulacion postular(
            UUID grupo, boolean restringido, boolean kycSuficiente, int reputacion, int morosos) {
        return transaccion.execute(e -> postularCU.postular(
                new EntradaPostulacion(
                        grupo,
                        (short) 1,
                        "quiero entrar",
                        restringido,
                        new BigDecimal("450.00"),
                        kycSuficiente,
                        reputacion,
                        morosos,
                        new BigDecimal("0.80"),
                        new BigDecimal("0.90"),
                        new BigDecimal("0.70"),
                        new BigDecimal("0.10")),
                contexto()));
    }

    /** Una propuesta de grupo con dos postulantes que todavia no respondieron. */
    protected Propuesta propuestaConDosPostulantes() {
        criterioVigente();
        UUID criterio = (UUID) dslFixtura
                .fetchOne("SELECT id FROM grupos.criterio_emparejamiento ORDER BY vigente_desde DESC LIMIT 1")
                .get(0);
        UUID propuesta = UUID.randomUUID();
        dslFixtura.execute(
                """
                INSERT INTO grupos.propuesta_grupo
                    (id, criterio_id, monto_aporte, periodicidad, puntaje_cohesion, riesgo_estimado,
                     estado, aceptaciones_recibidas, expira_en)
                VALUES (?, ?, 500.00, 'MENSUAL', 0.80, 0.20, 'PROPUESTA', 0, now() + interval '3 days')
                """,
                propuesta,
                criterio);

        List<UUID> postulaciones = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID postulacion = UUID.randomUUID();
            dslFixtura.execute(
                    """
                    INSERT INTO grupos.postulacion_emparejamiento
                        (id, usuario_id, monto_deseado, rango_monto_min, rango_monto_max,
                         periodicidad_deseada, fecha_inicio_deseada, preferencia_turno,
                         tolerancia_riesgo, estado, vigente_hasta)
                    VALUES (?, ?, 500.00, 400.00, 600.00, 'MENSUAL', CURRENT_DATE + 7,
                            'INDIFERENTE', 'MEDIA', 'ACTIVA', now() + interval '30 days')
                    """,
                    postulacion,
                    fixtura.usuario());
            dslFixtura.execute(
                    "INSERT INTO grupos.propuesta_postulacion (propuesta_id, postulacion_id) VALUES (?, ?)",
                    propuesta,
                    postulacion);
            postulaciones.add(postulacion);
        }
        return new Propuesta(propuesta, List.copyOf(postulaciones));
    }

    protected void vencer(UUID propuestaId) {
        dslFixtura.execute(
                "UPDATE grupos.propuesta_grupo SET expira_en = now() - interval '1 day' WHERE id = ?", propuestaId);
    }

    protected BigDecimal puntajeGuardado(UUID solicitudId) {
        return (BigDecimal)
                dsl.fetchOne("SELECT puntaje_compatibilidad FROM grupos.solicitud_ingreso WHERE id = ?", solicitudId)
                        .get(0);
    }

    protected UUID grupoMaterializadoDe(UUID propuestaId) {
        return (UUID)
                dsl.fetchOne("SELECT grupo_materializado_id FROM grupos.propuesta_grupo WHERE id = ?", propuestaId)
                        .get(0);
    }

    protected boolean respondioAlguien(UUID propuestaId) {
        return contar("SELECT count(*)::int FROM grupos.propuesta_postulacion WHERE propuesta_id = '" + propuestaId
                        + "' AND respondido_en IS NOT NULL")
                > 0;
    }

    protected int contar(String consulta) {
        return ((Number) dsl.fetchOne(consulta).get(0)).intValue();
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
                            'prueba_ingreso', gen_random_uuid(), 'CREACION', 'TAREA_PROGRAMADA',
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

    protected record Propuesta(UUID id, List<UUID> postulaciones) {}
}
