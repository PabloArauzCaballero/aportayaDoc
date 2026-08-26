package bo.aportaya.cumplimiento.infraestructura;

import bo.aportaya.cumplimiento.dominio.EstadoDeLicencia;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Lee {@code catalogo.licencia_regulatoria}.
 *
 * <p>Solo lectura: {@code catalogo} es «lo sembrado que muchos leen y nadie escribe
 * en caliente» ([[ADR-029]]), y el GRANT del servicio sobre ese esquema es SELECT.
 * La licencia la mantiene cumplimiento por via administrativa, no un caso de uso.
 */
@Component
public class LicenciaRepositorio {

    private static final String LICENCIA_DE_FUNCIONAMIENTO = "LICENCIA_FUNCIONAMIENTO";

    /**
     * La licencia de funcionamiento, si existe.
     *
     * <p>Se pide {@code ORDER BY fecha_solicitud DESC LIMIT 1} y no {@code fetchOne}:
     * una renovacion deja la anterior en la tabla, y {@code fetchOne} explotaria con
     * dos filas justo el dia que se renueva la licencia.
     */
    public Optional<Licencia> vigente(DSLContext dsl, LocalDate hoy) {
        Record fila = dsl.select(
                        DSL.field("estado", String.class),
                        DSL.field("vigente_hasta", LocalDate.class),
                        DSL.field("alcance_autorizado", String.class))
                .from(DSL.table(DSL.name("catalogo", "licencia_regulatoria")))
                .where(DSL.field("tipo").eq(LICENCIA_DE_FUNCIONAMIENTO))
                .orderBy(DSL.field("fecha_solicitud").desc())
                .limit(1)
                .fetchOne();

        if (fila == null) {
            return Optional.empty();
        }
        LocalDate hasta = fila.get("vigente_hasta", LocalDate.class);
        return Optional.of(new Licencia(
                EstadoDeLicencia.valueOf(fila.get("estado", String.class)),
                // Sin fecha de fin la licencia no vence: es el mismo criterio que
                // `fn_lic_servicio_habilitado` (vigente_hasta IS NULL OR >= hoy).
                hasta == null || !hasta.isBefore(hoy),
                codigosDe(fila.get("alcance_autorizado", String.class))));
    }

    /**
     * El alcance viaja como arreglo JSON de cadenas. Se parte a mano y no con un
     * lector de JSON completo porque la forma esta fijada por
     * {@code alcance_autorizado @> to_jsonb(ARRAY[...])}: siempre es un arreglo
     * plano de codigos, nunca objetos anidados.
     */
    private Set<String> codigosDe(String json) {
        Set<String> codigos = new LinkedHashSet<>();
        if (json == null) {
            return codigos;
        }
        for (String bruto : json.replace("[", "").replace("]", "").split(",")) {
            String codigo = bruto.trim().replace("\"", "");
            if (!codigo.isEmpty()) {
                codigos.add(codigo);
            }
        }
        return codigos;
    }

    public record Licencia(EstadoDeLicencia estado, boolean vigente, Set<String> alcance) {}
}
