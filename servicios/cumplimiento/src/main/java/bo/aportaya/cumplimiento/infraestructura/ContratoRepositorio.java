package bo.aportaya.cumplimiento.infraestructura;

import bo.aportaya.cumplimiento.dominio.VersionAceptable;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Lee {@code cumplimiento.contrato_adhesion} y el tarifario publicado. */
@Component
public class ContratoRepositorio {

    /** El contrato de ese tipo que rige hoy, con su version y su hash. */
    public Optional<Contrato> vigentePorTipo(DSLContext dsl, String tipo) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("version", Short.class),
                        DSL.field("estado", String.class),
                        DSL.field("hash_documento", String.class))
                .from(DSL.table(DSL.name("cumplimiento", "contrato_adhesion")))
                .where(DSL.field("tipo").eq(tipo))
                .and(DSL.field("estado").eq("VIGENTE"))
                // Si hubiera dos vigentes por error, gana la version mas alta: nunca
                // se ata a nadie a la mas vieja de dos que la base dejo pasar.
                .orderBy(DSL.field("version").desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Contrato(
                        f.get("id", UUID.class),
                        f.get("version", Short.class),
                        f.get("estado", String.class),
                        f.get("hash_documento", String.class)));
    }

    public Optional<Contrato> porId(DSLContext dsl, UUID contratoId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("version", Short.class),
                        DSL.field("estado", String.class),
                        DSL.field("hash_documento", String.class))
                .from(DSL.table(DSL.name("cumplimiento", "contrato_adhesion")))
                .where(DSL.field("id", UUID.class).eq(contratoId))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Contrato(
                        f.get("id", UUID.class),
                        f.get("version", Short.class),
                        f.get("estado", String.class),
                        f.get("hash_documento", String.class)));
    }

    /**
     * R-CON-07: no se acepta un contrato si no hay tarifario publicado que mostrar.
     *
     * <p>Se exige {@code publicado_en} y no solo {@code estado='VIGENTE'} porque la
     * pregunta del caso de uso no es «existe un tarifario» sino «la persona pudo
     * verlo antes de aceptar». Un tarifario vigente sin publicar no lo pudo ver.
     */
    public boolean hayTarifarioPublicado(DSLContext dsl, LocalDate hoy) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("catalogo", "tarifario")),
                        DSL.field("estado").eq("VIGENTE"),
                        DSL.field("publicado_en").isNotNull(),
                        DSL.field("vigente_desde", LocalDate.class).le(hoy),
                        DSL.field("vigente_hasta")
                                .isNull()
                                .or(DSL.field("vigente_hasta", LocalDate.class).ge(hoy)))
                > 0;
    }

    public record Contrato(UUID id, short version, String estado, String hashDocumento) {

        public VersionAceptable comoVersion() {
            return new VersionAceptable(version, estado);
        }
    }
}
