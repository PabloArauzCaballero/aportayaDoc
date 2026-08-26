package bo.aportaya.plataforma.pruebas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Invariantes 11 y 12, verificados por el motor y no por la revision de codigo.
 *
 * <p>La frontera entre servicios es el {@code GRANT}, no una convencion de nombres:
 * si {@code svc_aportes} pudiera leer {@code identidad}, la propiedad de los datos
 * seria una recomendacion.
 *
 * <p>Los roles {@code svc_*} son {@code NOLOGIN} en el esquema generado, asi que la
 * prueba entra con el dueno y hace {@code SET ROLE} — que es ademas la unica forma
 * de comprobar los permisos y no la conectividad.
 */
class AislamientoEsquemaTest {

    private static final List<String> SERVICIOS = List.of(
            "aportes",
            "auditoria",
            "cumplimiento",
            "entregas",
            "erp",
            "garantia",
            "grupos",
            "identidad",
            "notificaciones",
            "nucleo_financiero",
            "organizador",
            "publicidad",
            "tarifas",
            "transparencia");

    /** Una tabla real de cada esquema, para que el SELECT cruzado tenga a que apuntar. */
    private static String tablaDe(String esquema) {
        return switch (esquema) {
            case "aportes" -> "obligacion_aporte";
            case "auditoria" -> "bitacora_evento";
            case "cumplimiento" -> "debida_diligencia";
            case "entregas" -> "entrega_fondo";
            case "erp" -> "ejercicio_fiscal";
            case "garantia" -> "fondo_garantia";
            case "grupos" -> "grupo";
            case "identidad" -> "usuario";
            case "notificaciones" -> "notificacion";
            case "nucleo_financiero" -> "cuenta_billetera";
            case "organizador" -> "organizador";
            case "publicidad" -> "anunciante";
            case "tarifas" -> "tarifario";
            case "transparencia" -> "puntaje_reputacion";
            default -> throw new IllegalArgumentException(esquema);
        };
    }

    static List<String> servicios() {
        return SERVICIOS;
    }

    @ParameterizedTest(name = "svc_{0} no lee el esquema de ningun otro")
    @MethodSource("servicios")
    void ningunServicioLeeElEsquemaAjeno(String propio) throws SQLException {
        try (Connection conexion = BaseDePrueba.conexion();
                Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SET ROLE svc_" + propio);
            for (String ajeno : SERVICIOS) {
                if (ajeno.equals(propio)) {
                    continue;
                }
                String consulta = "SELECT 1 FROM %s.%s LIMIT 1".formatted(ajeno, tablaDe(ajeno));
                assertThatThrownBy(() -> sentencia.execute(consulta))
                        .as("svc_%s pudo leer %s: la frontera entre servicios dejo de existir", propio, ajeno)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    @DisplayName("El libro contable no se parte: solo svc_nucleo_financiero escribe asiento_contable")
    void soloElNucleoEscribeElLibro() throws SQLException {
        for (String otro : SERVICIOS) {
            if (otro.equals("nucleo_financiero")) {
                continue;
            }
            try (Connection conexion = BaseDePrueba.conexion();
                    Statement sentencia = conexion.createStatement()) {
                sentencia.execute("SET ROLE svc_" + otro);
                assertThat(puedeEscribir(sentencia, "nucleo_financiero.asiento_contable"))
                        .as("svc_%s puede escribir el libro contable (invariante 12)", otro)
                        .isFalse();
            }
        }

        try (Connection conexion = BaseDePrueba.conexion();
                Statement sentencia = conexion.createStatement()) {
            sentencia.execute("SET ROLE svc_nucleo_financiero");
            assertThat(puedeEscribir(sentencia, "nucleo_financiero.asiento_contable"))
                    .as("svc_nucleo_financiero no puede escribir el libro que le pertenece")
                    .isTrue();
        }
    }

    /**
     * Sin {@code USAGE} sobre el esquema ni siquiera se puede nombrar la tabla, y
     * {@code has_table_privilege} falla en vez de devolver false. Que no se pueda
     * preguntar es la forma mas fuerte de no poder escribir.
     */
    private boolean puedeEscribir(Statement sentencia, String tabla) throws SQLException {
        try (var filas =
                sentencia.executeQuery("SELECT has_table_privilege('%s', 'INSERT') AS puede".formatted(tabla))) {
            return filas.next() && filas.getBoolean("puede");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("permission denied")) {
                return false;
            }
            throw e;
        }
    }
}
