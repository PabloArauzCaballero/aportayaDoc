package bo.aportaya.auditoria;

import bo.aportaya.auditoria.aplicacion.CU07EjercerDerechos;
import bo.aportaya.auditoria.aplicacion.CU58DescargarExportacion;
import bo.aportaya.auditoria.aplicacion.CU58EjecutarReporte;
import bo.aportaya.auditoria.aplicacion.CU98PublicarTablero;
import bo.aportaya.auditoria.infraestructura.AnonimizacionRepositorio;
import bo.aportaya.auditoria.infraestructura.EjecutorDeConsulta;
import bo.aportaya.auditoria.infraestructura.IndicadorRepositorio;
import bo.aportaya.auditoria.infraestructura.PoliticaRetencionRepositorio;
import bo.aportaya.auditoria.infraestructura.ReporteRepositorio;
import bo.aportaya.auditoria.infraestructura.SolicitudDatosRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.datos.TransaccionAparte;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.plataforma.pruebas.BaseDePrueba;
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

/** El armado del carril de auditoria, con las piezas construidas a mano. */
abstract class BaseDeAuditoria {

    protected static DSLContext dsl;
    protected static DSLContext dslFixtura;
    protected static TransactionTemplate transaccion;
    protected static FixturaDeAuditoria fixtura;
    protected static FixturaDeReportes reportes;
    protected static CU98PublicarTablero tableroCU;
    protected static CU07EjercerDerechos derechosCU;
    protected static CU58EjecutarReporte reportesCU;
    protected static CU58DescargarExportacion descargasCU;

    /** El tope de descargas con el que se arma el caso de uso en las pruebas. */
    protected static final int TOPE_DE_DESCARGAS = 3;

    /** Un operador con permiso de ver indicadores. El rol decide que filas ve. */
    protected static final UUID OPERADOR = UUID.fromString("00000000-0000-4000-8000-0000000000a1");

    @BeforeAll
    static void armar() {
        var contenedor = BaseDePrueba.contenedor();
        DataSource fuente = new DriverManagerDataSource(
                contenedor.getJdbcUrl(), contenedor.getUsername(), contenedor.getPassword());
        // El proxy consciente de la transaccion no es opcional: sin el, jOOQ pide una
        // conexion nueva al pool y la consulta corre FUERA de la transaccion que acaba
        // de hacer `SET LOCAL`, con lo cual el contexto de RLS no aplica.
        dsl = DSL.using(new TransactionAwareDataSourceProxy(fuente), SQLDialect.POSTGRES);
        dslFixtura = DSL.using(fuente, SQLDialect.POSTGRES);
        var gestor = new DataSourceTransactionManager(fuente);
        transaccion = new TransactionTemplate(gestor);
        fixtura = new FixturaDeAuditoria(dslFixtura);
        // El operador de las pruebas existe de verdad: `ejecucion_reporte` lo exige por
        // clave foranea, y con razon — un reporte lo saca alguien.
        fixtura.usuarioConId(OPERADOR);
        reportes = new FixturaDeReportes(dslFixtura);
        tableroCU = new CU98PublicarTablero(new Datos(dsl), new IndicadorRepositorio());
        derechosCU = new CU07EjercerDerechos(
                new Datos(dsl),
                new SolicitudDatosRepositorio(),
                new PoliticaRetencionRepositorio(),
                new AnonimizacionRepositorio(),
                new Outbox("auditoria"),
                Reloj.delSistema(),
                // Sin feriados declarados el plazo cae en dias corridos habiles, que es
                // lo que hace la prueba determinista sin depender del calendario real.
                CalendarioVacio.SIN_FERIADOS,
                15);
        reportesCU = new CU58EjecutarReporte(
                new Datos(dsl),
                new ReporteRepositorio(),
                // Un segundo de tope: alcanza para cualquier consulta de prueba y deja
                // comprobar el corte con un `pg_sleep` sin que la suite tarde.
                new EjecutorDeConsulta(1),
                new TransaccionAparte(gestor),
                new Outbox("auditoria"),
                Reloj.delSistema(),
                72);
        descargasCU = new CU58DescargarExportacion(
                new Datos(dsl),
                new ReporteRepositorio(),
                new Outbox("auditoria"),
                Reloj.delSistema(),
                TOPE_DE_DESCARGAS);
    }

    /**
     * El rol va en MAYUSCULAS porque asi lo compara {@code fn_seg_rol_privilegiado()}:
     * la politica de {@code registro_acceso_datos} acepta BACKOFFICE, CUMPLIMIENTO o
     * AUDITOR y nada mas. Con «auditor» en minusculas la prueba pasaria igual —el
     * contenedor conecta como superusuario y omite RLS— y en produccion el INSERT
     * quedaria rechazado por la politica. Una prueba que solo pasa porque no aplica la
     * regla que dice probar es peor que no tenerla.
     */
    protected static ContextoSesion contexto() {
        return ContextoSesion.de(
                OPERADOR, "AUDITOR", new Traza(UUID.randomUUID().toString()));
    }

    protected static boolean politicaExiste(String nombre) {
        Number cuantas = (Number) dslFixtura
                .fetchOne("SELECT count(*) FROM pg_policies WHERE policyname = ?", nombre)
                .get(0);
        return cuantas.intValue() > 0;
    }

    /** Corre el SQL, espera que la base lo rechace y devuelve el mensaje del motor. */
    protected static String rechazaLaBase(String sql) {
        try {
            transaccion.execute(estado -> {
                dsl.execute("SET CONSTRAINTS ALL IMMEDIATE");
                dsl.execute(sql);
                // Se revierte a proposito: la prueba comprueba el rechazo, no deja
                // filas de prueba en el contenedor para la siguiente.
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

    protected static boolean constraintExiste(String nombre) {
        Number cuantos = (Number) dslFixtura
                .fetchOne(
                        """
                        SELECT (SELECT count(*) FROM pg_constraint WHERE conname = ?)
                             + (SELECT count(*) FROM pg_class WHERE relkind = 'i' AND relname = ?)
                        """,
                        nombre,
                        nombre)
                .get(0);
        return cuantos.intValue() > 0;
    }

    protected static boolean triggerExiste(String nombre) {
        Number cuantos = (Number) dslFixtura
                .fetchOne("SELECT count(*) FROM pg_trigger WHERE tgname = ?", nombre)
                .get(0);
        return cuantos.intValue() > 0;
    }

    protected static boolean funcionExiste(String nombre) {
        Number cuantos = (Number) dslFixtura
                .fetchOne("SELECT count(*) FROM pg_proc WHERE proname = ?", nombre)
                .get(0);
        return cuantos.intValue() > 0;
    }

    protected static CU98PublicarTablero.SalidaTablero tablero(String periodo) {
        return transaccion.execute(estado -> tableroCU.ejecutar(
                new CU98PublicarTablero.EntradaTablero(periodo, "GLOBAL", java.util.Optional.empty(), 6), contexto()));
    }
}
