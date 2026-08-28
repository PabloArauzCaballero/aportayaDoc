package bo.aportaya.organizador.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.util.UUID;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si un organizador esta habilitado, y hasta donde.
 *
 * <p>La pregunta CU-20 antes de dejar crear un grupo: **un organizador suspendido no
 * abre pasanakus nuevos**. `grupos` no puede leer este esquema (invariante 11), asi que
 * la respuesta se publica en el contrato.
 *
 * <p>Devuelve tambien los limites del nivel porque quien crea el grupo los necesita en
 * el mismo momento, y dos viajes para dos datos del mismo renglon es un viaje de mas.
 */
@Service
public class ConsultarHabilitacion {

    private final Datos datos;

    public ConsultarHabilitacion(Datos datos) {
        this.datos = datos;
    }

    @Transactional(readOnly = true)
    public Habilitacion ejecutar(UUID organizadorId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var fila = dsl.select(
                            DSL.field("estado", String.class),
                            DSL.field("nivel", String.class),
                            DSL.field("limite_grupos_simultaneos", Integer.class),
                            DSL.field("limite_monto_administrado", BigDecimal.class),
                            DSL.field("grupos_activos", Integer.class))
                    .from(DSL.table(DSL.name("organizador", "organizador")))
                    .where(DSL.field("id", UUID.class).eq(organizadorId))
                    .fetchOne();

            if (fila == null) {
                // Denegar por omision: un organizador que no existe no esta habilitado.
                return new Habilitacion(false, "INEXISTENTE", 0, BigDecimal.ZERO, 0);
            }
            return new Habilitacion(
                    "HABILITADO".equals(fila.get("estado", String.class)),
                    fila.get("nivel", String.class),
                    fila.get("limite_grupos_simultaneos", Integer.class),
                    fila.get("limite_monto_administrado", BigDecimal.class),
                    fila.get("grupos_activos", Integer.class));
        });
    }

    public record Habilitacion(
            boolean habilitado, String nivel, int limiteDeGrupos, BigDecimal limiteDeMonto, int gruposActivos) {}
}
