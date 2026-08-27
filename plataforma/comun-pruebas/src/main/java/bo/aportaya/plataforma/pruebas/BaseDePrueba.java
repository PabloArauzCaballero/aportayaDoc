package bo.aportaya.plataforma.pruebas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * La PostgreSQL real de las pruebas de integracion, con el esquema de {@code sql/}
 * aplicado.
 *
 * <p>El contenedor se levanta UNA vez por corrida y se reutiliza: levantarlo por
 * clase multiplica por veinte el tiempo de la suite y termina en pruebas que nadie
 * corre en local (ADR-026).
 *
 * <p>El esquema se aplica con el {@code psql} del propio contenedor y no por JDBC,
 * porque {@code sql/aplicar.sql} usa {@code \\ir}: es un guion de psql, no un lote
 * de sentencias.
 */
public final class BaseDePrueba {

    private static final String IMAGEN = "postgres:16";
    private static final String NOMBRE = "pasanaku";

    private static PostgreSQLContainer<?> contenedor;

    private BaseDePrueba() {}

    /** El contenedor compartido, ya con esquema, roles, permisos y catalogos. */
    public static synchronized PostgreSQLContainer<?> contenedor() {
        if (contenedor == null) {
            contenedor = arrancar();
        }
        return contenedor;
    }

    /** Una conexion como el rol dueno de la base. */
    public static Connection conexion() throws SQLException {
        PostgreSQLContainer<?> c = contenedor();
        return DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
    }

    private static PostgreSQLContainer<?> arrancar() {
        Path repositorio = raizDelRepositorio();
        PostgreSQLContainer<?> nuevo = new PostgreSQLContainer<>(IMAGEN)
                .withDatabaseName(NOMBRE)
                .withUsername(NOMBRE)
                .withPassword(NOMBRE)
                .withFileSystemBind(repositorio.resolve("sql").toString(), "/repo/sql", BindMode.READ_ONLY)
                // Cinco minutos para estar listo, no el minuto por omision. No es
                // tolerancia a un contenedor lento: es que la maquina de desarrollo
                // corre otros stacks y con carga alta un PostgreSQL tarda mas en
                // aceptar conexiones. Con el valor por omision la prueba falla por
                // «no arranco» y manda a buscar un defecto donde no lo hay.
                .withStartupTimeout(java.time.Duration.ofMinutes(5));
        nuevo.start();

        // El DDL califica cada tabla con su esquema; el SQL escrito a mano que viene
        // despues la referencia por nombre simple. Los 307 nombres son unicos.
        ejecutar(nuevo, "-c", "ALTER DATABASE " + NOMBRE + " SET search_path TO " + esquemas());
        ejecutar(nuevo, "-f", "/repo/sql/aplicar.sql");
        return nuevo;
    }

    private static String esquemas() {
        return "aportes, auditoria, cumplimiento, entregas, erp, garantia, grupos, identidad,"
                + " notificaciones, nucleo_financiero, organizador, publicidad, tarifas,"
                + " transparencia, catalogo, comun, public";
    }

    private static void ejecutar(PostgreSQLContainer<?> destino, String bandera, String valor) {
        try {
            var resultado = destino.execInContainer(
                    "psql", "-v", "ON_ERROR_STOP=1", "-U", NOMBRE, "-d", NOMBRE, "-q", bandera, valor);
            if (resultado.getExitCode() != 0) {
                throw new IllegalStateException("psql " + bandera + " " + valor + ":\n" + resultado.getStderr());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("No se pudo preparar la base de prueba", e);
        }
    }

    /** Sube desde el directorio del modulo hasta el que tiene {@code sql/}. */
    private static Path raizDelRepositorio() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null && !Files.isDirectory(actual.resolve("sql"))) {
            actual = actual.getParent();
        }
        if (actual == null) {
            throw new IllegalStateException(
                    "No encontre la raiz del repositorio desde " + Path.of("").toAbsolutePath());
        }
        return actual;
    }
}
