package bo.aportaya.notificaciones;

import bo.aportaya.notificaciones.aplicacion.CU80DespacharNotificacion;
import bo.aportaya.notificaciones.aplicacion.CU81ProgramarRecordatorios;
import bo.aportaya.notificaciones.aplicacion.CU82ProcesarRespuesta;
import bo.aportaya.notificaciones.aplicacion.CU83DespacharLote;
import bo.aportaya.notificaciones.aplicacion.CU83DespacharLote.AdaptadorMensajeria;
import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.notificaciones.dominio.VentanaDeEnvio;
import bo.aportaya.notificaciones.infraestructura.EnvioRepositorio;
import bo.aportaya.notificaciones.infraestructura.NotificacionRepositorio;
import bo.aportaya.notificaciones.infraestructura.PlantillaRepositorio;
import bo.aportaya.notificaciones.infraestructura.ProgramacionRepositorio;
import bo.aportaya.notificaciones.infraestructura.ProveedorRepositorio;
import bo.aportaya.notificaciones.infraestructura.RespuestaRepositorio;
import bo.aportaya.notificaciones.infraestructura.SupresionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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

/** El armado del carril de notificaciones, con las piezas construidas a mano. */
abstract class BaseDeNotificaciones {

    protected static final String SECRETO_WEBHOOK = "secreto-de-prueba-solo-para-pruebas";
    protected static final int UMBRAL_DE_SALUD = 70;

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeNotificaciones fixtura;
    protected static Consumidos consumidos;

    protected static CU80DespacharNotificacion despachoCU;
    protected static CU81ProgramarRecordatorios recordatoriosCU;
    protected static CU82ProcesarRespuesta respuestaCU;
    protected static CU83DespacharLote loteCU;
    protected static AdaptadorEspia adaptador;

    /**
     * El adaptador simulado, que ademas **cuenta**.
     *
     * <p>El default del proyecto es el simulador: nada sale de verdad. Contar los
     * envios es lo que permite afirmar «el proveedor recibio exactamente un mensaje»,
     * que es un criterio de aceptacion y no una suposicion.
     */
    protected static final class AdaptadorEspia implements AdaptadorMensajeria {
        final List<String> enviados = new ArrayList<>();
        Set<String> proveedoresQueFallan = Set.of();

        @Override
        public Resultado enviar(String proveedorCodigo, Canal canal, String destinatario, UUID envioId) {
            enviados.add(proveedorCodigo + ":" + envioId);
            return proveedoresQueFallan.contains(proveedorCodigo)
                    ? new Resultado(false, null, "PROVEEDOR_CAIDO")
                    : new Resultado(true, "msg-" + envioId, null);
        }

        void reiniciar() {
            enviados.clear();
            proveedoresQueFallan = Set.of();
        }
    }

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(fuente));
        fixtura = new FixturaDeNotificaciones(dslFixtura);
        consumidos = new Consumidos("notificaciones");
        adaptador = new AdaptadorEspia();

        Datos datos = new Datos(dsl);
        Outbox outbox = new Outbox("notificaciones");
        var notificaciones = new NotificacionRepositorio();
        var supresiones = new SupresionRepositorio();

        despachoCU = new CU80DespacharNotificacion(
                datos,
                notificaciones,
                new PlantillaRepositorio(),
                supresiones,
                outbox,
                Reloj.delSistema(),
                // Los tres encendidos por omision. WhatsApp y SMS quedan apagados:
                // encenderlos aca seria hacer en una prueba lo que el contrato prohibe.
                Set.of(Canal.IN_APP, Canal.CORREO, Canal.PUSH),
                new VentanaDeEnvio(LocalTime.of(0, 0), LocalTime.of(23, 59)));

        recordatoriosCU =
                new CU81ProgramarRecordatorios(datos, new ProgramacionRepositorio(), outbox, Reloj.delSistema());

        respuestaCU = new CU82ProcesarRespuesta(
                datos,
                new RespuestaRepositorio(),
                supresiones,
                consumidos,
                outbox,
                Reloj.delSistema(),
                SECRETO_WEBHOOK);

        loteCU = new CU83DespacharLote(
                datos,
                new EnvioRepositorio(),
                new ProveedorRepositorio(),
                outbox,
                Reloj.delSistema(),
                UMBRAL_DE_SALUD,
                java.time.Duration.ofMinutes(60),
                adaptador);
    }

    protected ContextoSesion contexto() {
        return ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected ContextoSesion contextoDe(UUID usuarioId) {
        return ContextoSesion.de(
                usuarioId, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    protected int contar(String consulta, Object... parametros) {
        return ((Number) dsl.fetchOne(consulta, parametros).get(0)).intValue();
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
