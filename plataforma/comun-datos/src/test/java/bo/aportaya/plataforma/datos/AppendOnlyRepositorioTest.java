package bo.aportaya.plataforma.datos;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariante 5: append-only. Corregir es un movimiento inverso, jamas un
 * {@code UPDATE}.
 *
 * <p>Lo hace cumplir la BASE, no la aplicacion. La diferencia importa: un `UPDATE`
 * escrito por error, un script de soporte a las tres de la manana o un ORM con dirty
 * checking pasan por encima de cualquier regla de codigo, y no por encima de un
 * trigger.
 *
 * <p>La lista de tablas no se escribe a mano: sale del catalogo del motor, asi que
 * una tabla sellada de mas —o de menos— aparece aca sin que nadie actualice nada.
 */
class AppendOnlyRepositorioTest {

    /** Las que el plan nombra explicitamente: si alguna deja de estar sellada, se sabe. */
    private static final List<String> IMPRESCINDIBLES = List.of(
            "nucleo_financiero.asiento_contable", "nucleo_financiero.movimiento_billetera", "comun.bitacora_evento");

    @Test
    @DisplayName("Las tablas del libro y la bitacora estan selladas contra UPDATE y DELETE")
    void lasTablasCriticasEstanSelladas() throws SQLException {
        List<String> selladas = tablasSelladas();

        assertThat(selladas).as("tablas append-only encontradas en el motor").hasSizeGreaterThan(20);
        assertThat(selladas).containsAll(IMPRESCINDIBLES);
    }

    @Test
    @DisplayName("La BASE rechaza el UPDATE sobre una tabla sellada, no la aplicacion")
    void laBaseRechazaElUpdate() throws SQLException {
        try (Connection conexion = BaseDePrueba.conexion();
                Statement sentencia = conexion.createStatement()) {
            // Sin filas de por medio: el trigger es BEFORE UPDATE FOR EACH ROW, asi
            // que hace falta una fila para dispararlo. Se inserta y se intenta mover.
            // Columnas y valores salidos del modelo: `accion` y `origen` tienen
            // CHECK cerrado, y `secuencia`, `hash_registro` y `hash_anterior` son
            // obligatorios — la bitacora es una cadena, no una lista.
            sentencia.execute(
                    """
                    INSERT INTO comun.bitacora_evento
                        (id, secuencia, entidad, entidad_id, accion, origen,
                         correlation_id, hash_registro, hash_anterior, fecha_hora)
                    VALUES (gen_random_uuid(), 999000001, 'prueba_append_only', gen_random_uuid(),
                            'CREACION', 'TAREA_PROGRAMADA', gen_random_uuid(),
                            repeat('a', 64), repeat('0', 64), now())
                    """);

            Throwable rechazo = catchSqlException(sentencia, "UPDATE comun.bitacora_evento SET accion = 'CAMBIADA'");
            assertThat(rechazo)
                    .as("la base dejo pasar un UPDATE sobre una tabla append-only")
                    .isNotNull();
            assertThat(rechazo).hasMessageContaining("R-AUD-01");

            Throwable rechazoBorrado = catchSqlException(sentencia, "DELETE FROM comun.bitacora_evento");
            assertThat(rechazoBorrado).isNotNull();
            assertThat(rechazoBorrado).hasMessageContaining("R-AUD-01");
        }
    }

    private Throwable catchSqlException(Statement sentencia, String consulta) {
        try {
            sentencia.execute(consulta);
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    private List<String> tablasSelladas() throws SQLException {
        List<String> selladas = new ArrayList<>();
        try (Connection conexion = BaseDePrueba.conexion();
                Statement sentencia = conexion.createStatement();
                ResultSet filas = sentencia.executeQuery(
                        """
                        SELECT n.nspname || '.' || c.relname AS tabla
                          FROM pg_trigger t
                          JOIN pg_class c ON c.oid = t.tgrelid
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE NOT t.tgisinternal
                           AND t.tgname LIKE '%_append_only'
                         ORDER BY 1
                        """)) {
            while (filas.next()) {
                selladas.add(filas.getString("tabla"));
            }
        }
        return selladas;
    }
}
